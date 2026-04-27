package server;

import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;

import db.DatabaseUtility;
import security.*;

/**
 * Handles one client connection per thread.
 *
 * Wire protocol — all values containing arbitrary text are Base64-encoded
 * so the space delimiter is never ambiguous.
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │ REGISTER  <user_b64> <pass_b64>                                     │
 * │ LOGIN     <user_b64> <pass_b64>   → TOKEN <token> | DENIED | ERROR  │
 * │ LOGOUT    <token>                                                    │
 * │ ADD_SECRET    <token> <name_b64> <category> <value_b64>             │
 * │ UPDATE_SECRET <token> <name_b64> <category> <value_b64>             │
 * │ GET_SECRET    <token> <name_b64>  → SECRET <name_b64> <cat> <val_b64>│
 * │ LIST_SECRETS  <token>             → ITEM lines … END                 │
 * │ DELETE_SECRET <token> <name_b64>                                    │
 * │ AUDIT_LOG     <token>             → LOG lines … END                  │
 * │ CHANGE_PASS   <token> <old_b64> <new_b64>                           │
 * │ CHECK_STRENGTH <pass_b64>         → STRENGTH <0-5> <label>          │
 * └─────────────────────────────────────────────────────────────────────┘
 */
public class ClientHandler implements Runnable {

    private final Socket socket;

    public ClientHandler(Socket socket) { this.socket = socket; }

    @Override
    public void run() {
        String ip = socket.getInetAddress().getHostAddress();
        try (
            BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter    out = new PrintWriter(socket.getOutputStream(), true);
            Connection     db  = DatabaseUtility.getConnection()
        ) {
            String line = in.readLine();
            if (line == null || line.isBlank()) return;

            String[] p = line.trim().split(" ");
            switch (p[0]) {
                case "REGISTER"      -> handleRegister(p, out, db, ip);
                case "LOGIN"         -> handleLogin(p, out, db, ip);
                case "LOGOUT"        -> handleLogout(p, out, db);
                case "ADD_SECRET"    -> handleAddSecret(p, out, db, ip, false);
                case "UPDATE_SECRET" -> handleAddSecret(p, out, db, ip, true);
                case "GET_SECRET"    -> handleGetSecret(p, out, db);
                case "LIST_SECRETS"  -> handleListSecrets(p, out, db);
                case "DELETE_SECRET" -> handleDeleteSecret(p, out, db, ip);
                case "AUDIT_LOG"     -> handleAuditLog(p, out, db);
                case "CHANGE_PASS"   -> handleChangePass(p, out, db, ip);
                case "CHECK_STRENGTH"-> handleCheckStrength(p, out);
                default              -> out.println("ERROR Unknown command");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── REGISTER ─────────────────────────────────────────────────────────

    private void handleRegister(String[] p, PrintWriter out, Connection db, String ip)
            throws Exception {
        if (p.length != 3) { out.println("ERROR Bad arguments"); return; }
        String username = b64d(p[1]);
        String password = b64d(p[2]);

        PreparedStatement check = db.prepareStatement(
                "SELECT id FROM users WHERE username = ?");
        check.setString(1, username);
        if (check.executeQuery().next()) { out.println("ERROR Username taken"); return; }

        String authHash  = PasswordHasher.hash(password);
        String vaultSalt = CryptoUtils.toHex(CryptoUtils.randomBytes(16));

        PreparedStatement ins = db.prepareStatement(
                "INSERT INTO users(username, auth_hash, vault_salt) VALUES(?,?,?)");
        ins.setString(1, username);
        ins.setString(2, authHash);
        ins.setString(3, vaultSalt);
        ins.executeUpdate();

        audit(db, userId(db, username), "REGISTER", "New account", ip);
        out.println("OK");
    }

    // ── LOGIN ─────────────────────────────────────────────────────────────

    private void handleLogin(String[] p, PrintWriter out, Connection db, String ip)
            throws Exception {
        if (p.length != 3) { out.println("ERROR Bad arguments"); return; }
        String username = b64d(p[1]);
        String password = b64d(p[2]);

        PreparedStatement st = db.prepareStatement(
                "SELECT id, auth_hash, vault_salt FROM users WHERE username = ?");
        st.setString(1, username);
        ResultSet rs = st.executeQuery();

        if (!rs.next()) { out.println("DENIED"); return; }

        int    uid       = rs.getInt("id");
        String authHash  = rs.getString("auth_hash");
        String vaultSalt = rs.getString("vault_salt");

        if (!PasswordHasher.verify(password, authHash)) {
            audit(db, uid, "LOGIN_FAIL", "Bad password", ip);
            out.println("DENIED");
            return;
        }

        SecretKeySpec vaultKey = CryptoUtils.deriveKey(
                password, CryptoUtils.fromHex(vaultSalt));
        String token = SessionManager.createSession(
                username, CryptoUtils.toHex(vaultKey.getEncoded()));

        db.prepareStatement(
                "UPDATE users SET last_login = NOW() WHERE id = " + uid)
          .executeUpdate();
        audit(db, uid, "LOGIN", "Success", ip);
        out.println("TOKEN " + token);
    }

    // ── LOGOUT ────────────────────────────────────────────────────────────

    private void handleLogout(String[] p, PrintWriter out, Connection db) throws Exception {
        if (p.length != 2) { out.println("ERROR Bad arguments"); return; }
        String token = p[1];
        String user  = SessionManager.getUser(token);
        if (user != null) audit(db, userId(db, user), "LOGOUT", null, null);
        SessionManager.invalidate(token);
        out.println("OK");
    }

    // ── ADD / UPDATE SECRET ───────────────────────────────────────────────

    private void handleAddSecret(String[] p, PrintWriter out, Connection db,
                                  String ip, boolean update) throws Exception {
        if (p.length < 5) { out.println("ERROR Bad arguments"); return; }
        if (!SessionManager.isValid(p[1])) { out.println("DENIED"); return; }

        String name     = b64d(p[2]);
        String category = p[3];
        // value is last token — may theoretically have spaces if protocol extended later
        String value    = b64d(p[4]);

        String user = SessionManager.getUser(p[1]);
        SecretKeySpec key = vaultKey(p[1]);
        String enc  = CryptoUtils.encrypt(value, key);
        int uid     = userId(db, user);

        if (update) {
            PreparedStatement st = db.prepareStatement(
                    "UPDATE secrets SET category=?, encrypted_value=? " +
                    "WHERE user_id=? AND name=?");
            st.setString(1, category);
            st.setString(2, enc);
            st.setInt(3, uid);
            st.setString(4, name);
            if (st.executeUpdate() == 0) { out.println("ERROR Not found"); return; }
            audit(db, uid, "UPDATE_SECRET", "name=" + name, ip);
        } else {
            PreparedStatement st = db.prepareStatement(
                    "INSERT INTO secrets(user_id, name, category, encrypted_value) " +
                    "VALUES(?,?,?,?)");
            st.setInt(1, uid);
            st.setString(2, name);
            st.setString(3, category);
            st.setString(4, enc);
            try {
                st.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException ex) {
                out.println("ERROR A secret with that name already exists");
                return;
            }
            audit(db, uid, "ADD_SECRET", "name=" + name + " cat=" + category, ip);
        }
        out.println("OK");
    }

    // ── GET SECRET ────────────────────────────────────────────────────────

    private void handleGetSecret(String[] p, PrintWriter out, Connection db) throws Exception {
        if (p.length != 3) { out.println("ERROR Bad arguments"); return; }
        if (!SessionManager.isValid(p[1])) { out.println("DENIED"); return; }

        String name = b64d(p[2]);
        int    uid  = userId(db, SessionManager.getUser(p[1]));

        PreparedStatement st = db.prepareStatement(
                "SELECT category, encrypted_value FROM secrets " +
                "WHERE user_id=? AND name=?");
        st.setInt(1, uid);
        st.setString(2, name);
        ResultSet rs = st.executeQuery();

        if (!rs.next()) { out.println("NOT_FOUND"); return; }

        String decrypted = CryptoUtils.decrypt(
                rs.getString("encrypted_value"), vaultKey(p[1]));
        out.println("SECRET " + b64e(name) + " " + rs.getString("category")
                    + " " + b64e(decrypted));
    }

    // ── LIST SECRETS ──────────────────────────────────────────────────────

    private void handleListSecrets(String[] p, PrintWriter out, Connection db) throws Exception {
        if (p.length != 2) { out.println("ERROR Bad arguments"); return; }
        if (!SessionManager.isValid(p[1])) { out.println("DENIED"); return; }

        int uid = userId(db, SessionManager.getUser(p[1]));
        PreparedStatement st = db.prepareStatement(
                "SELECT name, category, created_at FROM secrets " +
                "WHERE user_id=? ORDER BY created_at DESC");
        st.setInt(1, uid);
        ResultSet rs = st.executeQuery();

        while (rs.next()) {
            out.println("ITEM " + b64e(rs.getString("name"))
                        + " " + rs.getString("category")
                        + " " + rs.getTimestamp("created_at").toString());
        }
        out.println("END");
    }

    // ── DELETE SECRET ─────────────────────────────────────────────────────

    private void handleDeleteSecret(String[] p, PrintWriter out, Connection db, String ip)
            throws Exception {
        if (p.length != 3) { out.println("ERROR Bad arguments"); return; }
        if (!SessionManager.isValid(p[1])) { out.println("DENIED"); return; }

        String name = b64d(p[2]);
        String user = SessionManager.getUser(p[1]);
        int    uid  = userId(db, user);

        PreparedStatement st = db.prepareStatement(
                "DELETE FROM secrets WHERE user_id=? AND name=?");
        st.setInt(1, uid);
        st.setString(2, name);
        if (st.executeUpdate() == 0) { out.println("ERROR Not found"); return; }

        audit(db, uid, "DELETE_SECRET", "name=" + name, ip);
        out.println("OK");
    }

    // ── AUDIT LOG ─────────────────────────────────────────────────────────

    private void handleAuditLog(String[] p, PrintWriter out, Connection db) throws Exception {
        if (p.length != 2) { out.println("ERROR Bad arguments"); return; }
        if (!SessionManager.isValid(p[1])) { out.println("DENIED"); return; }

        int uid = userId(db, SessionManager.getUser(p[1]));
        PreparedStatement st = db.prepareStatement(
                "SELECT action, details, ip_address, created_at FROM audit_log " +
                "WHERE user_id=? ORDER BY created_at DESC LIMIT 200");
        st.setInt(1, uid);
        ResultSet rs = st.executeQuery();

        while (rs.next()) {
            String details = rs.getString("details");
            String ip      = rs.getString("ip_address");
            out.println("LOG " + b64e(rs.getString("action"))
                        + " " + b64e(details == null ? "" : details)
                        + " " + b64e(ip      == null ? "" : ip)
                        + " " + rs.getTimestamp("created_at").toString());
        }
        out.println("END");
    }

    // ── CHANGE PASSWORD ───────────────────────────────────────────────────

    private void handleChangePass(String[] p, PrintWriter out, Connection db, String ip)
            throws Exception {
        if (p.length != 4) { out.println("ERROR Bad arguments"); return; }
        if (!SessionManager.isValid(p[1])) { out.println("DENIED"); return; }

        String oldPass = b64d(p[2]);
        String newPass = b64d(p[3]);
        String user    = SessionManager.getUser(p[1]);
        int    uid     = userId(db, user);

        PreparedStatement st = db.prepareStatement(
                "SELECT auth_hash, vault_salt FROM users WHERE id=?");
        st.setInt(1, uid);
        ResultSet rs = st.executeQuery();
        rs.next();

        if (!PasswordHasher.verify(oldPass, rs.getString("auth_hash"))) {
            audit(db, uid, "CHANGE_PASS_FAIL", "Bad old password", ip);
            out.println("DENIED");
            return;
        }

        // Re-encrypt every secret: old vault key → new vault key
        String oldSalt      = rs.getString("vault_salt");
        SecretKeySpec oldKey = CryptoUtils.deriveKey(oldPass, CryptoUtils.fromHex(oldSalt));
        String newSalt      = CryptoUtils.toHex(CryptoUtils.randomBytes(16));
        SecretKeySpec newKey = CryptoUtils.deriveKey(newPass, CryptoUtils.fromHex(newSalt));

        PreparedStatement list = db.prepareStatement(
                "SELECT id, encrypted_value FROM secrets WHERE user_id=?");
        list.setInt(1, uid);
        ResultSet secrets = list.executeQuery();

        PreparedStatement upd = db.prepareStatement(
                "UPDATE secrets SET encrypted_value=? WHERE id=?");
        while (secrets.next()) {
            String plain    = CryptoUtils.decrypt(secrets.getString("encrypted_value"), oldKey);
            String reEnc    = CryptoUtils.encrypt(plain, newKey);
            upd.setString(1, reEnc);
            upd.setInt(2, secrets.getInt("id"));
            upd.addBatch();
        }
        upd.executeBatch();

        String newHash = PasswordHasher.hash(newPass);
        PreparedStatement updUser = db.prepareStatement(
                "UPDATE users SET auth_hash=?, vault_salt=? WHERE id=?");
        updUser.setString(1, newHash);
        updUser.setString(2, newSalt);
        updUser.setInt(3, uid);
        updUser.executeUpdate();

        SessionManager.updateVaultKey(p[1], CryptoUtils.toHex(newKey.getEncoded()));
        audit(db, uid, "CHANGE_PASS", "Success", ip);
        out.println("OK");
    }

    // ── CHECK STRENGTH ────────────────────────────────────────────────────

    private void handleCheckStrength(String[] p, PrintWriter out) throws Exception {
        if (p.length != 2) { out.println("ERROR Bad arguments"); return; }
        String pw    = b64d(p[1]);
        int    score = PasswordStrength.score(pw);
        String label = PasswordStrength.evaluate(pw).label;
        out.println("STRENGTH " + score + " " + b64e(label));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static int userId(Connection db, String username) throws Exception {
        PreparedStatement st = db.prepareStatement(
                "SELECT id FROM users WHERE username=?");
        st.setString(1, username);
        ResultSet rs = st.executeQuery();
        if (!rs.next()) throw new IllegalStateException("User not found: " + username);
        return rs.getInt("id");
    }

    private static SecretKeySpec vaultKey(String token) {
        String hex = SessionManager.getVaultKey(token);
        return new SecretKeySpec(CryptoUtils.fromHex(hex), "AES");
    }

    private static void audit(Connection db, int uid, String action,
                               String details, String ip) {
        try {
            PreparedStatement st = db.prepareStatement(
                    "INSERT INTO audit_log(user_id, action, details, ip_address) VALUES(?,?,?,?)");
            st.setInt(1, uid);
            st.setString(2, action);
            st.setString(3, details);
            st.setString(4, ip);
            st.executeUpdate();
        } catch (Exception ignored) {}
    }

    private static String b64e(String s) throws Exception {
        return Base64.getEncoder().encodeToString(s.getBytes("UTF-8"));
    }

    private static String b64d(String s) throws Exception {
        return new String(Base64.getDecoder().decode(s), "UTF-8");
    }
}
