package gui;

import client.VaultClient;
import security.PasswordStrength;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Dialog for adding a new secret or editing an existing one.
 * Supports categories: PASSWORD, API_KEY, NOTE, SSH_KEY, CREDIT_CARD.
 */
public class AddSecretDialog extends JDialog {

    public static final String[] CATEGORIES = {
        "PASSWORD", "API_KEY", "NOTE", "SSH_KEY", "CREDIT_CARD"
    };

    private final VaultClient client;
    private final String      token;
    private final String      editingName;   // null = new secret

    private final JTextField     nameField    = Theme.field();
    private final JComboBox<String> catCombo  = buildCatCombo();
    private final JPasswordField  passField   = Theme.passField();
    private final JTextArea       noteArea    = buildNoteArea();
    private final JProgressBar    strengthBar = buildStrBar();
    private final JLabel          strengthLbl = Theme.label("—");
    private final JLabel          statusLbl   = buildStatus();

    private boolean saved = false;

    public AddSecretDialog(Window owner, VaultClient client, String token,
                           String editingName, String prefillCategory, String prefillValue) {
        super(owner, editingName == null ? "Add Secret" : "Edit Secret",
              ModalityType.APPLICATION_MODAL);
        this.client      = client;
        this.token       = token;
        this.editingName = editingName;

        getContentPane().setBackground(Theme.PANEL);
        setLayout(new BorderLayout(0, 0));
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        if (editingName != null) {
            nameField.setText(editingName);
            nameField.setEditable(false);
            nameField.setForeground(Theme.DIM);
        }
        if (prefillCategory != null) catCombo.setSelectedItem(prefillCategory);
        if (prefillValue    != null) passField.setText(prefillValue);

        updateValuePanel();
        catCombo.addActionListener(e -> updateValuePanel());
        attachStrengthListener();

        pack();
        setMinimumSize(new Dimension(460, 0));
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    // ── Form ──────────────────────────────────────────────────────────────

    private JPanel buildForm() {
        JPanel p = Theme.cardPanel();
        p.setLayout(new GridBagLayout());

        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0; gc.weightx = 1.0;
        gc.insets = new Insets(4, 0, 4, 0);

        gc.gridy = 0; p.add(Theme.label("Secret Name"), gc);
        gc.gridy = 1; p.add(nameField, gc);

        gc.gridy = 2; p.add(Theme.label("Category"), gc);
        gc.gridy = 3; p.add(catCombo, gc);

        gc.gridy = 4; p.add(Theme.label("Value"), gc);

        // Password row: masked field + show/hide + generator
        JPanel pwRow = new JPanel(new BorderLayout(6, 0));
        pwRow.setBackground(Theme.CARD);
        JButton showBtn = Theme.ghostBtn("👁");
        showBtn.setPreferredSize(new Dimension(38, 34));
        showBtn.addActionListener(e -> toggleVisibility(showBtn));
        JButton genBtn = Theme.ghostBtn("⚙");
        genBtn.setPreferredSize(new Dimension(38, 34));
        genBtn.setToolTipText("Open password generator");
        genBtn.addActionListener(e -> openGenerator());
        JPanel iconPanel = new JPanel(new GridLayout(1, 2, 4, 0));
        iconPanel.setBackground(Theme.CARD);
        iconPanel.add(showBtn);
        iconPanel.add(genBtn);
        pwRow.add(passField,  BorderLayout.CENTER);
        pwRow.add(iconPanel,  BorderLayout.EAST);
        gc.gridy = 5; p.add(pwRow, gc);

        // Note area (shown only for NOTE category)
        gc.gridy = 6; p.add(new JScrollPane(noteArea), gc);

        // Strength
        JPanel strRow = new JPanel(new BorderLayout(8, 0));
        strRow.setBackground(Theme.CARD);
        strRow.add(strengthBar, BorderLayout.CENTER);
        strRow.add(strengthLbl,  BorderLayout.EAST);
        gc.gridy = 7; gc.insets = new Insets(6, 0, 0, 0);
        p.add(strRow, gc);

        gc.gridy = 8; gc.insets = new Insets(4, 0, 0, 0);
        p.add(statusLbl, gc);

        return p;
    }

    private JPanel buildButtons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        p.setBackground(Theme.PANEL);
        JButton cancel = Theme.ghostBtn("Cancel");
        JButton save   = Theme.primaryBtn(editingName == null ? "Add Secret" : "Save Changes");
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> doSave());
        p.add(cancel);
        p.add(save);
        return p;
    }

    // ── Actions ───────────────────────────────────────────────────────────

    private void doSave() {
        String name  = nameField.getText().trim();
        String cat   = (String) catCombo.getSelectedItem();
        String value = isNoteCategory()
                ? noteArea.getText().trim()
                : new String(passField.getPassword()).trim();

        if (name.isEmpty())  { setStatus("Name is required.",  Theme.WARNING); return; }
        if (value.isEmpty()) { setStatus("Value is required.", Theme.WARNING); return; }

        setStatus("Saving…", Theme.DIM);
        SwingWorker<Void, Void> w = new SwingWorker<>() {
            protected Void doInBackground() throws Exception {
                if (editingName == null) client.addSecret(token, name, cat, value);
                else                    client.updateSecret(token, name, cat, value);
                return null;
            }
            protected void done() {
                try {
                    get();
                    saved = true;
                    dispose();
                } catch (Exception ex) {
                    setStatus(rootCause(ex), Theme.DANGER);
                }
            }
        };
        w.execute();
    }

    private void openGenerator() {
        PasswordGeneratorDialog gen = new PasswordGeneratorDialog(this);
        gen.setVisible(true);
        String pw = gen.getGeneratedPassword();
        if (pw != null) {
            passField.setText(pw);
            updateStrength(pw);
        }
    }

    private void toggleVisibility(JButton btn) {
        boolean hidden = passField.getEchoChar() != 0;
        passField.setEchoChar(hidden ? (char) 0 : '•');
        btn.setText(hidden ? "🙈" : "👁");
    }

    private void updateValuePanel() {
        boolean note = isNoteCategory();
        passField.getParent().setVisible(!note);
        noteArea.getParent().setVisible(note);
        strengthBar.getParent().setVisible(!note);
        pack();
    }

    private void attachStrengthListener() {
        passField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { updateStrength(new String(passField.getPassword())); }
            public void removeUpdate(DocumentEvent e)  { updateStrength(new String(passField.getPassword())); }
            public void changedUpdate(DocumentEvent e) { updateStrength(new String(passField.getPassword())); }
        });
    }

    private void updateStrength(String pw) {
        if (isNoteCategory()) return;
        int sc = PasswordStrength.score(pw);
        strengthBar.setValue(sc);
        strengthBar.setForeground(Theme.strengthColor(sc));
        strengthLbl.setText(pw.isEmpty() ? "—" : PasswordStrength.evaluate(pw).label);
        strengthLbl.setForeground(Theme.strengthColor(sc));
    }

    // ── Public API ────────────────────────────────────────────────────────

    public boolean isSaved() { return saved; }

    // ── Helpers ───────────────────────────────────────────────────────────

    private boolean isNoteCategory() {
        return "NOTE".equals(catCombo.getSelectedItem());
    }

    private void setStatus(String msg, Color c) {
        statusLbl.setText(msg);
        statusLbl.setForeground(c);
    }

    private static String rootCause(Exception e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }

    private static JComboBox<String> buildCatCombo() {
        JComboBox<String> cb = new JComboBox<>(CATEGORIES);
        cb.setBackground(Theme.CARD);
        cb.setForeground(Theme.TEXT);
        cb.setFont(Theme.MONO);
        return cb;
    }

    private static JTextArea buildNoteArea() {
        JTextArea ta = new JTextArea(4, 30);
        ta.setBackground(Theme.BG);
        ta.setForeground(Theme.TEXT);
        ta.setCaretColor(Theme.ACCENT);
        ta.setFont(Theme.MONO);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return ta;
    }

    private static JProgressBar buildStrBar() {
        JProgressBar bar = new JProgressBar(0, 5);
        bar.setStringPainted(false);
        bar.setBackground(Theme.PANEL);
        bar.setForeground(Theme.DANGER);
        bar.setPreferredSize(new Dimension(0, 6));
        bar.setBorder(BorderFactory.createLineBorder(Theme.BORDER_C));
        return bar;
    }

    private static JLabel buildStatus() {
        JLabel l = new JLabel(" ");
        l.setFont(Theme.MONO_SM);
        l.setForeground(Theme.DIM);
        return l;
    }
}
