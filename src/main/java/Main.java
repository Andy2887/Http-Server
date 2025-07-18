import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPOutputStream;

public class Main {
    private static final String WEBSOCKET_MAGIC_STRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final CopyOnWriteArrayList<WebSocketConnection> webSocketConnections = new CopyOnWriteArrayList<>();
    
    public static void main(String[] args) {
        System.out.println("HTTP Server with WebSocket support starting...");

        try {
            ServerSocket serverSocket = new ServerSocket(4221);
            serverSocket.setReuseAddress(true);

            // Continuously accept new connections
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted new connection from: " + clientSocket.getRemoteSocketAddress());

                // Handle each connection in a separate thread
                new Thread(() -> handleConnection(clientSocket)).start();
            }

        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    private static void handleConnection(Socket clientSocket) {
        try {
            clientSocket.setSoTimeout(30000);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            
            boolean keepAlive = true;
            int requestCount = 0;
            
            while (keepAlive) {
                String requestLine = in.readLine();
                
                if (requestLine == null || requestLine.isEmpty()) {
                    break;
                }
                
                requestCount++;
                System.out.println("Processing request #" + requestCount + ": " + requestLine);

                String method = parseMethodFromRequest(requestLine);
                String path = parsePathFromRequest(requestLine);

                // Read HTTP headers
                String userAgent = null;
                String acceptEncoding = null;
                String connectionHeader = null;
                String upgradeHeader = null;
                String webSocketKey = null;
                String webSocketVersion = null;
                int contentLength = 0;
                
                String headerLine;
                while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                    String lowerHeader = headerLine.toLowerCase();
                    if (lowerHeader.startsWith("user-agent:")) {
                        userAgent = headerLine.substring("user-agent:".length()).trim();
                    } else if (lowerHeader.startsWith("content-length:")) {
                        contentLength = Integer.parseInt(headerLine.substring("content-length:".length()).trim());
                    } else if (lowerHeader.startsWith("accept-encoding:")) {
                        acceptEncoding = headerLine.substring("accept-encoding:".length()).trim();
                    } else if (lowerHeader.startsWith("connection:")) {
                        connectionHeader = headerLine.substring("connection:".length()).trim();
                    } else if (lowerHeader.startsWith("upgrade:")) {
                        upgradeHeader = headerLine.substring("upgrade:".length()).trim();
                    } else if (lowerHeader.startsWith("sec-websocket-key:")) {
                        webSocketKey = headerLine.substring("sec-websocket-key:".length()).trim();
                    } else if (lowerHeader.startsWith("sec-websocket-version:")) {
                        webSocketVersion = headerLine.substring("sec-websocket-version:".length()).trim();
                    }
                }

                // Check if this is a WebSocket upgrade request
                if (isWebSocketUpgradeRequest(connectionHeader, upgradeHeader, webSocketKey, webSocketVersion)) {
                    handleWebSocketUpgrade(clientSocket, webSocketKey, path);
                    return; // WebSocket connection takes over
                }

                // Read request body if Content-Length is specified
                String requestBody = null;
                if (contentLength > 0) {
                    char[] buffer = new char[contentLength];
                    in.read(buffer, 0, contentLength);
                    requestBody = new String(buffer);
                }

                // Handle regular HTTP requests
                String connectionResponseHeader = "";
                if (connectionHeader != null && connectionHeader.toLowerCase().contains("close")) {
                    connectionResponseHeader = "Connection: close\r\n";
                }
                
                handleHttpRequest(clientSocket, method, path, userAgent, acceptEncoding, requestBody, connectionResponseHeader);
                
                if (connectionHeader != null && connectionHeader.toLowerCase().contains("close")) {
                    keepAlive = false;
                }
            }

            clientSocket.close();

        } catch (IOException e) {
            System.out.println("IOException in handleConnection: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Error closing socket: " + e.getMessage());
            }
        }
    }

    private static boolean isWebSocketUpgradeRequest(String connection, String upgrade, String webSocketKey, String webSocketVersion) {
        return connection != null && connection.toLowerCase().contains("upgrade") &&
               upgrade != null && upgrade.toLowerCase().contains("websocket") &&
               webSocketKey != null &&
               "13".equals(webSocketVersion);
    }

    private static void handleWebSocketUpgrade(Socket clientSocket, String webSocketKey, String path) {
        try {
            // Generate WebSocket accept key
            String acceptKey = generateWebSocketAcceptKey(webSocketKey);
            
            // Send WebSocket handshake response
            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: " + acceptKey + "\r\n" +
                            "\r\n";
            
            OutputStream out = clientSocket.getOutputStream();
            out.write(response.getBytes());
            out.flush();
            
            System.out.println("WebSocket handshake completed for path: " + path);
            
            // Create WebSocket connection and handle it
            WebSocketConnection wsConnection = new WebSocketConnection(clientSocket, path);
            webSocketConnections.add(wsConnection);
            
            // Handle WebSocket communication
            handleWebSocketCommunication(wsConnection);
            
        } catch (Exception e) {
            System.out.println("Error in WebSocket upgrade: " + e.getMessage());
        }
    }

    private static String generateWebSocketAcceptKey(String webSocketKey) throws NoSuchAlgorithmException {
        String concatenated = webSocketKey + WEBSOCKET_MAGIC_STRING;
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(concatenated.getBytes());
        return Base64.getEncoder().encodeToString(hash);
    }

    private static void handleWebSocketCommunication(WebSocketConnection wsConnection) {
        try {
            InputStream in = wsConnection.getSocket().getInputStream();
            
            // Send welcome message
            wsConnection.sendMessage("Welcome to WebSocket server! Path: " + wsConnection.getPath());
            
            while (!wsConnection.getSocket().isClosed()) {
                // Read WebSocket frame
                WebSocketFrame frame = readWebSocketFrame(in);
                if (frame == null) break;
                
                switch (frame.getOpcode()) {
                    case 0x1: // Text frame
                        String message = new String(frame.getPayload());
                        System.out.println("Received WebSocket message: " + message);
                        
                        // Echo the message back
                        wsConnection.sendMessage("Echo: " + message);
                        
                        // Broadcast to other connections
                        broadcastMessage("Broadcast: " + message, wsConnection);
                        break;
                        
                    case 0x8: // Close frame
                        System.out.println("WebSocket close frame received");
                        wsConnection.close();
                        return;
                        
                    case 0x9: // Ping frame
                        wsConnection.sendPong(frame.getPayload());
                        break;
                        
                    case 0xA: // Pong frame
                        System.out.println("Received pong frame");
                        break;
                }
            }
            
        } catch (IOException e) {
            System.out.println("WebSocket communication error: " + e.getMessage());
        } finally {
            webSocketConnections.remove(wsConnection);
            try {
                wsConnection.close();
            } catch (IOException e) {
                System.out.println("Error closing WebSocket: " + e.getMessage());
            }
        }
    }

    private static void broadcastMessage(String message, WebSocketConnection sender) {
        for (WebSocketConnection conn : webSocketConnections) {
            if (conn != sender && !conn.getSocket().isClosed()) {
                try {
                    conn.sendMessage(message);
                } catch (IOException e) {
                    System.out.println("Error broadcasting message: " + e.getMessage());
                }
            }
        }
    }

    private static WebSocketFrame readWebSocketFrame(InputStream in) throws IOException {
        int firstByte = in.read();
        if (firstByte == -1) return null;
        
        boolean fin = (firstByte & 0x80) != 0;
        int opcode = firstByte & 0x0F;
        
        int secondByte = in.read();
        if (secondByte == -1) return null;
        
        boolean masked = (secondByte & 0x80) != 0;
        int payloadLength = secondByte & 0x7F;
        
        // Handle extended payload length
        if (payloadLength == 126) {
            payloadLength = (in.read() << 8) | in.read();
        } else if (payloadLength == 127) {
            // For simplicity, we'll limit to int range
            for (int i = 0; i < 4; i++) in.read(); // Skip first 4 bytes
            payloadLength = (in.read() << 24) | (in.read() << 16) | (in.read() << 8) | in.read();
        }
        
        // Read masking key if present
        byte[] maskingKey = null;
        if (masked) {
            maskingKey = new byte[4];
            in.read(maskingKey);
        }
        
        // Read payload
        byte[] payload = new byte[payloadLength];
        in.read(payload);
        
        // Unmask payload if necessary
        if (masked && maskingKey != null) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= maskingKey[i % 4];
            }
        }
        
        return new WebSocketFrame(fin, opcode, payload);
    }

    private static void handleHttpRequest(Socket clientSocket, String method, String path, String userAgent, 
                                        String acceptEncoding, String requestBody, String connectionResponseHeader) throws IOException {
        if ("/".equals(path)) {
            sendTextResponse(clientSocket, "HTTP/1.1 200 OK\r\n" + connectionResponseHeader + "\r\n");
        } else if (path != null && path.startsWith("/echo/")) {
            String echoString = path.substring("/echo/".length());
            handleEchoRequest(clientSocket, echoString, acceptEncoding, connectionResponseHeader);
        } else if ("/user-agent".equals(path)) {
            if (userAgent != null) {
                int agentLength = userAgent.length();
                String response = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/plain\r\n" +
                                connectionResponseHeader +
                                "Content-Length: " + agentLength + "\r\n" +
                                "\r\n" +
                                userAgent;
                sendTextResponse(clientSocket, response);
            } else {
                sendTextResponse(clientSocket, "HTTP/1.1 400 Bad Request\r\n" + connectionResponseHeader + "\r\n");
            }
        } else if (path != null && path.startsWith("/files/")) {
            String filename = path.substring("/files/".length());
            String response = handleFileRequest(method, filename, requestBody, connectionResponseHeader);
            sendTextResponse(clientSocket, response);
        } else if ("/websocket-test".equals(path)) {
            // Serve a simple WebSocket test page
            String html = getWebSocketTestPage();
            String response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html\r\n" +
                            "Content-Length: " + html.length() + "\r\n" +
                            connectionResponseHeader +
                            "\r\n" +
                            html;
            sendTextResponse(clientSocket, response);
        } else {
            sendTextResponse(clientSocket, "HTTP/1.1 404 Not Found\r\n" + connectionResponseHeader + "\r\n");
        }
    }

    private static String getWebSocketTestPage() {
        return "<!DOCTYPE html>\n" +
               "<html>\n" +
               "<head><title>WebSocket Test</title></head>\n" +
               "<body>\n" +
               "<h1>WebSocket Test</h1>\n" +
               "<div id=\"messages\"></div>\n" +
               "<input type=\"text\" id=\"messageInput\" placeholder=\"Type a message...\">\n" +
               "<button onclick=\"sendMessage()\">Send</button>\n" +
               "<script>\n" +
               "const ws = new WebSocket('ws://localhost:4221/ws');\n" +
               "const messages = document.getElementById('messages');\n" +
               "ws.onmessage = function(event) {\n" +
               "  const div = document.createElement('div');\n" +
               "  div.textContent = event.data;\n" +
               "  messages.appendChild(div);\n" +
               "};\n" +
               "function sendMessage() {\n" +
               "  const input = document.getElementById('messageInput');\n" +
               "  ws.send(input.value);\n" +
               "  input.value = '';\n" +
               "}\n" +
               "document.getElementById('messageInput').addEventListener('keypress', function(e) {\n" +
               "  if (e.key === 'Enter') sendMessage();\n" +
               "});\n" +
               "</script>\n" +
               "</body>\n" +
               "</html>";
    }

    // ... (keeping all existing HTTP methods: sendTextResponse, handleEchoRequest, compressWithGzip, 
    //      handleFileRequest, supportsGzipEncoding, parseMethodFromRequest, parsePathFromRequest)

    // WebSocket helper classes
    static class WebSocketConnection {
        private final Socket socket;
        private final String path;
        private final OutputStream out;

        public WebSocketConnection(Socket socket, String path) throws IOException {
            this.socket = socket;
            this.path = path;
            this.out = socket.getOutputStream();
        }

        public void sendMessage(String message) throws IOException {
            byte[] payload = message.getBytes();
            sendFrame(0x1, payload); // Text frame
        }

        public void sendPong(byte[] payload) throws IOException {
            sendFrame(0xA, payload); // Pong frame
        }

        private void sendFrame(int opcode, byte[] payload) throws IOException {
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            
            // First byte: FIN (1) + RSV (000) + opcode (4 bits)
            frame.write(0x80 | opcode);
            
            // Second byte: MASK (0) + payload length
            if (payload.length < 126) {
                frame.write(payload.length);
            } else if (payload.length < 65536) {
                frame.write(126);
                frame.write((payload.length >> 8) & 0xFF);
                frame.write(payload.length & 0xFF);
            } else {
                frame.write(127);
                // Write 8-byte length (simplified for demo)
                for (int i = 7; i >= 0; i--) {
                    frame.write((payload.length >> (i * 8)) & 0xFF);
                }
            }
            
            frame.write(payload);
            
            out.write(frame.toByteArray());
            out.flush();
        }

        public Socket getSocket() { return socket; }
        public String getPath() { return path; }
        public void close() throws IOException { socket.close(); }
    }

    static class WebSocketFrame {
        private final boolean fin;
        private final int opcode;
        private final byte[] payload;

        public WebSocketFrame(boolean fin, int opcode, byte[] payload) {
            this.fin = fin;
            this.opcode = opcode;
            this.payload = payload;
        }

        public boolean isFin() { return fin; }
        public int getOpcode() { return opcode; }
        public byte[] getPayload() { return payload; }
    }

    // Keep all existing HTTP methods here...
    private static void sendTextResponse(Socket clientSocket, String response) throws IOException {
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        out.print(response);
        out.flush();
        System.out.println("Response sent to client: " + response.trim());
    }

    private static void handleEchoRequest(Socket clientSocket, String echoString, String acceptEncoding, String connectionResponseHeader) throws IOException {
        boolean supportsGzip = supportsGzipEncoding(acceptEncoding);
        
        if (supportsGzip) {
            byte[] compressedData = compressWithGzip(echoString);
            String headers = "HTTP/1.1 200 OK\r\n" +
                           "Content-Type: text/plain\r\n" +
                           "Content-Encoding: gzip\r\n" +
                           connectionResponseHeader +
                           "Content-Length: " + compressedData.length + "\r\n" +
                           "\r\n";
            
            clientSocket.getOutputStream().write(headers.getBytes());
            clientSocket.getOutputStream().write(compressedData);
            clientSocket.getOutputStream().flush();
        } else {
            int echoLength = echoString.length();
            String response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain\r\n" +
                            connectionResponseHeader +
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

    private static String handleFileRequest(String method, String filename, String requestBody, String connectionResponseHeader) {
        try {
            Path filePath = Paths.get("files", filename);
            
            if ("GET".equals(method)) {
                if (!Files.exists(filePath)) {
                    return "HTTP/1.1 404 Not Found\r\n" + connectionResponseHeader + "\r\n";
                }
                
                byte[] fileContent = Files.readAllBytes(filePath);
                String content = new String(fileContent);
                
                return "HTTP/1.1 200 OK\r\n" +
                       "Content-Type: application/octet-stream\r\n" +
                       connectionResponseHeader +
                       "Content-Length: " + fileContent.length + "\r\n" +
                       "\r\n" +
                       content;
                       
            } else if ("POST".equals(method)) {
                if (requestBody == null) {
                    return "HTTP/1.1 400 Bad Request\r\n" + connectionResponseHeader + "\r\n";
                }
                
                Path filesDir = Paths.get("files");
                if (!Files.exists(filesDir)) {
                    Files.createDirectories(filesDir);
                }
                
                Files.write(filePath, requestBody.getBytes());
                return "HTTP/1.1 201 Created\r\n" + connectionResponseHeader + "\r\n";
                
            } else {
                return "HTTP/1.1 405 Method Not Allowed\r\n" + connectionResponseHeader + "\r\n";
            }
                   
        } catch (IOException e) {
            System.out.println("Error handling file request: " + e.getMessage());
            return "HTTP/1.1 500 Internal Server Error\r\n" + connectionResponseHeader + "\r\n";
        }
    }

    private static boolean supportsGzipEncoding(String acceptEncoding) {
        if (acceptEncoding == null || acceptEncoding.isEmpty()) {
            return false;
        }
        
        String[] encodings = acceptEncoding.split(",");
        for (String encoding : encodings) {
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

        String[] parts = requestLine.split(" ");
        
        if (parts.length >= 1) {
            return parts[0];
        }
        
        return null;
    }

    private static String parsePathFromRequest(String requestLine) {
        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }

        String[] parts = requestLine.split(" ");
        
        if (parts.length >= 2) {
            return parts[1];
        }
        
        return null;
    }
}