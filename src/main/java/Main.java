import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
    public static void main(String[] args) {
        // You can use print statements as follows for debugging, they'll be visible
        // when running tests.
        System.out.println("Logs from your program will appear here!");

        try {
            ServerSocket serverSocket = new ServerSocket(4221);

            serverSocket.setReuseAddress(true);

            // create new socket object when client sent request
            Socket clientSocket = serverSocket.accept(); // Wait for connection from client.
            System.out.println("accepted new connection");

            // object to read from the socket
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // object ot write to the socket
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            String requestMessage, responseMessage;

            // reading form the socket
            requestMessage = in.readLine();
            System.out.println("The received message from the client: " + requestMessage);

            responseMessage = requestMessage.toUpperCase();

            // writing to the socket
            responseMessage = "HTTP/1.1 200 OK\r\n\r\n";
            out.println(responseMessage);
            System.out.println("Modified message sent to the client: " + responseMessage);

        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }
}
