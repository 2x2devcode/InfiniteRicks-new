package com.infinitericks.wallet.core.chain;

/**
 * Immutable network parameters for InfiniteRicks (RICK) mainnet.
 * Values sourced from the official repository:
 * https://github.com/2x2devcode/InfiniteRicks
 */
public final class NetworkParameters {
    public static final String TICKER = "RICK";
    public static final String COIN_NAME = "InfiniteRicks";
    public static final int COIN_DECIMALS = 8;
    public static final long COIN = 100_000_000L;
    public static final long CENT = 1_000_000L;

    /** P2PKH address version byte (base58.h PUBKEY_ADDRESS). */
    public static final int PUBKEY_ADDRESS_VERSION = 0x00;
    /** P2SH address version byte (base58.h SCRIPT_ADDRESS). */
    public static final int SCRIPT_ADDRESS_VERSION = 0x55;
    /** WIF = 128 + PUBKEY_ADDRESS_VERSION. */
    public static final int WIF_VERSION = 0x80;
    public static final byte WIF_COMPRESSED_SUFFIX = 0x01;

    public static final long MIN_TX_FEE = 10_000L;
    public static final long DEFAULT_FEE_PER_KB = 10_000L;
    public static final int COINBASE_MATURITY = 24;
    public static final int TX_VERSION = 1;
    public static final int LOCKTIME_THRESHOLD = 500_000_000;

    public static final String MESSAGE_MAGIC = "InfiniteRicks Signed Message:\n";

    public static final String OFFICIAL_API_HOST = "server.infinitericks.com";
    public static final int OFFICIAL_API_PORT = 40002;
    public static final int EXPLORER_PORT = 40051;
    public static final String OFFICIAL_API_BASE_URL = "https://" + OFFICIAL_API_HOST + ":" + OFFICIAL_API_PORT;
    public static final String EXPLORER_BASE_URL = "https://" + OFFICIAL_API_HOST + ":" + EXPLORER_PORT;

    public static final int P2P_PORT = 31647;
    public static final int RPC_PORT = 31648;

    public static final int ADDRESS_MIN_LENGTH = 26;
    public static final int ADDRESS_MAX_LENGTH = 35;

    private NetworkParameters() {
    }

    public static boolean isMainnetP2pkhAddress(String address) {
        if (address == null) {
            return false;
        }
        String trimmed = address.trim();
        if (trimmed.length() < ADDRESS_MIN_LENGTH || trimmed.length() > ADDRESS_MAX_LENGTH) {
            return false;
        }
        return trimmed.matches("[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]+");
    }
}
