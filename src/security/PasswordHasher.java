package security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;

/**
 * Password hashing via PBKDF2-HMAC-SHA256 (OWASP 2023 recommended: 310,000 iterations).
 *
 * Stored format: iterations:saltHex:hashHex
 * The iteration count is embedded so future migrations to higher counts are non-breaking.
 */
public class PasswordHasher {

    private static final SecureRandom RNG        = new SecureRandom();
    private static final int ITERATIONS          = 310_000;
    private static final int HASH_BITS           = 256;
    private static final int SALT_BYTES          = 16;

    public static String hash(String password) throws Exception {
        byte[] salt = new byte[SALT_BYTES];
        RNG.nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt, ITERATIONS, HASH_BITS);
        return ITERATIONS + ":" + CryptoUtils.toHex(salt) + ":" + CryptoUtils.toHex(hash);
    }

    /** Constant-time comparison prevents timing attacks. */
    public static boolean verify(String password, String stored) throws Exception {
        String[] parts = stored.split(":");
        if (parts.length != 3) return false;
        int iters     = Integer.parseInt(parts[0]);
        byte[] salt   = CryptoUtils.fromHex(parts[1]);
        byte[] expected = CryptoUtils.fromHex(parts[2]);
        byte[] actual   = pbkdf2(password.toCharArray(), salt, iters, HASH_BITS);
        if (actual.length != expected.length) return false;
        int diff = 0;
        for (int i = 0; i < actual.length; i++) diff |= actual[i] ^ expected[i];
        return diff == 0;
    }

    private static byte[] pbkdf2(char[] pass, byte[] salt, int iters, int keyBits)
            throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pass, salt, iters, keyBits);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] out = skf.generateSecret(spec).getEncoded();
        spec.clearPassword();
        return out;
    }
}
