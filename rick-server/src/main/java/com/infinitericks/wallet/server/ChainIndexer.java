package com.infinitericks.wallet.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.infinitericks.wallet.core.chain.NetworkParameters;
import com.infinitericks.wallet.core.crypto.AddressCodec;
import com.infinitericks.wallet.core.crypto.Base58;
import com.infinitericks.wallet.core.crypto.HexUtils;
import com.infinitericks.wallet.core.wallet.Amount;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Indexes P2PKH outputs and spends from the local node so balance/UTXO queries work
 * for non-custodial wallet addresses (daemon wallet RPC only sees its own keys).
 */
final class ChainIndexer {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type STATE_TYPE = new TypeToken<PersistedState>() {
    }.getType();
    private static final int SYNC_BATCH_SIZE = 100;
    private static final int DEFAULT_LOOKBACK_BLOCKS = readIntEnv("INDEX_LOOKBACK_BLOCKS", 2_000);
    private static final int START_HEIGHT = readIntEnv("INDEX_START_HEIGHT", 0);

    private final Path indexDir;
    private final Path statePath;
    private final Path lockPath;
    private final Object memoryLock = new Object();

    private int indexedHeight = -1;
    private int chainTip = -1;
    private final Map<String, IndexedUtxo> outpoints = new HashMap<>();
    private final Map<String, Set<String>> utxosByAddress = new HashMap<>();

    private ChainIndexer(Path indexDir) {
        this.indexDir = indexDir;
        this.statePath = indexDir.resolve("chain-index.json");
        this.lockPath = indexDir.resolve("chain-index.lock");
    }

    static ChainIndexer open() throws IOException {
        String configured = System.getenv("INDEX_DIR");
        Path indexDir = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"), ".rick-wallet-index")
                : Path.of(configured);
        Files.createDirectories(indexDir);
        ChainIndexer indexer = new ChainIndexer(indexDir);
        indexer.loadState();
        return indexer;
    }

    void startBackgroundSync(RpcClient rpcClient) {
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    syncToTip(rpcClient);
                    Thread.sleep(15_000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (IOException e) {
                    System.err.println("[chain-indexer] sync failed: " + e.getMessage());
                    sleepQuietly(30_000L);
                }
            }
        }, "rick-chain-indexer");
        thread.setDaemon(true);
        thread.start();
    }

    void syncToTip(RpcClient rpcClient) throws IOException {
        synchronized (memoryLock) {
            chainTip = readBlockCount(rpcClient);
            int nextHeight = indexedHeight + 1;
            if (nextHeight < START_HEIGHT) {
                nextHeight = START_HEIGHT;
            }
            while (nextHeight <= chainTip) {
                int batchEnd = Math.min(chainTip, nextHeight + SYNC_BATCH_SIZE - 1);
                for (int height = nextHeight; height <= batchEnd; height++) {
                    indexBlock(rpcClient, height);
                    indexedHeight = height;
                }
                persistStateLocked();
                if (indexedHeight % 5_000 == 0) {
                    System.out.println("[chain-indexer] indexed through block " + indexedHeight + " / " + chainTip);
                }
                nextHeight = indexedHeight + 1;
            }
            chainTip = readBlockCount(rpcClient);
        }
    }

    List<IndexedUtxo> utxosFor(String address, int minConfirmations, RpcClient rpcClient) throws IOException {
        synchronized (memoryLock) {
            if (!AddressCodec.isValidP2pkh(address)) {
                throw new IOException("invalid address: " + address);
            }
            chainTip = readBlockCount(rpcClient);
            Map<String, IndexedUtxo> merged = new LinkedHashMap<>();
            for (IndexedUtxo utxo : listIndexedUtxos(address, minConfirmations)) {
                merged.put(outpointKey(utxo.txid, utxo.vout), utxo);
            }
            if (indexedHeight < chainTip) {
                int fromHeight = Math.max(START_HEIGHT, indexedHeight + 1);
                mergeWindow(rpcClient, address, fromHeight, chainTip, minConfirmations, merged);
            }
            if (merged.isEmpty()) {
                int fromHeight = Math.max(START_HEIGHT, chainTip - DEFAULT_LOOKBACK_BLOCKS + 1);
                mergeWindow(rpcClient, address, fromHeight, chainTip, minConfirmations, merged);
            }
            return new ArrayList<>(merged.values());
        }
    }

    private void mergeWindow(
            RpcClient rpcClient,
            String address,
            int fromHeight,
            int toHeight,
            int minConfirmations,
            Map<String, IndexedUtxo> merged
    ) throws IOException {
        if (fromHeight > toHeight) {
            return;
        }
        for (IndexedUtxo utxo : scanAddressWindow(rpcClient, address, fromHeight, toHeight, minConfirmations)) {
            merged.put(outpointKey(utxo.txid, utxo.vout), utxo);
        }
    }

    long balanceSatoshis(String address, int minConfirmations, RpcClient rpcClient) throws IOException {
        long total = 0L;
        for (IndexedUtxo utxo : utxosFor(address, minConfirmations, rpcClient)) {
            total += utxo.amountSatoshis;
        }
        return total;
    }

    int indexedHeight() {
        synchronized (memoryLock) {
            return indexedHeight;
        }
    }

    int chainTip() {
        synchronized (memoryLock) {
            return chainTip;
        }
    }

    private List<IndexedUtxo> listIndexedUtxos(String address, int minConfirmations) {
        Set<String> keys = utxosByAddress.getOrDefault(address, Collections.emptySet());
        List<IndexedUtxo> result = new ArrayList<>();
        for (String key : keys) {
            IndexedUtxo utxo = outpoints.get(key);
            if (utxo == null) {
                continue;
            }
            int confirmations = confirmationsFor(utxo.blockHeight);
            if (confirmations >= minConfirmations) {
                utxo.confirmations = confirmations;
                result.add(utxo);
            }
        }
        result.sort((a, b) -> {
            int byHeight = Integer.compare(b.blockHeight, a.blockHeight);
            if (byHeight != 0) {
                return byHeight;
            }
            int byVout = Integer.compare(a.vout, b.vout);
            if (byVout != 0) {
                return byVout;
            }
            return a.txid.compareTo(b.txid);
        });
        return result;
    }

    private List<IndexedUtxo> scanAddressWindow(
            RpcClient rpcClient,
            String address,
            int fromHeight,
            int toHeight,
            int minConfirmations
    ) throws IOException {
        Map<String, IndexedUtxo> live = new LinkedHashMap<>();
        int workers = Math.min(8, Math.max(1, Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            List<Future<BlockScan>> futures = new ArrayList<>();
            int chunk = Math.max(25, (toHeight - fromHeight + 1) / workers);
            for (int start = fromHeight; start <= toHeight; start += chunk) {
                int end = Math.min(toHeight, start + chunk - 1);
                int chunkStart = start;
                futures.add(pool.submit(() -> scanAddressChunk(rpcClient, address, chunkStart, end)));
            }
            List<BlockScan> chunks = new ArrayList<>();
            for (Future<BlockScan> future : futures) {
                chunks.add(future.get());
            }
            chunks.sort((a, b) -> Integer.compare(a.fromHeight(), b.fromHeight()));
            for (BlockScan chunkResult : chunks) {
                for (IndexedUtxo created : chunkResult.created().values()) {
                    live.put(outpointKey(created.txid, created.vout), created);
                }
                for (String spentKey : chunkResult.spentKeys()) {
                    live.remove(spentKey);
                }
            }
        } catch (Exception e) {
            throw new IOException("address lookback scan failed", e);
        } finally {
            pool.shutdownNow();
        }
        List<IndexedUtxo> result = new ArrayList<>();
        for (IndexedUtxo utxo : live.values()) {
            int confirmations = confirmationsFor(utxo.blockHeight);
            if (confirmations >= minConfirmations) {
                utxo.confirmations = confirmations;
                result.add(utxo);
            }
        }
        return result;
    }

    private BlockScan scanAddressChunk(RpcClient rpcClient, String address, int fromHeight, int toHeight) throws IOException {
        Map<String, IndexedUtxo> created = new LinkedHashMap<>();
        List<String> spentKeys = new ArrayList<>();
        for (int height = fromHeight; height <= toHeight; height++) {
            JsonObject block = fetchBlock(rpcClient, height);
            JsonArray transactions = block.getAsJsonArray("tx");
            if (transactions == null) {
                continue;
            }
            for (JsonElement txElement : transactions) {
                if (!txElement.isJsonObject()) {
                    continue;
                }
                scanTransactionForAddress(
                        txElement.getAsJsonObject(),
                        height,
                        address,
                        created,
                        spentKeys
                );
            }
        }
        return new BlockScan(fromHeight, created, spentKeys);
    }

    private void scanTransactionForAddress(
            JsonObject tx,
            int blockHeight,
            String address,
            Map<String, IndexedUtxo> created,
            List<String> spentKeys
    ) {
        JsonArray inputs = tx.getAsJsonArray("vin");
        if (inputs != null) {
            for (JsonElement inputElement : inputs) {
                if (!inputElement.isJsonObject()) {
                    continue;
                }
                JsonObject input = inputElement.getAsJsonObject();
                if (input.has("coinbase") || !input.has("txid") || !input.has("vout")) {
                    continue;
                }
                spentKeys.add(outpointKey(input.get("txid").getAsString(), input.get("vout").getAsInt()));
            }
        }

        String txid = tx.get("txid").getAsString();
        JsonArray outputs = tx.getAsJsonArray("vout");
        if (outputs == null) {
            return;
        }
        for (JsonElement outputElement : outputs) {
            if (!outputElement.isJsonObject()) {
                continue;
            }
            JsonObject output = outputElement.getAsJsonObject();
            if (!outputHasAddress(output, address)) {
                continue;
            }
            int vout = output.get("n").getAsInt();
            long amountSatoshis = Amount.toSatoshis(String.format(
                    Locale.US,
                    "%.8f",
                    output.get("value").getAsDouble()
            ));
            IndexedUtxo utxo = new IndexedUtxo();
            utxo.txid = txid;
            utxo.vout = vout;
            utxo.address = address;
            utxo.amountSatoshis = amountSatoshis;
            utxo.blockHeight = blockHeight;
            created.put(outpointKey(txid, vout), utxo);
        }
    }

    private record BlockScan(int fromHeight, Map<String, IndexedUtxo> created, List<String> spentKeys) {
    }

    private void indexBlock(RpcClient rpcClient, int height) throws IOException {
        JsonObject block = fetchBlock(rpcClient, height);
        JsonArray transactions = block.getAsJsonArray("tx");
        if (transactions == null) {
            return;
        }
        for (JsonElement txElement : transactions) {
            if (!txElement.isJsonObject()) {
                continue;
            }
            processTransaction(txElement.getAsJsonObject(), height);
        }
    }

    private JsonObject fetchBlock(RpcClient rpcClient, int height) throws IOException {
        JsonArray params = new JsonArray();
        params.add(height);
        params.add(true);
        return rpcClient.call("getblockbynumber", params).getAsJsonObject();
    }

    private void processTransaction(JsonObject tx, int blockHeight) {
        JsonArray inputs = tx.getAsJsonArray("vin");
        if (inputs != null) {
            for (JsonElement inputElement : inputs) {
                if (!inputElement.isJsonObject()) {
                    continue;
                }
                JsonObject input = inputElement.getAsJsonObject();
                if (input.has("coinbase") || !input.has("txid") || !input.has("vout")) {
                    continue;
                }
                removeOutpoint(input.get("txid").getAsString(), input.get("vout").getAsInt());
            }
        }

        String txid = tx.get("txid").getAsString();
        JsonArray outputs = tx.getAsJsonArray("vout");
        if (outputs == null) {
            return;
        }
        for (JsonElement outputElement : outputs) {
            if (!outputElement.isJsonObject()) {
                continue;
            }
            JsonObject output = outputElement.getAsJsonObject();
            int vout = output.get("n").getAsInt();
            long amountSatoshis = Amount.toSatoshis(String.format(
                    Locale.US,
                    "%.8f",
                    output.get("value").getAsDouble()
            ));
            for (String address : extractAddresses(output)) {
                addOutpoint(txid, vout, address, amountSatoshis, blockHeight);
            }
        }
    }

    private Set<String> extractAddresses(JsonObject output) {
        Set<String> addresses = new HashSet<>();
        JsonObject script = output.getAsJsonObject("scriptPubKey");
        if (script != null && script.has("addresses")) {
            JsonArray listed = script.getAsJsonArray("addresses");
            for (JsonElement element : listed) {
                String address = element.getAsString();
                if (AddressCodec.isValidP2pkh(address)) {
                    addresses.add(address);
                }
            }
        }
        if (!addresses.isEmpty()) {
            return addresses;
        }
        if (script != null && script.has("hex")) {
            String derived = addressFromScriptHex(script.get("hex").getAsString());
            if (derived != null) {
                addresses.add(derived);
            }
        }
        return addresses;
    }

    private boolean outputHasAddress(JsonObject output, String address) {
        JsonObject script = output.getAsJsonObject("scriptPubKey");
        if (script != null && script.has("addresses")) {
            for (JsonElement element : script.getAsJsonArray("addresses")) {
                if (address.equals(element.getAsString())) {
                    return true;
                }
            }
        }
        if (script != null && script.has("hex")) {
            String derived = addressFromScriptHex(script.get("hex").getAsString());
            return address.equals(derived);
        }
        return false;
    }

    private static String addressFromScriptHex(String scriptHex) {
        if (scriptHex == null || scriptHex.length() < 50) {
            return null;
        }
        byte[] script = HexUtils.fromHex(scriptHex);
        if (script.length != 25 || script[0] != 0x76 || script[1] != (byte) 0xA9 || script[2] != 0x14) {
            return null;
        }
        byte[] hash160 = new byte[20];
        System.arraycopy(script, 3, hash160, 0, 20);
        return Base58.encodeCheck((byte) NetworkParameters.PUBKEY_ADDRESS_VERSION, hash160);
    }

    private void addOutpoint(String txid, int vout, String address, long amountSatoshis, int blockHeight) {
        String key = outpointKey(txid, vout);
        IndexedUtxo utxo = new IndexedUtxo();
        utxo.txid = txid;
        utxo.vout = vout;
        utxo.address = address;
        utxo.amountSatoshis = amountSatoshis;
        utxo.blockHeight = blockHeight;
        IndexedUtxo previous = outpoints.put(key, utxo);
        if (previous != null && !previous.address.equals(address)) {
            removeFromAddressIndex(previous.address, key);
        }
        utxosByAddress.computeIfAbsent(address, ignored -> new HashSet<>()).add(key);
    }

    private void removeOutpoint(String txid, int vout) {
        String key = outpointKey(txid, vout);
        IndexedUtxo removed = outpoints.remove(key);
        if (removed != null) {
            removeFromAddressIndex(removed.address, key);
        }
    }

    private void removeFromAddressIndex(String address, String key) {
        Set<String> keys = utxosByAddress.get(address);
        if (keys == null) {
            return;
        }
        keys.remove(key);
        if (keys.isEmpty()) {
            utxosByAddress.remove(address);
        }
    }

    private int confirmationsFor(int blockHeight) {
        if (chainTip < 0 || blockHeight < 0) {
            return 0;
        }
        return Math.max(0, chainTip - blockHeight + 1);
    }

    private static String outpointKey(String txid, int vout) {
        return txid + ":" + vout;
    }

    private static int readBlockCount(RpcClient rpcClient) throws IOException {
        return rpcClient.call("getblockcount", new JsonArray()).getAsInt();
    }

    private void loadState() throws IOException {
        synchronized (memoryLock) {
            if (!Files.exists(statePath)) {
                indexedHeight = START_HEIGHT - 1;
                return;
            }
            try (Reader reader = Files.newBufferedReader(statePath, StandardCharsets.UTF_8)) {
                PersistedState state = GSON.fromJson(reader, STATE_TYPE);
                if (state == null) {
                    indexedHeight = START_HEIGHT - 1;
                    return;
                }
                indexedHeight = state.indexedHeight;
                chainTip = state.chainTip;
                outpoints.clear();
                utxosByAddress.clear();
                if (state.outpoints != null) {
                    outpoints.putAll(state.outpoints);
                }
                if (state.utxosByAddress != null) {
                    for (Map.Entry<String, Set<String>> entry : state.utxosByAddress.entrySet()) {
                        utxosByAddress.put(entry.getKey(), new HashSet<>(entry.getValue()));
                    }
                }
            }
        }
    }

    private void persistStateLocked() throws IOException {
        PersistedState state = new PersistedState();
        state.indexedHeight = indexedHeight;
        state.chainTip = chainTip;
        state.outpoints = new HashMap<>(outpoints);
        state.utxosByAddress = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : utxosByAddress.entrySet()) {
            state.utxosByAddress.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }

        Files.createDirectories(indexDir);
        try (FileChannel channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        )) {
            FileLock fileLock = channel.lock();
            try {
                Path tempPath = statePath.resolveSibling(statePath.getFileName() + ".tmp");
                try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                    GSON.toJson(state, STATE_TYPE, writer);
                }
                Files.move(tempPath, statePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                fileLock.release();
            }
        }
    }

    private static int readIntEnv(String key, int fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static final class IndexedUtxo {
        String txid;
        int vout;
        String address;
        long amountSatoshis;
        int blockHeight;
        int confirmations;
    }

    private static final class PersistedState {
        private int indexedHeight = -1;
        private int chainTip = -1;
        private Map<String, IndexedUtxo> outpoints = new HashMap<>();
        private Map<String, Set<String>> utxosByAddress = new HashMap<>();
    }
}
