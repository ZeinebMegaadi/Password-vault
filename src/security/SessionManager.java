package security;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session store.
 *
 * Each session carries:
 *   - the authenticated username
 *   - the hex-encoded AES vault key derived at login (never written to disk)
 *
 * Sessions expire after 30 minutes of inactivity.
 * A daemon thread purges expired sessions every 5 minutes.
 */
public class SessionManager {

    private static final long SESSION_TTL_MS = 30 * 60 * 1000L;

    private static class Session {
        final String username;
        final String vaultKeyHex;
        volatile long lastAccess;

        Session(String username, String vaultKeyHex) {
            this.username    = username;
            this.vaultKeyHex = vaultKeyHex;
            this.lastAccess  = System.currentTimeMillis();
        }

        boolean expired() {
            return System.currentTimeMillis() - lastAccess > SESSION_TTL_MS;
        }

        void touch() { lastAccess = System.currentTimeMillis(); }
    }

    private static final Map<String, Session> STORE = new ConcurrentHashMap<>();

    static {
        Thread cleaner = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try { Thread.sleep(5 * 60_000L); } catch (InterruptedException e) { break; }
                STORE.entrySet().removeIf(e -> e.getValue().expired());
            }
        });
        cleaner.setDaemon(true);
        cleaner.start();
    }

    public static String createSession(String username, String vaultKeyHex) {
        String token = UUID.randomUUID().toString();
        STORE.put(token, new Session(username, vaultKeyHex));
        return token;
    }

    public static boolean isValid(String token) {
        Session s = STORE.get(token);
        if (s == null) return false;
        if (s.expired()) { STORE.remove(token); return false; }
        s.touch();
        return true;
    }

    public static String getUser(String token) {
        Session s = STORE.get(token);
        if (s == null || s.expired()) return null;
        s.touch();
        return s.username;
    }

    public static String getVaultKey(String token) {
        Session s = STORE.get(token);
        if (s == null || s.expired()) return null;
        s.touch();
        return s.vaultKeyHex;
    }

    /** Replaces the in-session vault key after a password change. */
    public static void updateVaultKey(String token, String newVaultKeyHex) {
        Session old = STORE.get(token);
        if (old != null) {
            STORE.put(token, new Session(old.username, newVaultKeyHex));
        }
    }

    public static void invalidate(String token) {
        STORE.remove(token);
    }
}
