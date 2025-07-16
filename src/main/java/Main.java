import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
    public static void main(String[] args) {
        System.out.println("Logs from your program will appear here!");

        try {
            ServerSocket serverSocket = new ServerSocket(4221);
            serverSocket.setReuseAddress(true);

            // create new socket object when client sent request
            Socket clientSocket = serverSocket.accept(); // Wait for connection from client.
            System.out.println("accepted new connection");

            // object to read from the socket
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // object to write to the socket
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            // reading the first line of the HTTP request
            String requestLine = in.readLine();
            System.out.println("The received message from the client: " + requestLine);

            // Parse the HTTP request line to extract the path
            String path = parsePathFromRequest(requestLine);
            System.out.println("Extracted path: " + path);

            // Generate appropriate response based on the path
            String responseMessage;
            if ("/".equals(path)) {
                responseMessage = "HTTP/1.1 200 OK\r\n\r\n";
            } else {
                responseMessage = "HTTP/1.1 404 Not Found\r\n\r\n";
            }

            // Send the response
            out.print(responseMessage);
            out.flush();
            System.out.println("Response sent to client: " + responseMessage.trim());

            // Close the connection
            clientSocket.close();
            serverSocket.close();

        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    private static String parsePathFromRequest(String requestLine) {
        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }

        // HTTP request line format: METHOD PATH HTTP_VERSION
        // Example: "GET /abcdefg HTTP/1.1"
        String[] parts = requestLine.split(" ");
        
        if (parts.length >= 2) {
            return parts[1]; // Return the path part
        }
        
        return null;
    }
}