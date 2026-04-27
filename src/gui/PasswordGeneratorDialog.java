package gui;

import security.PasswordStrength;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.security.SecureRandom;

/**
 * Standalone password generator dialog.
 * Can be opened from MainFrame or embedded inside AddSecretDialog.
 * Call getGeneratedPassword() after dispose() to retrieve the result.
 */
public class PasswordGeneratorDialog extends JDialog {

    private static final String UPPER   = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER   = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS  = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?";
    private static final String AMBIG   = "Il1O0o";
    private static final SecureRandom RNG = new SecureRandom();

    private final JSlider     lengthSlider   = new JSlider(8, 64, 16);
    private final JLabel      lengthLabel    = Theme.label("16");
    private final JCheckBox   chkUpper       = check("Uppercase  A-Z", true);
    private final JCheckBox   chkLower       = check("Lowercase  a-z", true);
    private final JCheckBox   chkDigits      = check("Digits     0-9", true);
    private final JCheckBox   chkSymbols     = check("Symbols  !@#…",  true);
    private final JCheckBox   chkNoAmbig     = check("Exclude ambiguous (I l 1 O 0)", false);
    private final JTextField  outputField    = buildOutputField();
    private final JProgressBar strengthBar   = buildStrBar();
    private final JLabel      strengthLbl    = Theme.label("—");

    private String result = null;

    public PasswordGeneratorDialog(Window owner) {
        super(owner, "Password Generator", ModalityType.APPLICATION_MODAL);
        setBackground(Theme.BG);
        getContentPane().setBackground(Theme.PANEL);
        setLayout(new BorderLayout(0, 0));

        add(buildControls(), BorderLayout.WEST);
        add(buildOutput(),   BorderLayout.CENTER);

        pack();
        setMinimumSize(new Dimension(520, 320));
        setResizable(false);
        setLocationRelativeTo(owner);
        generate();
    }

    // ── Panels ────────────────────────────────────────────────────────────

    private JPanel buildControls() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Theme.CARD);
        p.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill  = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0; gc.insets = new Insets(4, 0, 4, 0);

        // Length row
        JPanel lenRow = new JPanel(new BorderLayout(8, 0));
        lenRow.setBackground(Theme.CARD);
        JLabel lenHead = Theme.label("Length:");
        lenHead.setFont(Theme.MONO_BOLD);
        lenRow.add(lenHead,    BorderLayout.WEST);
        lenRow.add(lengthLabel,BorderLayout.EAST);
        gc.gridy = 0; p.add(lenRow, gc);

        gc.gridy = 1; p.add(lengthSlider, gc);
        lengthSlider.setBackground(Theme.CARD);
        lengthSlider.addChangeListener((ChangeEvent e) -> {
            lengthLabel.setText(String.valueOf(lengthSlider.getValue()));
            generate();
        });

        gc.gridy = 2; p.add(new JSeparator(), gc);
        gc.gridy = 3; p.add(chkUpper,  gc);
        gc.gridy = 4; p.add(chkLower,  gc);
        gc.gridy = 5; p.add(chkDigits, gc);
        gc.gridy = 6; p.add(chkSymbols,gc);
        gc.gridy = 7; p.add(chkNoAmbig,gc);

        for (JCheckBox cb : new JCheckBox[]{chkUpper,chkLower,chkDigits,chkSymbols,chkNoAmbig})
            cb.addActionListener(e -> generate());

        return p;
    }

    private JPanel buildOutput() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Theme.PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0; gc.weightx = 1.0;

        gc.gridy = 0; gc.insets = new Insets(0,0,8,0);
        p.add(Theme.heading("Generated Password"), gc);

        gc.gridy = 1; gc.insets = new Insets(0,0,6,0);
        p.add(outputField, gc);

        // Strength row
        JPanel strRow = new JPanel(new BorderLayout(8, 0));
        strRow.setBackground(Theme.PANEL);
        strRow.add(strengthBar, BorderLayout.CENTER);
        strRow.add(strengthLbl,  BorderLayout.EAST);
        gc.gridy = 2; gc.insets = new Insets(0,0,14,0);
        p.add(strRow, gc);

        // Action buttons
        JButton btnGen    = Theme.primaryBtn("⟳ Regenerate");
        JButton btnCopy   = Theme.successBtn("⎘ Copy");
        JButton btnUse    = Theme.ghostBtn("✔ Use this Password");

        btnGen.addActionListener(e -> generate());
        btnCopy.addActionListener(e -> copyToClipboard());
        btnUse.addActionListener(e -> { result = outputField.getText(); dispose(); });

        JPanel row = new JPanel(new GridLayout(1, 3, 8, 0));
        row.setBackground(Theme.PANEL);
        row.add(btnGen); row.add(btnCopy); row.add(btnUse);
        gc.gridy = 3; p.add(row, gc);

        // Info label
        JLabel info = Theme.label("Passwords are generated locally using SecureRandom.");
        info.setFont(Theme.MONO_SM);
        gc.gridy = 4; gc.insets = new Insets(12,0,0,0);
        p.add(info, gc);

        return p;
    }

    // ── Logic ─────────────────────────────────────────────────────────────

    private void generate() {
        StringBuilder alphabet = new StringBuilder();
        if (chkUpper.isSelected())   alphabet.append(UPPER);
        if (chkLower.isSelected())   alphabet.append(LOWER);
        if (chkDigits.isSelected())  alphabet.append(DIGITS);
        if (chkSymbols.isSelected()) alphabet.append(SYMBOLS);

        if (alphabet.length() == 0) { outputField.setText(""); return; }

        String pool = chkNoAmbig.isSelected()
                ? alphabet.toString().replaceAll("[" + AMBIG + "]", "")
                : alphabet.toString();
        if (pool.isEmpty()) { outputField.setText(""); return; }

        int len = lengthSlider.getValue();
        StringBuilder pw = new StringBuilder(len);

        // Guarantee at least one char from each selected class
        if (chkUpper.isSelected())   pw.append(randomFrom(filtered(UPPER,   pool)));
        if (chkLower.isSelected())   pw.append(randomFrom(filtered(LOWER,   pool)));
        if (chkDigits.isSelected())  pw.append(randomFrom(filtered(DIGITS,  pool)));
        if (chkSymbols.isSelected()) pw.append(randomFrom(filtered(SYMBOLS, pool)));

        while (pw.length() < len) pw.append(pool.charAt(RNG.nextInt(pool.length())));

        // Fisher-Yates shuffle
        char[] arr = pw.toString().toCharArray();
        for (int i = arr.length - 1; i > 0; i--) {
            int j = RNG.nextInt(i + 1);
            char tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
        }

        String pw2 = new String(arr);
        outputField.setText(pw2);
        updateStrength(pw2);
    }

    private void updateStrength(String pw) {
        int score = PasswordStrength.score(pw);
        strengthBar.setValue(score);
        strengthBar.setForeground(Theme.strengthColor(score));
        strengthLbl.setText(PasswordStrength.evaluate(pw).label);
        strengthLbl.setForeground(Theme.strengthColor(score));
    }

    private void copyToClipboard() {
        String pw = outputField.getText();
        if (!pw.isEmpty()) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                   .setContents(new StringSelection(pw), null);
            JOptionPane.showMessageDialog(this,
                    "Password copied to clipboard.", "Copied",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    /** Returns the password the user clicked "Use this Password" on, or null if cancelled. */
    public String getGeneratedPassword() { return result; }

    // ── Static helpers ────────────────────────────────────────────────────

    private static String filtered(String chars, String pool) {
        StringBuilder sb = new StringBuilder();
        for (char c : chars.toCharArray()) if (pool.indexOf(c) >= 0) sb.append(c);
        return sb.length() > 0 ? sb.toString() : pool;
    }

    private static char randomFrom(String s) {
        return s.charAt(RNG.nextInt(s.length()));
    }

    private static JCheckBox check(String label, boolean selected) {
        JCheckBox cb = new JCheckBox(label, selected);
        cb.setBackground(Theme.CARD);
        cb.setForeground(Theme.TEXT);
        cb.setFont(Theme.MONO);
        cb.setFocusPainted(false);
        return cb;
    }

    private static JTextField buildOutputField() {
        JTextField f = new JTextField();
        f.setFont(new Font("Consolas", Font.BOLD, 15));
        f.setBackground(Theme.BG);
        f.setForeground(Theme.ACCENT);
        f.setCaretColor(Theme.ACCENT);
        f.setEditable(false);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_C),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        return f;
    }

    private static JProgressBar buildStrBar() {
        JProgressBar bar = new JProgressBar(0, 5);
        bar.setStringPainted(false);
        bar.setBackground(Theme.PANEL);
        bar.setForeground(Theme.SUCCESS);
        bar.setPreferredSize(new Dimension(0, 6));
        bar.setBorder(BorderFactory.createLineBorder(Theme.BORDER_C));
        return bar;
    }
}
