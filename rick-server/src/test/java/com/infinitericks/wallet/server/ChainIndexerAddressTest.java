package com.infinitericks.wallet.server;

import com.google.gson.JsonObject;
import com.infinitericks.wallet.core.crypto.AddressCodec;
import com.infinitericks.wallet.core.crypto.HexUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainIndexerAddressTest {
    @Test
    void p2pkhScriptMatchesKnownAddress() throws Exception {
        String address = "1AYqgJLpBzhyfejNNMJtyZ4QTcMmi8RU9g";
        byte[] script = AddressCodec.p2pkhScript(AddressCodec.decodeHash160(address));
        JsonObject output = new JsonObject();
        JsonObject scriptPubKey = new JsonObject();
        scriptPubKey.addProperty("hex", HexUtils.toHex(script));
        output.add("scriptPubKey", scriptPubKey);
        output.addProperty("n", 0);
        output.addProperty("value", 2.0);

        ChainIndexer indexer = ChainIndexer.open();
        var method = ChainIndexer.class.getDeclaredMethod("outputHasAddress", JsonObject.class, String.class);
        method.setAccessible(true);
        assertTrue((Boolean) method.invoke(indexer, output, address));
    }
}
