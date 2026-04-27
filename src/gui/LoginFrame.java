package gui;

import client.VaultClient;
import security.PasswordStrength;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;

/**
 * Login / Register window.
 * After successful login it constructs MainFrame and hides itself.
 */
public class LoginFrame extends JFrame {

    private final VaultClient client;

    private final JTextField     userField  = Theme.field();
    private final JPasswordField passField  = Theme.passField();
    private final JProgressBar   strengthBar;
    private final JLabel         strengthLbl = Theme.label("—");
    private final JLabel         statusLbl;

    public LoginFrame(String host, int port) {
        super("VAULT — Secure Credential Manager");
        this.client = new VaultClient(host, port);
        this.strengthBar = buildStrengthBar();
        this.statusLbl   = buildStatusLabel();

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        getContentPane().setBackground(Theme.BG);
        setLayout(new GridBagLayout());

        add(buildCard(), new GridBagConstraints());
        pack();
        setMinimumSize(new Dimension(440, 0));
        setLocationRelativeTo(null);

        // Live strength feedback while typing password
        passField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateStrength(); }
            public void removeUpdate(DocumentEvent e) { updateStrength(); }
            public void changedUpdate(DocumentEvent e) { updateStrength(); }
        });

        // Enter key submits
        ActionListener submit = e -> doLogin();
        userField.addActionListener(submit);
        passField.addActionListener(submit);
    }

    // ── Card panel ────────────────────────────────────────────────────────

    private JPanel buildCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Theme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_C),
                BorderFactory.createEmptyBorder(30, 36, 30, 36)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 0, 4, 0);
        gc.fill   = GridBagConstraints.HORIZONTAL;
        gc.gridx  = 0;

        // Title
        JLabel title = new JLabel("⬡ VAULT", SwingConstants.CENTER);
        title.setForeground(Theme.ACCENT);
        title.setFont(Theme.MONO_TITLE);
        gc.gridy = 0; gc.insets = new Insets(0, 0, 2, 0);
        card.add(title, gc);

        JLabel sub = new JLabel("Secure Credential Manager", SwingConstants.CENTER);
        sub.setForeground(Theme.DIM);
        sub.setFont(Theme.MONO_SM);
        gc.gridy = 1; gc.insets = new Insets(0, 0, 22, 0);
        card.add(sub, gc);

        // Fields
        gc.insets = new Insets(4, 0, 2, 0);
        gc.gridy = 2; card.add(Theme.label("Username"), gc);
        userField.setPreferredSize(new Dimension(300, 34));
        gc.gridy = 3; card.add(userField, gc);

        gc.gridy = 4; card.add(Theme.label("Master Password"), gc);
        passField.setPreferredSize(new Dimension(300, 34));
        gc.gridy = 5; card.add(passField, gc);

        // Strength row
        JPanel strRow = new JPanel(new BorderLayout(6, 0));
        strRow.setBackground(Theme.CARD);
        strRow.add(strengthBar, BorderLayout.CENTER);
        strRow.add(strengthLbl, BorderLayout.EAST);
        gc.gridy = 6; gc.insets = new Insets(4, 0, 12, 0);
        card.add(strRow, gc);

        // Buttons
        JPanel btns = new JPanel(new GridLayout(1, 2, 8, 0));
        btns.setBackground(Theme.CARD);
        JButton loginBtn    = Theme.primaryBtn("Login");
        JButton registerBtn = Theme.ghostBtn("Register");
        loginBtn.addActionListener(e -> doLogin());
        registerBtn.addActionListener(e -> doRegister());
        btns.add(loginBtn);
        btns.add(registerBtn);
        gc.gridy = 7; gc.insets = new Insets(0, 0, 10, 0);
        card.add(btns, gc);

        // Status
        gc.gridy = 8; gc.insets = new Insets(4, 0, 0, 0);
        card.add(statusLbl, gc);

        return card;
    }

    // ── Actions ───────────────────────────────────────────────────────────

    private void doLogin() {
        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());
        if (user.isEmpty() || pass.isEmpty()) {
            setStatus("Username and password are required.", Theme.WARNING);
            return;
        }
        setStatus("Authenticating…", Theme.DIM);
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            protected String doInBackground() throws Exception {
                return client.login(user, pass);
            }
            protected void done() {
                try {
                    String token = get();
                    passField.setText("");
                    setVisible(false);
                    new MainFrame(client, token, user, LoginFrame.this).setVisible(true);
                } catch (Exception ex) {
                    setStatus(rootCause(ex), Theme.DANGER);
                }
            }
        };
        worker.execute();
    }

    private void doRegister() {
        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());
        if (user.isEmpty() || pass.isEmpty()) {
            setStatus("Username and password are required.", Theme.WARNING);
            return;
        }
        if (PasswordStrength.score(pass) < 2) {
            setStatus("Password too weak — use ≥ 8 chars with mixed types.", Theme.WARNING);
            return;
        }
        setStatus("Registering…", Theme.DIM);
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            protected Void doInBackground() throws Exception {
                client.register(user, pass);
                return null;
            }
            protected void done() {
                try {
                    get();
                    setStatus("Account created. You can now log in.", Theme.SUCCESS);
                } catch (Exception ex) {
                    setStatus(rootCause(ex), Theme.DANGER);
                }
            }
        };
        worker.execute();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void updateStrength() {
        String pw    = new String(passField.getPassword());
        int    score = PasswordStrength.score(pw);
        String label = PasswordStrength.evaluate(pw).label;
        strengthBar.setValue(score);
        strengthBar.setForeground(Theme.strengthColor(score));
        strengthLbl.setText(pw.isEmpty() ? "—" : label);
        strengthLbl.setForeground(Theme.strengthColor(score));
    }

    private void setStatus(String msg, Color color) {
        statusLbl.setText(msg);
        statusLbl.setForeground(color);
    }

    private static JLabel buildStatusLabel() {
        JLabel l = new JLabel(" ", SwingConstants.CENTER);
        l.setFont(Theme.MONO_SM);
        l.setForeground(Theme.DIM);
        return l;
    }

    private static JProgressBar buildStrengthBar() {
        JProgressBar bar = new JProgressBar(0, 5);
        bar.setValue(0);
        bar.setStringPainted(false);
        bar.setBackground(Theme.PANEL);
        bar.setForeground(Theme.DANGER);
        bar.setPreferredSize(new Dimension(0, 6));
        bar.setBorder(BorderFactory.createLineBorder(Theme.BORDER_C));
        return bar;
    }

    private static String rootCause(Exception e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
