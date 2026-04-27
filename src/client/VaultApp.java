package client;

import gui.LoginFrame;
import gui.Theme;
import javax.swing.SwingUtilities;

/**
 * Entry point for the Vault GUI client.
 * Start the server (server.Server) first, then run this class.
 */
public class VaultApp {
    public static void main(String[] args) {
        Theme.apply();
        SwingUtilities.invokeLater(() -> new LoginFrame("localhost", 9090).setVisible(true));
    }
}
