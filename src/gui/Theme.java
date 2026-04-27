package gui;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * Centralised dark-cyber colour palette and factory helpers.
 * Call Theme.apply() once before creating any Swing component.
 */
public class Theme {

    // ── Palette ───────────────────────────────────────────────────────────
    public static final Color BG        = new Color(10,  14,  23);
    public static final Color PANEL     = new Color(13,  19,  33);
    public static final Color CARD      = new Color(22,  33,  55);
    public static final Color BORDER_C  = new Color(38,  58,  90);
    public static final Color ACCENT    = new Color(0,  180, 216);
    public static final Color SUCCESS   = new Color(45, 198,  83);
    public static final Color WARNING   = new Color(247,183,  49);
    public static final Color DANGER    = new Color(233, 69,  96);
    public static final Color TEXT      = new Color(220,230,242);
    public static final Color DIM       = new Color(120,140,165);
    public static final Color ALT_ROW   = new Color(16,  25,  40);
    public static final Color SEL_BG    = new Color(0,  180, 216, 55);

    // ── Fonts ─────────────────────────────────────────────────────────────
    public static final Font MONO        = mono(Font.PLAIN,  13);
    public static final Font MONO_BOLD   = mono(Font.BOLD,   13);
    public static final Font MONO_SM     = mono(Font.PLAIN,  11);
    public static final Font MONO_LG     = mono(Font.BOLD,   15);
    public static final Font MONO_TITLE  = mono(Font.BOLD,   22);

    private static Font mono(int style, int size) {
        Font f = new Font("Consolas", style, size);
        if (!f.getFamily().equals("Consolas"))
            f = new Font(Font.MONOSPACED, style, size);
        return f;
    }

    // ── Global UIManager defaults ─────────────────────────────────────────
    public static void apply() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}

        String[] bgKeys = {
            "Panel.background","OptionPane.background","Dialog.background",
            "Frame.background","TabbedPane.background","Viewport.background",
            "ScrollPane.background","SplitPane.background"
        };
        for (String k : bgKeys) UIManager.put(k, PANEL);

        UIManager.put("Label.foreground",             TEXT);
        UIManager.put("Label.background",             PANEL);
        UIManager.put("TextField.background",         CARD);
        UIManager.put("TextField.foreground",         TEXT);
        UIManager.put("TextField.caretForeground",    ACCENT);
        UIManager.put("TextField.border",             fieldBorder());
        UIManager.put("PasswordField.background",     CARD);
        UIManager.put("PasswordField.foreground",     TEXT);
        UIManager.put("PasswordField.caretForeground",ACCENT);
        UIManager.put("PasswordField.border",         fieldBorder());
        UIManager.put("TextArea.background",          CARD);
        UIManager.put("TextArea.foreground",          TEXT);
        UIManager.put("ComboBox.background",          CARD);
        UIManager.put("ComboBox.foreground",          TEXT);
        UIManager.put("ComboBox.selectionBackground", ACCENT);
        UIManager.put("ComboBox.selectionForeground", BG);
        UIManager.put("Button.background",            CARD);
        UIManager.put("Button.foreground",            TEXT);
        UIManager.put("Button.focus",                 new Color(0,0,0,0));
        UIManager.put("Table.background",             PANEL);
        UIManager.put("Table.foreground",             TEXT);
        UIManager.put("Table.gridColor",              BORDER_C);
        UIManager.put("Table.selectionBackground",    CARD);
        UIManager.put("Table.selectionForeground",    ACCENT);
        UIManager.put("TableHeader.background",       BG);
        UIManager.put("TableHeader.foreground",       DIM);
        UIManager.put("TableHeader.font",             MONO_SM);
        UIManager.put("ScrollBar.background",         PANEL);
        UIManager.put("ScrollBar.thumb",              CARD);
        UIManager.put("ScrollBar.track",              PANEL);
        UIManager.put("List.background",              PANEL);
        UIManager.put("List.foreground",              TEXT);
        UIManager.put("List.selectionBackground",     CARD);
        UIManager.put("List.selectionForeground",     ACCENT);
        UIManager.put("ToolTip.background",           CARD);
        UIManager.put("ToolTip.foreground",           TEXT);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("CheckBox.background",          PANEL);
        UIManager.put("CheckBox.foreground",          TEXT);
        UIManager.put("Slider.background",            PANEL);
        UIManager.put("Slider.foreground",            ACCENT);
        UIManager.put("Slider.thumbColor",            ACCENT);
        UIManager.put("Separator.foreground",         BORDER_C);
        UIManager.put("TitledBorder.titleColor",      DIM);
    }

    // ── Button factories ──────────────────────────────────────────────────

    public static JButton primaryBtn(String text) {
        return styledBtn(text, ACCENT, BG);
    }
    public static JButton successBtn(String text) {
        return styledBtn(text, SUCCESS, BG);
    }
    public static JButton dangerBtn(String text) {
        return styledBtn(text, DANGER, Color.WHITE);
    }
    public static JButton ghostBtn(String text) {
        JButton b = styledBtn(text, PANEL, DIM);
        b.setBorder(BorderFactory.createLineBorder(BORDER_C));
        return b;
    }

    private static JButton styledBtn(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFont(MONO_BOLD);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        return b;
    }

    // ── Field factories ───────────────────────────────────────────────────

    public static JTextField field() {
        JTextField f = new JTextField();
        f.setBackground(CARD);
        f.setForeground(TEXT);
        f.setCaretColor(ACCENT);
        f.setFont(MONO);
        f.setBorder(fieldBorder());
        return f;
    }

    public static JPasswordField passField() {
        JPasswordField f = new JPasswordField();
        f.setBackground(CARD);
        f.setForeground(TEXT);
        f.setCaretColor(ACCENT);
        f.setFont(MONO);
        f.setBorder(fieldBorder());
        return f;
    }

    // ── Label factories ───────────────────────────────────────────────────

    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(DIM);
        l.setFont(MONO);
        return l;
    }

    public static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT);
        l.setFont(MONO_LG);
        return l;
    }

    // ── Panel factories ───────────────────────────────────────────────────

    public static JPanel cardPanel() {
        JPanel p = new JPanel();
        p.setBackground(CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_C),
                BorderFactory.createEmptyBorder(14, 18, 14, 18)));
        return p;
    }

    // ── Borders ───────────────────────────────────────────────────────────

    public static Border fieldBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_C),
                BorderFactory.createEmptyBorder(6, 9, 6, 9));
    }

    public static Border sectionBorder(String title) {
        return BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER_C), title,
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                MONO_SM, DIM);
    }

    // ── Strength bar ──────────────────────────────────────────────────────

    public static Color strengthColor(int score) {
        return switch (score) {
            case 0  -> DANGER;
            case 1  -> DANGER;
            case 2  -> WARNING;
            case 3  -> WARNING;
            case 4  -> SUCCESS;
            default -> SUCCESS;
        };
    }
}
