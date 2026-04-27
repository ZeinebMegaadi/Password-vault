package gui;

import client.VaultClient;
import client.VaultClient.AuditEntry;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;

/**
 * Read-only table of audit log entries for the current user.
 * Actions are colour-coded: logins green, failures red, mutations yellow.
 */
public class AuditLogDialog extends JDialog {

    private static final String[] COLS = { "Timestamp", "Action", "Details", "IP Address" };
    private static final int[] COL_W   = { 180, 140, 220, 110 };

    private final VaultClient   client;
    private final String        token;
    private final DefaultTableModel model = new DefaultTableModel(COLS, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };

    public AuditLogDialog(Window owner, VaultClient client, String token) {
        super(owner, "Audit Log", ModalityType.APPLICATION_MODAL);
        this.client = client;
        this.token  = token;

        getContentPane().setBackground(Theme.PANEL);
        setLayout(new BorderLayout(0, 0));
        add(buildHeader(),  BorderLayout.NORTH);
        add(buildTable(),   BorderLayout.CENTER);
        add(buildFooter(),  BorderLayout.SOUTH);

        setSize(700, 440);
        setMinimumSize(new Dimension(600, 300));
        setLocationRelativeTo(owner);
        loadData();
    }

    // ── Panels ────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.BG);
        p.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel title = Theme.heading("Security Audit Log");
        JLabel sub   = Theme.label("Last 200 events for your account");
        sub.setFont(Theme.MONO_SM);

        JPanel text = new JPanel(new GridLayout(2, 1, 0, 2));
        text.setBackground(Theme.BG);
        text.add(title);
        text.add(sub);
        p.add(text, BorderLayout.WEST);
        return p;
    }

    private JScrollPane buildTable() {
        JTable table = new JTable(model);
        table.setBackground(Theme.PANEL);
        table.setForeground(Theme.TEXT);
        table.setFont(Theme.MONO_SM);
        table.setRowHeight(24);
        table.setGridColor(Theme.BORDER_C);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setSelectionBackground(Theme.CARD);
        table.setSelectionForeground(Theme.ACCENT);
        table.getTableHeader().setBackground(Theme.BG);
        table.getTableHeader().setForeground(Theme.DIM);
        table.getTableHeader().setFont(Theme.MONO_SM);
        table.setFillsViewportHeight(true);

        // Column widths
        for (int i = 0; i < COL_W.length; i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(COL_W[i]);

        // Colour rows by action type
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, focus, row, col);
                setBackground(row % 2 == 0 ? Theme.PANEL : Theme.ALT_ROW);
                setForeground(Theme.TEXT);
                setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
                if (!sel) {
                    String action = (String) model.getValueAt(row, 1);
                    if (action != null) {
                        if (action.contains("FAIL"))   setForeground(Theme.DANGER);
                        else if (action.contains("DELETE")) setForeground(Theme.WARNING);
                        else if (action.startsWith("LOGIN") || action.equals("REGISTER"))
                            setForeground(Theme.SUCCESS);
                    }
                }
                setFont(Theme.MONO_SM);
                return this;
            }
        });

        JScrollPane sp = new JScrollPane(table);
        sp.setBackground(Theme.PANEL);
        sp.getViewport().setBackground(Theme.PANEL);
        sp.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.BORDER_C));
        return sp;
    }

    private JPanel buildFooter() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        p.setBackground(Theme.PANEL);

        JButton refresh  = Theme.ghostBtn("⟳ Refresh");
        JButton export   = Theme.ghostBtn("⎘ Export CSV");
        JButton close    = Theme.primaryBtn("Close");

        refresh.addActionListener(e -> loadData());
        export.addActionListener(e  -> exportCsv());
        close.addActionListener(e   -> dispose());

        p.add(refresh); p.add(export); p.add(close);
        return p;
    }

    // ── Data ──────────────────────────────────────────────────────────────

    private void loadData() {
        model.setRowCount(0);
        SwingWorker<List<AuditEntry>, Void> w = new SwingWorker<>() {
            protected List<AuditEntry> doInBackground() throws Exception {
                return client.getAuditLog(token);
            }
            protected void done() {
                try {
                    for (AuditEntry e : get()) {
                        model.addRow(new Object[]{ e.timestamp, e.action, e.details, e.ip });
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(AuditLogDialog.this,
                            "Failed to load audit log: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        w.execute();
    }

    private void exportCsv() {
        StringBuilder sb = new StringBuilder("Timestamp,Action,Details,IP\n");
        for (int r = 0; r < model.getRowCount(); r++) {
            for (int c = 0; c < model.getColumnCount(); c++) {
                if (c > 0) sb.append(',');
                Object v = model.getValueAt(r, c);
                String cell = v == null ? "" : v.toString().replace("\"", "\"\"");
                sb.append('"').append(cell).append('"');
            }
            sb.append('\n');
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
               .setContents(new StringSelection(sb.toString()), null);
        JOptionPane.showMessageDialog(this,
                model.getRowCount() + " rows copied to clipboard as CSV.",
                "Exported", JOptionPane.INFORMATION_MESSAGE);
    }
}
