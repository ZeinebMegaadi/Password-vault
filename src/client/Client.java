package client;
import java.io.*;
import java.net.Socket;

public class Client {

    public static void main(String[] args) throws Exception {

        Socket socket = new Socket("localhost", 9090);
        BufferedReader userInput = new BufferedReader(
                new InputStreamReader(System.in));

        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        System.out.print("Enter command: ");
        String msg = userInput.readLine();
        out.println(msg);
   
   
   
    }
}