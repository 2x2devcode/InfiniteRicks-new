package com.infinitericks.wallet.core.crypto;

import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;

public final class Secp256k1 {
    private static final X9ECParameters CURVE = SECNamedCurves.getByName("secp256k1");
    private static final ECDomainParameters DOMAIN = new ECDomainParameters(
            CURVE.getCurve(), CURVE.getG(), CURVE.getN(), CURVE.getH());
    private static final BigInteger N = CURVE.getN();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Secp256k1() {
    }

    public static BigInteger generatePrivateKey() {
        while (true) {
            BigInteger key = new BigInteger(256, RANDOM);
            if (key.signum() > 0 && key.compareTo(N) < 0) {
                return key;
            }
        }
    }

    public static byte[] compressedPublicKey(BigInteger privateKey) {
        ECPoint point = CURVE.getG().multiply(privateKey).normalize();
        byte[] x = toFixed(point.getAffineXCoord().toBigInteger(), 32);
        byte prefix = point.getAffineYCoord().toBigInteger().testBit(0) ? (byte) 0x03 : (byte) 0x02;
        byte[] pub = new byte[33];
        pub[0] = prefix;
        System.arraycopy(x, 0, pub, 1, 32);
        return pub;
    }

    public static byte[] signDer(BigInteger privateKey, byte[] hash) {
        ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
        signer.init(true, new ECPrivateKeyParameters(privateKey, DOMAIN));
        BigInteger[] sig = signer.generateSignature(hash);
        BigInteger r = sig[0];
        BigInteger s = sig[1];
        if (s.compareTo(N.shiftRight(1)) > 0) {
            s = N.subtract(s);
        }
        return encodeDer(r, s);
    }

    private static byte[] encodeDer(BigInteger r, BigInteger s) {
        byte[] rb = unsigned(r);
        byte[] sb = unsigned(s);
        int total = 2 + rb.length + 2 + sb.length;
        byte[] out = new byte[2 + total];
        int i = 0;
        out[i++] = 0x30;
        out[i++] = (byte) total;
        out[i++] = 0x02;
        out[i++] = (byte) rb.length;
        System.arraycopy(rb, 0, out, i, rb.length);
        i += rb.length;
        out[i++] = 0x02;
        out[i++] = (byte) sb.length;
        System.arraycopy(sb, 0, out, i, sb.length);
        return out;
    }

    private static byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        if ((bytes[0] & 0x80) != 0) {
            byte[] prefixed = new byte[bytes.length + 1];
            System.arraycopy(bytes, 0, prefixed, 1, bytes.length);
            return prefixed;
        }
        return bytes;
    }

    private static byte[] toFixed(BigInteger value, int size) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > size) {
            bytes = Arrays.copyOfRange(bytes, bytes.length - size, bytes.length);
        }
        byte[] out = new byte[size];
        System.arraycopy(bytes, 0, out, size - bytes.length, bytes.length);
        return out;
    }
}
