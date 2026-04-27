package client;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Thin wrapper around the Vault socket protocol.
 * Every method opens a fresh connection, sends one command, reads the response,
 * then closes — stateless from the network perspective.
 */
public class VaultClient {

    // ── Inner model types ─────────────────────────────────────────────────

    public static class VaultException extends Exception {
        public VaultException(String msg) { super(msg); }
    }

    public static class SecretEntry {
        public final String name, category, createdAt;
        public SecretEntry(String name, String category, String createdAt) {
            this.name = name; this.category = category; this.createdAt = createdAt;
        }
    }

    public static class SecretDetail {
        public final String name, category, value;
        public SecretDetail(String name, String category, String value) {
            this.name = name; this.category = category; this.value = value;
        }
    }

    public static class AuditEntry {
        public final String action, details, ip, timestamp;
        public AuditEntry(String action, String details, String ip, String timestamp) {
            this.action = action; this.details = details;
            this.ip = ip; this.timestamp = timestamp;
        }
    }

    // ── Constructor ───────────────────────────────────────────────────────

    private final String host;
    private final int    port;

    public VaultClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ── Auth ──────────────────────────────────────────────────────────────

    public void register(String username, String password)
            throws Exception {
        String resp = send("REGISTER " + enc(username) + " " + enc(password));
        if (!resp.startsWith("OK")) throw new VaultException(
                resp.startsWith("ERROR ") ? resp.substring(6) : resp);
    }

    /** @return session token */
    public String login(String username, String password) throws Exception {
        String resp = send("LOGIN " + enc(username) + " " + enc(password));
        if (resp.startsWith("TOKEN ")) return resp.substring(6).trim();
        if (resp.equals("DENIED")) throw new VaultException("Invalid username or password.");
        throw new VaultException(resp);
    }

    public void logout(String token) throws Exception {
        send("LOGOUT " + token);
    }

    public void changePassword(String token, String oldPass, String newPass)
            throws Exception {
        String resp = send("CHANGE_PASS " + token + " " + enc(oldPass) + " " + enc(newPass));
        if (resp.equals("DENIED")) throw new VaultException("Current password is incorrect.");
        if (!resp.startsWith("OK")) throw new VaultException(
                resp.startsWith("ERROR ") ? resp.substring(6) : resp);
    }

    // ── Secrets CRUD ──────────────────────────────────────────────────────

    public void addSecret(String token, String name, String category, String value)
            throws Exception {
        String resp = send("ADD_SECRET " + token + " " + enc(name)
                           + " " + category + " " + enc(value));
        if (!resp.startsWith("OK")) throw new VaultException(
                resp.startsWith("ERROR ") ? resp.substring(6) : resp);
    }

    public void updateSecret(String token, String name, String category, String value)
            throws Exception {
        String resp = send("UPDATE_SECRET " + token + " " + enc(name)
                           + " " + category + " " + enc(value));
        if (!resp.startsWith("OK")) throw new VaultException(
                resp.startsWith("ERROR ") ? resp.substring(6) : resp);
    }

    public SecretDetail getSecret(String token, String name) throws Exception {
        String resp = send("GET_SECRET " + token + " " + enc(name));
        if (resp.equals("DENIED"))    throw new VaultException("Session expired.");
        if (resp.equals("NOT_FOUND")) throw new VaultException("Secret not found.");
        if (resp.startsWith("ERROR")) throw new VaultException(resp.substring(6));
        String[] parts = resp.split(" ", 4);     // SECRET <name_b64> <cat> <val_b64>
        return new SecretDetail(dec(parts[1]), parts[2], dec(parts[3]));
    }

    public List<SecretEntry> listSecrets(String token) throws Exception {
        List<String> lines = sendMulti("LIST_SECRETS " + token);
        List<SecretEntry> result = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith("ITEM ")) continue;
            String[] parts = line.split(" ", 4); // ITEM <name_b64> <cat> <ts>
            result.add(new SecretEntry(dec(parts[1]), parts[2],
                    parts.length > 3 ? parts[3] : ""));
        }
        return result;
    }

    public void deleteSecret(String token, String name) throws Exception {
        String resp = send("DELETE_SECRET " + token + " " + enc(name));
        if (!resp.startsWith("OK")) throw new VaultException(
                resp.startsWith("ERROR ") ? resp.substring(6) : resp);
    }

    // ── Audit log ─────────────────────────────────────────────────────────

    public List<AuditEntry> getAuditLog(String token) throws Exception {
        List<String> lines = sendMulti("AUDIT_LOG " + token);
        List<AuditEntry> result = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith("LOG ")) continue;
            // LOG <action_b64> <details_b64> <ip_b64> <timestamp>
            String[] parts = line.split(" ", 5);
            result.add(new AuditEntry(
                    dec(parts[1]),
                    dec(parts[2]),
                    dec(parts[3]),
                    parts.length > 4 ? parts[4] : ""));
        }
        return result;
    }

    // ── Utilities ─────────────────────────────────────────────────────────

    /** Returns strength score 0-5 and label. */
    public int[] checkStrength(String password) throws Exception {
        String resp = send("CHECK_STRENGTH " + enc(password));
        if (!resp.startsWith("STRENGTH ")) return new int[]{0};
        String[] parts = resp.split(" ");
        return new int[]{ Integer.parseInt(parts[1]) };
    }

    // ── Transport ─────────────────────────────────────────────────────────

    private String send(String command) throws Exception {
        try (
            Socket       s   = new Socket(host, port);
            PrintWriter  out = new PrintWriter(s.getOutputStream(), true);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(s.getInputStream()))
        ) {
            out.println(command);
            String resp = in.readLine();
            return resp == null ? "ERROR No response" : resp;
        }
    }

    private List<String> sendMulti(String command) throws Exception {
        List<String> lines = new ArrayList<>();
        try (
            Socket       s   = new Socket(host, port);
            PrintWriter  out = new PrintWriter(s.getOutputStream(), true);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(s.getInputStream()))
        ) {
            out.println(command);
            String line;
            while ((line = in.readLine()) != null && !line.equals("END")) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static String enc(String s) throws Exception {
        return Base64.getEncoder().encodeToString(s.getBytes("UTF-8"));
    }

    private static String dec(String s) throws Exception {
        return new String(Base64.getDecoder().decode(s), "UTF-8");
    }
}
