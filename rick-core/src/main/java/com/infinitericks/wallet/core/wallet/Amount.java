package com.infinitericks.wallet.core.wallet;

import com.infinitericks.wallet.core.chain.NetworkParameters;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Amount {
    private Amount() {
    }

    public static long toSatoshis(String decimal) {
        String[] parts = decimal.trim().split("\\.");
        long whole = Long.parseLong(parts[0]);
        long fraction = 0;
        if (parts.length > 1) {
            String padded = (parts[1] + "00000000").substring(0, 8);
            fraction = Long.parseLong(padded);
        }
        return whole * NetworkParameters.COIN + fraction;
    }

    public static String fromSatoshis(long satoshis) {
        BigDecimal value = new BigDecimal(satoshis)
                .divide(new BigDecimal(NetworkParameters.COIN), NetworkParameters.COIN_DECIMALS, RoundingMode.DOWN);
        return value.toPlainString();
    }
}
