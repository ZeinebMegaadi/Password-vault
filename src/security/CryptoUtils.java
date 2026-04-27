package security;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption and PBKDF2 key derivation utilities.
 *
 * Encryption scheme:
 *   output = Base64( IV[12] || ciphertext+authTag )
 * The 12-byte IV is prepended to the ciphertext so each call is self-contained.
 */
public class CryptoUtils {

    private static final SecureRandom RNG       = new SecureRandom();
    private static final int GCM_IV_LEN         = 12;
    private static final int GCM_TAG_BITS        = 128;
    private static final int VAULT_KEY_ITERATIONS = 100_000;
    private static final int VAULT_KEY_BITS      = 256;

    // ── Key derivation ──────────────────────────────────────────────────────

    /** Derives a 256-bit AES key from a password + salt using PBKDF2-HMAC-SHA256. */
    public static SecretKeySpec deriveKey(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(), salt, VAULT_KEY_ITERATIONS, VAULT_KEY_BITS);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] raw = skf.generateSecret(spec).getEncoded();
        spec.clearPassword();
        return new SecretKeySpec(raw, "AES");
    }

    // ── Encryption / Decryption ─────────────────────────────────────────────

    /** Encrypts plaintext with AES-256-GCM. Returns Base64(IV || ciphertext+tag). */
    public static String encrypt(String plaintext, SecretKeySpec key) throws Exception {
        byte[] iv = new byte[GCM_IV_LEN];
        RNG.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ct = cipher.doFinal(plaintext.getBytes("UTF-8"));

        byte[] out = new byte[GCM_IV_LEN + ct.length];
        System.arraycopy(iv, 0, out, 0, GCM_IV_LEN);
        System.arraycopy(ct, 0, out, GCM_IV_LEN, ct.length);
        return Base64.getEncoder().encodeToString(out);
    }

    /** Decrypts a Base64(IV || ciphertext+tag) blob produced by {@link #encrypt}. */
    public static String decrypt(String encoded, SecretKeySpec key) throws Exception {
        byte[] data = Base64.getDecoder().decode(encoded);

        byte[] iv = new byte[GCM_IV_LEN];
        System.arraycopy(data, 0, iv, 0, GCM_IV_LEN);
        byte[] ct = new byte[data.length - GCM_IV_LEN];
        System.arraycopy(data, GCM_IV_LEN, ct, 0, ct.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(cipher.doFinal(ct), "UTF-8");
    }

    // ── Encoding helpers ────────────────────────────────────────────────────

    public static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RNG.nextBytes(b);
        return b;
    }

    public static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    public static byte[] fromHex(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    public static String b64Encode(String s) throws Exception {
        return Base64.getEncoder().encodeToString(s.getBytes("UTF-8"));
    }

    public static String b64Decode(String s) throws Exception {
        return new String(Base64.getDecoder().decode(s), "UTF-8");
    }
}
