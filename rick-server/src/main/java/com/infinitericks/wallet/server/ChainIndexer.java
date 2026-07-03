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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Indexes P2PKH outputs and spends from the local node so balance/UTXO queries work
 * for non-custodial wallet addresses (daemon wallet RPC only sees its own keys).
 */
final class ChainIndexer {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type STATE_TYPE = new TypeToken<PersistedState>() {
    }.getType();
    private static final int SYNC_BATCH_SIZE = 100;
    private static final int[] LOOKBACK_WINDOWS = readLookbackWindows();
    private static final int[] FAST_LOOKBACK_WINDOWS = readFastLookbackWindows();
    private static final long FAST_QUERY_BUDGET_MS = readLongEnv("INDEX_FAST_BUDGET_MS", 6_000L);
    private static final long QUERY_BUDGET_MS = readLongEnv("INDEX_QUERY_BUDGET_MS", 60_000L);
    private static final int INCREMENTAL_SCAN_MAX_BLOCKS = readIntEnv("INDEX_INCREMENTAL_MAX", 200);
    private static final int START_HEIGHT = readIntEnv("INDEX_START_HEIGHT", 0);
    private static final int SCAN_WORKERS = readIntEnv("INDEX_SCAN_WORKERS", 4);
    private static final long CHUNK_TIMEOUT_MS = readLongEnv("INDEX_CHUNK_TIMEOUT_MS", 4_000L);
    private static final long CHAIN_TIP_STALE_MS = readLongEnv("INDEX_CHAIN_TIP_STALE_MS", 5_000L);
    private static final int REVERSE_SCAN_MAX_BLOCKS = readIntEnv("INDEX_REVERSE_SCAN_MAX", 10_000);

    @FunctionalInterface
    interface AddressUpdateListener {
        void onAddressUpdated(String address);
    }

    private final ExecutorService addressScanExecutor = Executors.newFixedThreadPool(2);
    private final Set<String> pendingAddressScans = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingExplorerEnrich = ConcurrentHashMap.newKeySet();
    private volatile AddressUpdateListener addressUpdateListener;

    private final Path indexDir;
    private final Path statePath;
    private final Path lockPath;
    private final ReentrantReadWriteLock indexLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = indexLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = indexLock.writeLock();

    private int indexedHeight = -1;
    private int chainTip = -1;
    private volatile long chainTipUpdatedAtMs = 0L;
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

    void setAddressUpdateListener(AddressUpdateListener listener) {
        this.addressUpdateListener = listener;
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
        int tip = readBlockCount(rpcClient);
        int nextHeight;
        writeLock.lock();
        try {
            chainTip = tip;
            chainTipUpdatedAtMs = System.currentTimeMillis();
            nextHeight = indexedHeight + 1;
            if (nextHeight < START_HEIGHT) {
                nextHeight = START_HEIGHT;
            }
        } finally {
            writeLock.unlock();
        }
        while (nextHeight <= tip) {
            int batchEnd = Math.min(tip, nextHeight + SYNC_BATCH_SIZE - 1);
            for (int height = nextHeight; height <= batchEnd; height++) {
                JsonObject block = fetchBlock(rpcClient, height);
                writeLock.lock();
                try {
                    applyBlockToIndex(block, height);
                    indexedHeight = height;
                } finally {
                    writeLock.unlock();
                }
            }
            writeLock.lock();
            try {
                if (indexedHeight % 5_000 == 0) {
                    System.out.println("[chain-indexer] indexed through block " + indexedHeight + " / " + chainTip);
                }
            } finally {
                writeLock.unlock();
            }
            persistState();
            nextHeight = batchEnd + 1;
            tip = readBlockCount(rpcClient);
            writeLock.lock();
            try {
                chainTip = tip;
                chainTipUpdatedAtMs = System.currentTimeMillis();
            } finally {
                writeLock.unlock();
            }
        }
    }

    record BalanceResult(long satoshis, boolean scanning) {
    }

    BalanceResult balanceFast(String address, int minConfirmations, RpcClient rpcClient) throws IOException {
        if (!AddressCodec.isValidP2pkh(address)) {
            throw new IOException("invalid address: " + address);
        }
        List<IndexedUtxo> indexed = readIndexedOnly(address, minConfirmations);
        long total = 0L;
        for (IndexedUtxo utxo : indexed) {
            total += utxo.amountSatoshis;
        }
        boolean scanning = false;
        if (total == 0L) {
            scanning = scheduleDeepScan(address, minConfirmations, rpcClient);
        }
        return new BalanceResult(total, scanning);
    }

    List<IndexedUtxo> utxosFor(String address, int minConfirmations, RpcClient rpcClient) throws IOException {
        if (!AddressCodec.isValidP2pkh(address)) {
            throw new IOException("invalid address: " + address);
        }
        List<IndexedUtxo> indexed = readIndexedOnly(address, minConfirmations);
        if (indexed.isEmpty()) {
            scheduleDeepScan(address, minConfirmations, rpcClient);
        }
        return indexed;
    }

    private List<IndexedUtxo> readIndexedOnly(String address, int minConfirmations) {
        readLock.lock();
        try {
            List<IndexedUtxo> result = new ArrayList<>();
            for (IndexedUtxo utxo : listIndexedUtxos(address, minConfirmations)) {
                result.add(copyUtxo(utxo));
            }
            return result;
        } finally {
            readLock.unlock();
        }
    }

    private List<IndexedUtxo> utxosForDeep(
            String address,
            int minConfirmations,
            RpcClient rpcClient
    ) throws IOException {
        if (!AddressCodec.isValidP2pkh(address)) {
            throw new IOException("invalid address: " + address);
        }

        int tip = readBlockCount(rpcClient);
        int indexed;
        Map<String, IndexedUtxo> merged = new LinkedHashMap<>();
        readLock.lock();
        try {
            indexed = indexedHeight;
            for (IndexedUtxo utxo : listIndexedUtxos(address, minConfirmations)) {
                merged.put(outpointKey(utxo.txid, utxo.vout), copyUtxo(utxo));
            }
        } finally {
            readLock.unlock();
        }
        writeLock.lock();
        try {
            chainTip = tip;
            chainTipUpdatedAtMs = System.currentTimeMillis();
        } finally {
            writeLock.unlock();
        }

        long deadline = System.currentTimeMillis() + QUERY_BUDGET_MS;
        int[] windows = LOOKBACK_WINDOWS;

        if (!merged.isEmpty() && indexed >= 0 && indexed < tip) {
            int blocksBehind = tip - indexed;
            if (blocksBehind <= INCREMENTAL_SCAN_MAX_BLOCKS) {
                mergeWindow(rpcClient, address, indexed + 1, tip, minConfirmations, merged, tip, deadline);
            }
        }

        if (merged.isEmpty()) {
            mergeReverseFromTip(rpcClient, address, tip, minConfirmations, merged, deadline);
        }

        if (merged.isEmpty()) {
            for (int window : windows) {
                if (System.currentTimeMillis() >= deadline || !merged.isEmpty()) {
                    break;
                }
                int fromHeight = Math.max(START_HEIGHT, tip - window + 1);
                mergeWindow(rpcClient, address, fromHeight, tip, minConfirmations, merged, tip, deadline);
            }
        }

        if (!merged.isEmpty()) {
            writeLock.lock();
            try {
                chainTip = tip;
                chainTipUpdatedAtMs = System.currentTimeMillis();
                for (IndexedUtxo utxo : merged.values()) {
                    addOutpoint(utxo.txid, utxo.vout, utxo.address, utxo.amountSatoshis, utxo.blockHeight);
                }
            } finally {
                writeLock.unlock();
            }
            persistState();
            notifyAddressUpdated(address);
        }

        return finalizeUtxos(merged, tip, minConfirmations);
    }

    void scheduleExplorerEnrich(String address, OfficialExplorerClient explorer, RpcClient rpcClient) {
        if (!explorer.enabled() || !pendingExplorerEnrich.add(address)) {
            return;
        }
        addressScanExecutor.submit(() -> {
            try {
                enrichFromExplorer(address, explorer, rpcClient);
            } catch (IOException e) {
                System.err.println("[chain-indexer] explorer enrich failed for " + address + ": " + e.getMessage());
            } finally {
                pendingExplorerEnrich.remove(address);
            }
        });
    }

    private void enrichFromExplorer(String address, OfficialExplorerClient explorer, RpcClient rpcClient)
            throws IOException {
        List<String> txids = explorer.recentTxids(address, 20);
        if (txids.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (String txid : txids) {
            if (indexRawTransaction(txid, address, rpcClient)) {
                changed = true;
            }
        }
        if (changed) {
            persistState();
            notifyAddressUpdated(address);
        }
    }

    private boolean indexRawTransaction(String txid, String address, RpcClient rpcClient) throws IOException {
        JsonArray params = new JsonArray();
        params.add(txid);
        params.add(1);
        JsonObject tx;
        try {
            tx = rpcClient.call("getrawtransaction", params).getAsJsonObject();
        } catch (IOException e) {
            System.err.println("[chain-indexer] getrawtransaction " + txid + ": " + e.getMessage());
            return false;
        }
        if (!tx.has("blockheight")) {
            return false;
        }
        int blockHeight = tx.get("blockheight").getAsInt();
        Map<String, IndexedUtxo> created = new LinkedHashMap<>();
        List<String> spentKeys = new ArrayList<>();
        scanTransactionForAddress(tx, blockHeight, address, created, spentKeys);

        if (created.isEmpty() && spentKeys.isEmpty()) {
            return false;
        }

        writeLock.lock();
        try {
            chainTip = Math.max(chainTip, blockHeight);
            chainTipUpdatedAtMs = System.currentTimeMillis();
            for (String spentKey : spentKeys) {
                removeOutpointByKey(spentKey);
            }
            for (IndexedUtxo utxo : created.values()) {
                addOutpoint(utxo.txid, utxo.vout, utxo.address, utxo.amountSatoshis, utxo.blockHeight);
            }
        } finally {
            writeLock.unlock();
        }
        return true;
    }

    private void mergeReverseFromTip(
            RpcClient rpcClient,
            String address,
            int tip,
            int minConfirmations,
            Map<String, IndexedUtxo> merged,
            long deadlineMs
    ) throws IOException {
        int fromHeight = Math.max(START_HEIGHT, tip - REVERSE_SCAN_MAX_BLOCKS + 1);
        for (int height = tip; height >= fromHeight; height--) {
            if (System.currentTimeMillis() >= deadlineMs) {
                break;
            }
            JsonObject block = fetchBlock(rpcClient, height);
            JsonArray transactions = block.getAsJsonArray("tx");
            if (transactions == null) {
                continue;
            }
            for (JsonElement txElement : transactions) {
                if (!txElement.isJsonObject()) {
                    continue;
                }
                Map<String, IndexedUtxo> created = new LinkedHashMap<>();
                List<String> spentKeys = new ArrayList<>();
                scanTransactionForAddress(txElement.getAsJsonObject(), height, address, created, spentKeys);
                for (IndexedUtxo utxo : created.values()) {
                    merged.put(outpointKey(utxo.txid, utxo.vout), utxo);
                }
                for (String spentKey : spentKeys) {
                    merged.remove(spentKey);
                }
            }
            if (!merged.isEmpty()) {
                break;
            }
        }
    }

    private void notifyAddressUpdated(String address) {
        AddressUpdateListener listener = addressUpdateListener;
        if (listener != null) {
            listener.onAddressUpdated(address);
        }
    }

    private boolean scheduleDeepScan(String address, int minConfirmations, RpcClient rpcClient) {
        if (!pendingAddressScans.add(address)) {
            return true;
        }
        addressScanExecutor.submit(() -> {
            try {
                utxosForDeep(address, minConfirmations, rpcClient);
            } catch (IOException e) {
                System.err.println("[chain-indexer] deep scan failed for " + address + ": " + e.getMessage());
            } finally {
                pendingAddressScans.remove(address);
            }
        });
        return true;
    }

    private static IndexedUtxo copyUtxo(IndexedUtxo source) {
        IndexedUtxo copy = new IndexedUtxo();
        copy.txid = source.txid;
        copy.vout = source.vout;
        copy.address = source.address;
        copy.amountSatoshis = source.amountSatoshis;
        copy.blockHeight = source.blockHeight;
        copy.confirmations = source.confirmations;
        return copy;
    }

    private List<IndexedUtxo> finalizeUtxos(Map<String, IndexedUtxo> merged, int tip, int minConfirmations) {
        List<IndexedUtxo> result = new ArrayList<>();
        for (IndexedUtxo utxo : merged.values()) {
            int confirmations = Math.max(0, tip - utxo.blockHeight + 1);
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

    private void mergeWindow(
            RpcClient rpcClient,
            String address,
            int fromHeight,
            int toHeight,
            int minConfirmations,
            Map<String, IndexedUtxo> merged,
            int tip,
            long deadlineMs
    ) throws IOException {
        if (fromHeight > toHeight || System.currentTimeMillis() >= deadlineMs) {
            return;
        }
        for (IndexedUtxo utxo : scanAddressWindow(rpcClient, address, fromHeight, toHeight, minConfirmations, tip, deadlineMs)) {
            merged.put(outpointKey(utxo.txid, utxo.vout), utxo);
        }
    }

    long balanceSatoshis(String address, int minConfirmations, RpcClient rpcClient) throws IOException {
        return balanceFast(address, minConfirmations, rpcClient).satoshis();
    }

    int indexedHeight() {
        readLock.lock();
        try {
            return indexedHeight;
        } finally {
            readLock.unlock();
        }
    }

    int chainTip() {
        readLock.lock();
        try {
            return chainTip;
        } finally {
            readLock.unlock();
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
            int minConfirmations,
            int tip,
            long deadlineMs
    ) throws IOException {
        Map<String, IndexedUtxo> live = new LinkedHashMap<>();
        int workers = Math.min(SCAN_WORKERS, Math.max(1, Runtime.getRuntime().availableProcessors() * 2));
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            List<Future<BlockScan>> futures = new ArrayList<>();
            int totalBlocks = toHeight - fromHeight + 1;
            int chunk = Math.max(10, totalBlocks / workers);
            for (int start = fromHeight; start <= toHeight; start += chunk) {
                if (System.currentTimeMillis() >= deadlineMs) {
                    break;
                }
                int end = Math.min(toHeight, start + chunk - 1);
                int chunkStart = start;
                futures.add(pool.submit(() -> scanAddressChunk(rpcClient, address, chunkStart, end, deadlineMs)));
            }
            List<BlockScan> chunks = new ArrayList<>();
            for (Future<BlockScan> future : futures) {
                if (System.currentTimeMillis() >= deadlineMs) {
                    break;
                }
                try {
                    chunks.add(future.get(CHUNK_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                } catch (TimeoutException e) {
                    future.cancel(true);
                }
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
            int confirmations = Math.max(0, tip - utxo.blockHeight + 1);
            if (confirmations >= minConfirmations) {
                utxo.confirmations = confirmations;
                result.add(utxo);
            }
        }
        return result;
    }

    private BlockScan scanAddressChunk(
            RpcClient rpcClient,
            String address,
            int fromHeight,
            int toHeight,
            long deadlineMs
    ) throws IOException {
        Map<String, IndexedUtxo> created = new LinkedHashMap<>();
        List<String> spentKeys = new ArrayList<>();
        for (int height = fromHeight; height <= toHeight; height++) {
            if (System.currentTimeMillis() >= deadlineMs) {
                break;
            }
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

    private void applyBlockToIndex(JsonObject block, int height) {
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

    private void indexBlock(RpcClient rpcClient, int height) throws IOException {
        JsonObject block = fetchBlock(rpcClient, height);
        writeLock.lock();
        try {
            applyBlockToIndex(block, height);
        } finally {
            writeLock.unlock();
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

    private void removeOutpointByKey(String key) {
        int separator = key.lastIndexOf(':');
        if (separator <= 0 || separator >= key.length() - 1) {
            return;
        }
        removeOutpoint(key.substring(0, separator), Integer.parseInt(key.substring(separator + 1)));
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
        writeLock.lock();
        try {
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
                chainTipUpdatedAtMs = System.currentTimeMillis();
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
        } finally {
            writeLock.unlock();
        }
    }

    private void persistState() throws IOException {
        PersistedState state = snapshotState();
        writeStateToDisk(state);
    }

    private PersistedState snapshotState() {
        writeLock.lock();
        try {
            PersistedState state = new PersistedState();
            state.indexedHeight = indexedHeight;
            state.chainTip = chainTip;
            state.outpoints = new HashMap<>(outpoints);
            state.utxosByAddress = new HashMap<>();
            for (Map.Entry<String, Set<String>> entry : utxosByAddress.entrySet()) {
                state.utxosByAddress.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
            return state;
        } finally {
            writeLock.unlock();
        }
    }

    private void writeStateToDisk(PersistedState state) throws IOException {
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

    private static long readLongEnv(String key, long fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int[] readLookbackWindows() {
        return readWindowEnv("INDEX_LOOKBACK_WINDOWS", new int[]{200, 500, 1000, 2000});
    }

    private static int[] readFastLookbackWindows() {
        return readWindowEnv("INDEX_FAST_LOOKBACK_WINDOWS", new int[]{30, 60, 120});
    }

    private static int[] readWindowEnv(String key, int[] fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String[] parts = value.split(",");
        List<Integer> windows = new ArrayList<>();
        for (String part : parts) {
            try {
                int parsed = Integer.parseInt(part.trim());
                if (parsed > 0) {
                    windows.add(parsed);
                }
            } catch (NumberFormatException ignored) {
                // Skip invalid entries.
            }
        }
        if (windows.isEmpty()) {
            return fallback;
        }
        int[] result = new int[windows.size()];
        for (int i = 0; i < windows.size(); i++) {
            result[i] = windows.get(i);
        }
        return result;
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
