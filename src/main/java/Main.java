import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        System.out.println("Logs from your program will appear here!");

        try {
            ServerSocket serverSocket = new ServerSocket(4221);
            serverSocket.setReuseAddress(true);

            // Continuously accept new connections
            while (true) {
                // Wait for connection from client
                Socket clientSocket = serverSocket.accept();
                System.out.println("accepted new connection");

                // Handle each connection in a separate thread
                new Thread(() -> handleConnection(clientSocket)).start();
            }

        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    private static void handleConnection(Socket clientSocket) {
        try {
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

            // Read and parse HTTP headers
            String userAgent = null;
            String headerLine;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                System.out.println("Header: " + headerLine);
                if (headerLine.toLowerCase().startsWith("user-agent:")) {
                    userAgent = headerLine.substring("user-agent:".length()).trim();
                }
            }

            // Generate appropriate response based on the path
            String responseMessage;
            if ("/".equals(path)) {
                responseMessage = "HTTP/1.1 200 OK\r\n\r\n";
            } else if (path != null && path.startsWith("/echo/")) {
                // Extract the string after "/echo/"
                String echoString = path.substring("/echo/".length());
                int contentLength = echoString.length();
                
                responseMessage = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/plain\r\n" +
                                "Content-Length: " + contentLength + "\r\n" +
                                "\r\n" +
                                echoString;
            } else if ("/user-agent".equals(path)) {
                // Return the User-Agent header value
                if (userAgent != null) {
                    int contentLength = userAgent.length();
                    responseMessage = "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/plain\r\n" +
                                    "Content-Length: " + contentLength + "\r\n" +
                                    "\r\n" +
                                    userAgent;
                } else {
                    responseMessage = "HTTP/1.1 400 Bad Request\r\n\r\n";
                }
            } else if (path != null && path.startsWith("/files/")) {
                // Handle file serving
                String filename = path.substring("/files/".length());
                responseMessage = handleFileRequest(filename);
            } else {
                responseMessage = "HTTP/1.1 404 Not Found\r\n\r\n";
            }

            // Send the response
            out.print(responseMessage);
            out.flush();
            System.out.println("Response sent to client: " + responseMessage.trim());

            // Close the connection
            clientSocket.close();

        } catch (IOException e) {
            System.out.println("IOException in handleConnection: " + e.getMessage());
        }
    }

    private static String handleFileRequest(String filename) {
        try {
            // Create path to the file in the files directory
            Path filePath = Paths.get("files", filename);
            
            // Check if file exists
            if (!Files.exists(filePath)) {
                return "HTTP/1.1 404 Not Found\r\n\r\n";
            }
            
            // Read file contents
            byte[] fileContent = Files.readAllBytes(filePath);
            String content = new String(fileContent);
            
            // Create response with proper headers
            return "HTTP/1.1 200 OK\r\n" +
                   "Content-Type: application/octet-stream\r\n" +
                   "Content-Length: " + fileContent.length + "\r\n" +
                   "\r\n" +
                   content;
                   
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
            return "HTTP/1.1 404 Not Found\r\n\r\n";
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