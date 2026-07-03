package com.infinitericks.wallet.server;

import com.infinitericks.wallet.core.wallet.Amount;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OfficialExplorerClientTest {
    @Test
    void parsesWholeCoinBalance() {
        assertEquals(200_000_000L, Amount.toSatoshis("2"));
        assertEquals(200_000_000L, Amount.toSatoshis("2.0"));
    }
}
