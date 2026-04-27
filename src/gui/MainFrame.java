package gui;

import client.VaultClient;
import client.VaultClient.SecretDetail;
import client.VaultClient.SecretEntry;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.List;

/**
 * Main application dashboard shown after successful login.
 *
 * Layout:
 *   ┌─────────────────────────────────────────────────┐
 *   │  Header: logo | search | user | logout          │
 *   ├──────────┬──────────────────────────────────────┤
 *   │ Category │  Secrets table                       │
 *   │ sidebar  ├──────────────────────────────────────┤
 *   │          │  Detail panel (revealed on selection)│
 *   ├──────────┴──────────────────────────────────────┤
 *   │  Footer: Add | Generator | Audit | Change Pass  │
 *   └─────────────────────────────────────────────────┘
 */
public class MainFrame extends JFrame {

    private static final String[] TABLE_COLS = { "#", "Name", "Category", "Created" };
    private static final int[]    COL_W      = { 36, 200, 110, 170 };

    private final VaultClient client;
    private final String      token;
    private final String      username;
    private final JFrame      loginFrame;

    // Table
    private final DefaultTableModel tableModel = new DefaultTableModel(TABLE_COLS, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
        public Class<?> getColumnClass(int c) { return c == 0 ? Integer.class : String.class; }
    };
    private final JTable            table     = buildTable();
    private final TableRowSorter<DefaultTableModel> sorter =
            new TableRowSorter<>(tableModel);

    // Filter
    private final JTextField searchField = buildSearchField();

    // Category sidebar
    private static final String[] CATS = {
        "All", "PASSWORD", "API_KEY", "NOTE", "SSH_KEY", "CREDIT_CARD"
    };
    private final JList<String> catList = buildCatList();
    private String activeCategory = "All";

    // Detail panel
    private final JLabel  detailName   = styledDetail();
    private final JLabel  detailCat    = Theme.label("—");
    private final JLabel  detailDate   = Theme.label("—");
    private final JTextField detailVal = buildDetailVal();
    private final JPanel  detailPanel  = buildDetailPanel();

    // Status
    private final JLabel statusLbl = Theme.label("Ready");

    public MainFrame(VaultClient client, String token, String username, JFrame loginFrame) {
        super("VAULT  —  " + username);
        this.client     = client;
        this.token      = token;
        this.username   = username;
        this.loginFrame = loginFrame;

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout(0, 0));

        add(buildHeader(),   BorderLayout.NORTH);
        add(buildCenter(),   BorderLayout.CENTER);
        add(buildFooter(),   BorderLayout.SOUTH);

        table.setRowSorter(sorter);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onRowSelected();
        });

        searchField.getDocument().addDocumentListener(
            new javax.swing.event.DocumentListener() {
                public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
                public void insertUpdate (javax.swing.event.DocumentEvent e) { applyFilter(); }
                public void removeUpdate (javax.swing.event.DocumentEvent e) { applyFilter(); }
            });

        catList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                activeCategory = catList.getSelectedValue();
                applyFilter();
            }
        });

        setSize(920, 620);
        setMinimumSize(new Dimension(780, 500));
        setLocationRelativeTo(null);
        refreshSecrets();
    }

    // ── Header ────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setBackground(Theme.BG);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_C),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));

        JLabel logo = new JLabel("⬡ VAULT");
        logo.setForeground(Theme.ACCENT);
        logo.setFont(Theme.MONO_LG);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setBackground(Theme.BG);
        JLabel userLbl = Theme.label("🔐 " + username);
        JButton logout = Theme.dangerBtn("Logout");
        logout.addActionListener(e -> doLogout());
        right.add(userLbl);
        right.add(logout);

        p.add(logo,         BorderLayout.WEST);
        p.add(searchField,  BorderLayout.CENTER);
        p.add(right,        BorderLayout.EAST);
        return p;
    }

    // ── Center (sidebar + table + detail) ─────────────────────────────────

    private JSplitPane buildCenter() {
        // Left: category sidebar
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(Theme.PANEL);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER_C));
        JLabel catHead = Theme.label("CATEGORIES");
        catHead.setFont(Theme.MONO_SM);
        catHead.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
        sidebar.add(catHead, BorderLayout.NORTH);
        sidebar.add(new JScrollPane(catList), BorderLayout.CENTER);

        // Right: table + detail pane
        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildTablePanel(), detailPanel);
        right.setResizeWeight(0.7);
        right.setDividerSize(4);
        right.setBackground(Theme.PANEL);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, right);
        split.setDividerLocation(145);
        split.setDividerSize(4);
        split.setBackground(Theme.BG);
        return split;
    }

    private JScrollPane buildTablePanel() {
        JScrollPane sp = new JScrollPane(table);
        sp.setBackground(Theme.PANEL);
        sp.getViewport().setBackground(Theme.PANEL);
        sp.setBorder(BorderFactory.createEmptyBorder());
        return sp;
    }

    // ── Footer ────────────────────────────────────────────────────────────

    private JPanel buildFooter() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.BG);
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_C));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        btns.setBackground(Theme.BG);

        JButton addBtn  = Theme.successBtn("＋ Add Secret");
        JButton genBtn  = Theme.ghostBtn("⚙ Generator");
        JButton auditBtn= Theme.ghostBtn("📋 Audit Log");
        JButton chpBtn  = Theme.ghostBtn("🔑 Change Password");
        JButton delBtn  = Theme.dangerBtn("✕ Delete");

        addBtn.addActionListener(e   -> openAddDialog(null));
        genBtn.addActionListener(e   -> new PasswordGeneratorDialog(this).setVisible(true));
        auditBtn.addActionListener(e -> new AuditLogDialog(this, client, token).setVisible(true));
        chpBtn.addActionListener(e   -> openChangePassword());
        delBtn.addActionListener(e   -> deleteSelected());

        btns.add(addBtn); btns.add(genBtn); btns.add(auditBtn);
        btns.add(chpBtn); btns.add(delBtn);

        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        statusRow.setBackground(Theme.BG);
        statusRow.add(statusLbl);

        p.add(btns, BorderLayout.WEST);
        p.add(statusRow, BorderLayout.EAST);
        return p;
    }

    // ── Detail panel ──────────────────────────────────────────────────────

    private JPanel buildDetailPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Theme.CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_C),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(2, 0, 2, 12);
        gc.anchor = GridBagConstraints.WEST;

        // Row 0: name + category + date
        gc.gridy = 0; gc.gridx = 0; p.add(Theme.label("Name:"), gc);
        gc.gridx = 1; p.add(detailName, gc);
        gc.gridx = 2; p.add(Theme.label("Category:"), gc);
        gc.gridx = 3; p.add(detailCat, gc);
        gc.gridx = 4; p.add(Theme.label("Created:"), gc);
        gc.gridx = 5; p.add(detailDate, gc);

        // Row 1: value + buttons
        JPanel valRow = new JPanel(new BorderLayout(6, 0));
        valRow.setBackground(Theme.CARD);

        JButton revealBtn = Theme.ghostBtn("👁 Reveal");
        JButton copyBtn   = Theme.ghostBtn("⎘ Copy");
        JButton editBtn   = Theme.primaryBtn("Edit");

        revealBtn.addActionListener(e -> revealSecret());
        copyBtn.addActionListener(e   -> copySecret());
        editBtn.addActionListener(e   -> editSelected());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setBackground(Theme.CARD);
        actions.add(revealBtn); actions.add(copyBtn); actions.add(editBtn);

        valRow.add(detailVal, BorderLayout.CENTER);
        valRow.add(actions,   BorderLayout.EAST);

        gc.gridy = 1; gc.gridx = 0; gc.gridwidth = 6;
        gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1.0;
        gc.insets = new Insets(8, 0, 0, 0);
        p.add(valRow, gc);

        p.setVisible(false);
        return p;
    }

    // ── Actions ───────────────────────────────────────────────────────────

    private void refreshSecrets() {
        setStatus("Loading…");
        SwingWorker<List<SecretEntry>, Void> w = new SwingWorker<>() {
            protected List<SecretEntry> doInBackground() throws Exception {
                return client.listSecrets(token);
            }
            protected void done() {
                try {
                    List<SecretEntry> list = get();
                    tableModel.setRowCount(0);
                    int i = 1;
                    for (SecretEntry e : list)
                        tableModel.addRow(new Object[]{ i++, e.name, e.category, e.createdAt });
                    setStatus(list.size() + " secret" + (list.size() != 1 ? "s" : ""));
                    detailPanel.setVisible(false);
                    applyFilter();
                } catch (Exception ex) {
                    setStatus("Error: " + rootCause(ex));
                }
            }
        };
        w.execute();
    }

    private void onRowSelected() {
        int row = table.getSelectedRow();
        if (row < 0) { detailPanel.setVisible(false); return; }
        String name = (String) table.getValueAt(row, 1);
        String cat  = (String) table.getValueAt(row, 2);
        String date = (String) table.getValueAt(row, 3);
        detailName.setText(name);
        detailCat.setText(cat);
        detailDate.setText(date != null ? date.substring(0, Math.min(19, date.length())) : "");
        detailVal.setText("••••••••");
        detailPanel.setVisible(true);
    }

    private void revealSecret() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        String name = (String) table.getValueAt(row, 1);
        setStatus("Fetching…");
        SwingWorker<SecretDetail, Void> w = new SwingWorker<>() {
            protected SecretDetail doInBackground() throws Exception {
                return client.getSecret(token, name);
            }
            protected void done() {
                try {
                    SecretDetail d = get();
                    detailVal.setText(d.value);
                    setStatus("Ready");
                } catch (Exception ex) {
                    setStatus("Error: " + rootCause(ex));
                }
            }
        };
        w.execute();
    }

    private void copySecret() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        String name = (String) table.getValueAt(row, 1);
        setStatus("Copying…");
        SwingWorker<SecretDetail, Void> w = new SwingWorker<>() {
            protected SecretDetail doInBackground() throws Exception {
                return client.getSecret(token, name);
            }
            protected void done() {
                try {
                    SecretDetail d = get();
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                           .setContents(new StringSelection(d.value), null);
                    setStatus("Copied to clipboard.");
                } catch (Exception ex) {
                    setStatus("Error: " + rootCause(ex));
                }
            }
        };
        w.execute();
    }

    private void openAddDialog(String prefillName) {
        AddSecretDialog dlg = new AddSecretDialog(this, client, token, null, null, null);
        dlg.setVisible(true);
        if (dlg.isSaved()) refreshSecrets();
    }

    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        String name = (String) table.getValueAt(row, 1);
        String cat  = (String) table.getValueAt(row, 2);
        setStatus("Loading secret for edit…");
        SwingWorker<SecretDetail, Void> w = new SwingWorker<>() {
            protected SecretDetail doInBackground() throws Exception {
                return client.getSecret(token, name);
            }
            protected void done() {
                try {
                    SecretDetail d = get();
                    AddSecretDialog dlg = new AddSecretDialog(
                            MainFrame.this, client, token, name, cat, d.value);
                    dlg.setVisible(true);
                    if (dlg.isSaved()) refreshSecrets();
                    else setStatus("Ready");
                } catch (Exception ex) {
                    setStatus("Error: " + rootCause(ex));
                }
            }
        };
        w.execute();
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this,
                "Select a secret first.", "No selection", JOptionPane.WARNING_MESSAGE); return; }
        String name = (String) table.getValueAt(row, 1);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Permanently delete \"" + name + "\"?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        SwingWorker<Void, Void> w = new SwingWorker<>() {
            protected Void doInBackground() throws Exception {
                client.deleteSecret(token, name);
                return null;
            }
            protected void done() {
                try { get(); refreshSecrets(); }
                catch (Exception ex) { setStatus("Error: " + rootCause(ex)); }
            }
        };
        w.execute();
    }

    private void openChangePassword() {
        JPasswordField oldPw = Theme.passField();
        JPasswordField newPw = Theme.passField();
        JPasswordField cfmPw = Theme.passField();
        JPanel form = new JPanel(new GridLayout(6, 1, 0, 4));
        form.setBackground(Theme.PANEL);
        form.add(Theme.label("Current password:")); form.add(oldPw);
        form.add(Theme.label("New password:"));     form.add(newPw);
        form.add(Theme.label("Confirm new:"));      form.add(cfmPw);

        int res = JOptionPane.showConfirmDialog(this, form,
                "Change Master Password", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        String oldP = new String(oldPw.getPassword());
        String newP = new String(newPw.getPassword());
        String cfm  = new String(cfmPw.getPassword());
        if (!newP.equals(cfm)) {
            JOptionPane.showMessageDialog(this, "New passwords do not match.",
                    "Error", JOptionPane.ERROR_MESSAGE); return;
        }
        setStatus("Changing password…");
        SwingWorker<Void, Void> w = new SwingWorker<>() {
            protected Void doInBackground() throws Exception {
                client.changePassword(token, oldP, newP);
                return null;
            }
            protected void done() {
                try { get(); setStatus("Password changed successfully."); }
                catch (Exception ex) { setStatus("Error: " + rootCause(ex)); }
            }
        };
        w.execute();
    }

    private void doLogout() {
        SwingWorker<Void, Void> w = new SwingWorker<>() {
            protected Void doInBackground() throws Exception {
                client.logout(token); return null;
            }
            protected void done() {
                dispose();
                loginFrame.setVisible(true);
            }
        };
        w.execute();
    }

    // ── Filter ────────────────────────────────────────────────────────────

    private void applyFilter() {
        String text = searchField.getText().trim().toLowerCase();
        sorter.setRowFilter(RowFilter.andFilter(java.util.Arrays.asList(
            RowFilter.regexFilter("(?i)" + (text.isEmpty() ? ".*" : java.util.regex.Pattern.quote(text)), 1, 2),
            "All".equals(activeCategory)
                ? RowFilter.regexFilter(".*", 2)
                : RowFilter.regexFilter("^" + activeCategory + "$", 2)
        )));
    }

    // ── Component builders ────────────────────────────────────────────────

    private JTable buildTable() {
        JTable t = new JTable(tableModel);
        t.setBackground(Theme.PANEL);
        t.setForeground(Theme.TEXT);
        t.setFont(Theme.MONO);
        t.setRowHeight(26);
        t.setGridColor(Theme.BORDER_C);
        t.setShowHorizontalLines(true);
        t.setShowVerticalLines(false);
        t.setSelectionBackground(Theme.CARD);
        t.setSelectionForeground(Theme.ACCENT);
        t.getTableHeader().setBackground(Theme.BG);
        t.getTableHeader().setForeground(Theme.DIM);
        t.getTableHeader().setFont(Theme.MONO_SM);
        t.setFillsViewportHeight(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        for (int i = 0; i < COL_W.length; i++)
            t.getColumnModel().getColumn(i).setPreferredWidth(COL_W[i]);
        t.getColumnModel().getColumn(0).setMaxWidth(44);

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable tbl, Object val,
                    boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(tbl, val, sel, focus, row, col);
                setFont(col == 0 ? Theme.MONO_SM : Theme.MONO);
                setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
                if (!sel) {
                    setBackground(row % 2 == 0 ? Theme.PANEL : Theme.ALT_ROW);
                    setForeground(col == 2 ? Theme.DIM : Theme.TEXT);
                } else {
                    setBackground(Theme.CARD);
                    setForeground(Theme.ACCENT);
                }
                return this;
            }
        };
        for (int i = 0; i < TABLE_COLS.length; i++)
            t.getColumnModel().getColumn(i).setCellRenderer(renderer);

        // Double-click to reveal
        t.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) revealSecret();
            }
        });
        return t;
    }

    private JList<String> buildCatList() {
        JList<String> list = new JList<>(CATS);
        list.setBackground(Theme.PANEL);
        list.setForeground(Theme.TEXT);
        list.setFont(Theme.MONO);
        list.setSelectionBackground(Theme.CARD);
        list.setSelectionForeground(Theme.ACCENT);
        list.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        list.setSelectedIndex(0);
        list.setCellRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> l, Object val,
                    int idx, boolean sel, boolean focus) {
                super.getListCellRendererComponent(l, val, idx, sel, focus);
                setFont(Theme.MONO_SM);
                setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                setBackground(sel ? Theme.CARD : Theme.PANEL);
                setForeground(sel ? Theme.ACCENT : Theme.DIM);
                return this;
            }
        });
        return list;
    }

    private JTextField buildSearchField() {
        JTextField f = Theme.field();
        f.setPreferredSize(new Dimension(260, 30));
        f.putClientProperty("JTextField.placeholderText", "Search secrets…");
        // Fallback placeholder via FocusListener
        f.setForeground(Theme.DIM);
        f.setText("Search secrets…");
        f.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (f.getText().equals("Search secrets…")) { f.setText(""); f.setForeground(Theme.TEXT); }
            }
            public void focusLost(FocusEvent e) {
                if (f.getText().isEmpty()) { f.setText("Search secrets…"); f.setForeground(Theme.DIM); }
            }
        });
        return f;
    }

    private static JLabel styledDetail() {
        JLabel l = new JLabel("—");
        l.setForeground(Theme.TEXT);
        l.setFont(Theme.MONO_BOLD);
        return l;
    }

    private static JTextField buildDetailVal() {
        JTextField f = new JTextField("••••••••");
        f.setBackground(Theme.BG);
        f.setForeground(Theme.ACCENT);
        f.setFont(new Font("Consolas", Font.BOLD, 13));
        f.setEditable(false);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_C),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return f;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void setStatus(String msg) {
        statusLbl.setText(msg);
    }

    private static String rootCause(Exception e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
