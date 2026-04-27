package server;

import db.DatabaseUtility;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    public static final int PORT = 9090;

    public static void main(String[] args) throws Exception {
        System.out.println("[VAULT] Initialising database schema...");
        DatabaseUtility.initSchema();
        System.out.println("[VAULT] Schema ready.");

        try (ServerSocket srv = new ServerSocket(PORT)) {
            System.out.println("[VAULT] Listening on port " + PORT);
            while (true) {
                Socket client = srv.accept();
                Thread t = new Thread(new ClientHandler(client));
                t.setDaemon(true);
                t.start();
            }
        }
    }
}
