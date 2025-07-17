import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPOutputStream;

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

            // reading the client request
            String requestLine = in.readLine();
            System.out.println("The received message from the client: " + requestLine);

            // Parse the HTTP request line to extract the method and path
            String method = parseMethodFromRequest(requestLine);
            String path = parsePathFromRequest(requestLine);
            System.out.println("Method: " + method + ", Path: " + path);

            // Read and parse HTTP headers
            String userAgent = null;
            String acceptEncoding = null;
            int contentLength = 0;
            String headerLine;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                System.out.println("Header: " + headerLine);
                if (headerLine.toLowerCase().startsWith("user-agent:")) {
                    userAgent = headerLine.substring("user-agent:".length()).trim();
                } else if (headerLine.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(headerLine.substring("content-length:".length()).trim());
                } else if (headerLine.toLowerCase().startsWith("accept-encoding:")) {
                    acceptEncoding = headerLine.substring("accept-encoding:".length()).trim();
                }
            }

            // Read request body if Content-Length is specified
            String requestBody = null;
            if (contentLength > 0) {
                char[] buffer = new char[contentLength];
                in.read(buffer, 0, contentLength);
                requestBody = new String(buffer);
                System.out.println("Request body: " + requestBody);
            }

            // Generate appropriate response based on the path
            if ("/".equals(path)) {
                sendTextResponse(clientSocket, "HTTP/1.1 200 OK\r\n\r\n");
            } else if (path != null && path.startsWith("/echo/")) {
                // Extract the string after "/echo/"
                String echoString = path.substring("/echo/".length());
                handleEchoRequest(clientSocket, echoString, acceptEncoding);
            } else if ("/user-agent".equals(path)) {
                // Return the User-Agent header value
                if (userAgent != null) {
                    int agentLength = userAgent.length();
                    String response = "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/plain\r\n" +
                                    "Content-Length: " + agentLength + "\r\n" +
                                    "\r\n" +
                                    userAgent;
                    sendTextResponse(clientSocket, response);
                } else {
                    sendTextResponse(clientSocket, "HTTP/1.1 400 Bad Request\r\n\r\n");
                }
            } else if (path != null && path.startsWith("/files/")) {
                // Handle file serving
                String filename = path.substring("/files/".length());
                String response = handleFileRequest(method, filename, requestBody);
                sendTextResponse(clientSocket, response);
            } else {
                sendTextResponse(clientSocket, "HTTP/1.1 404 Not Found\r\n\r\n");
            }

            // Close the connection
            clientSocket.close();

        } catch (IOException e) {
            System.out.println("IOException in handleConnection: " + e.getMessage());
        }
    }

    private static void sendTextResponse(Socket clientSocket, String response) throws IOException {
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        out.print(response);
        out.flush();
        System.out.println("Response sent to client: " + response.trim());
    }

    private static void handleEchoRequest(Socket clientSocket, String echoString, String acceptEncoding) throws IOException {
        boolean supportsGzip = supportsGzipEncoding(acceptEncoding);
        
        if (supportsGzip) {
            // Compress the response body with gzip
            byte[] compressedData = compressWithGzip(echoString);
            
            // Build response headers
            String headers = "HTTP/1.1 200 OK\r\n" +
                           "Content-Type: text/plain\r\n" +
                           "Content-Encoding: gzip\r\n" +
                           "Content-Length: " + compressedData.length + "\r\n" +
                           "\r\n";
            
            // Send headers as text, then compressed body as binary
            clientSocket.getOutputStream().write(headers.getBytes());
            clientSocket.getOutputStream().write(compressedData);
            clientSocket.getOutputStream().flush();
            
            System.out.println("Sent gzip compressed response for: " + echoString + " (compressed size: " + compressedData.length + ")");
        } else {
            // Send uncompressed response
            int echoLength = echoString.length();
            String response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: " + echoLength + "\r\n" +
                            "\r\n" +
                            echoString;
            sendTextResponse(clientSocket, response);
        }
    }

    private static byte[] compressWithGzip(String data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(data.getBytes());
        }
        return baos.toByteArray();
    }

    private static String handleFileRequest(String method, String filename, String requestBody) {
        try {
            // Create path to the file in the files directory
            Path filePath = Paths.get("files", filename);
            
            if ("GET".equals(method)) {
                // Handle GET request - serve existing file
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
                       
            } else if ("POST".equals(method)) {
                // Handle POST request - create new file
                if (requestBody == null) {
                    return "HTTP/1.1 400 Bad Request\r\n\r\n";
                }
                
                // Create files directory if it doesn't exist
                Path filesDir = Paths.get("files");
                if (!Files.exists(filesDir)) {
                    Files.createDirectories(filesDir);
                }
                
                // Write request body to file
                Files.write(filePath, requestBody.getBytes());
                
                // Return 201 Created response
                return "HTTP/1.1 201 Created\r\n\r\n";
                
            } else {
                return "HTTP/1.1 405 Method Not Allowed\r\n\r\n";
            }
                   
        } catch (IOException e) {
            System.out.println("Error handling file request: " + e.getMessage());
            return "HTTP/1.1 500 Internal Server Error\r\n\r\n";
        }
    }

    private static boolean supportsGzipEncoding(String acceptEncoding) {
        if (acceptEncoding == null || acceptEncoding.isEmpty()) {
            return false;
        }
        
        // Split by comma and check each encoding
        String[] encodings = acceptEncoding.split(",");
        for (String encoding : encodings) {
            // Trim whitespace and check if it's exactly "gzip"
            if ("gzip".equals(encoding.trim().toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }

    private static String parseMethodFromRequest(String requestLine) {
        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }

        // HTTP request line format: METHOD PATH HTTP_VERSION
        // Example: "GET /abcdefg HTTP/1.1"
        String[] parts = requestLine.split(" ");
        
        if (parts.length >= 1) {
            return parts[0]; // Return the method part
        }
        
        return null;
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