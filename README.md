# HTTP Server with WebSocket Support

A multi-threaded HTTP server implementation in Java that supports concurrent connections, file serving, HTTP endpoints, and **real-time WebSocket communication** for bidirectional messaging.

## Tech Stack

- **Language:** Java 23
- **Build Tool:** Maven 3.6+
- **Architecture:** Multi-threaded server with concurrent connection handling
- **Networking:** Java Socket API (`java.net.ServerSocket`, `java.net.Socket`)
- **I/O:** Java NIO for file operations (`java.nio.file.*`)
- **HTTP Protocol:** HTTP/1.1 compliant responses
- **WebSocket Protocol:** RFC 6455 compliant WebSocket implementation
- **Cryptography:** SHA-1 hashing for WebSocket handshake, Base64 encoding

## Project Structure

```
├── pom.xml                 # Maven configuration file
├── README.md              # This file
├── files/                 # Directory for file serving (create if needed)
├── src/
│   └── main/
│       └── java/
│           └── Main.java  # Main server implementation
└── target/                # Compiled classes and JAR files
    └── networking-http-server.jar
```

## Supported Endpoints

### 1. Root Endpoint
- **Path:** `/`
- **Method:** GET
- **Response:** 200 OK
- **Example:** `curl http://localhost:4221/`

### 2. Echo Endpoint
- **Path:** `/echo/{string}`
- **Method:** GET
- **Response:** Returns the provided string with `Content-Type: text/plain`
- **Example:** `curl http://localhost:4221/echo/hello` → Returns "hello"

### 3. User Agent Endpoint
- **Path:** `/user-agent`
- **Method:** GET
- **Response:** Returns the User-Agent header value
- **Example:** `curl -H "User-Agent: MyBot/1.0" http://localhost:4221/user-agent` → Returns "MyBot/1.0"

### 4. File Serving Endpoints
- **Path:** `/files/{filename}`
- **Methods:** GET, POST
- **GET:** Serves existing files from the `files/` directory
  - **Content-Type:** `application/octet-stream`
  - **Example:** `curl http://localhost:4221/files/test.txt`
- **POST:** Creates new files with request body content
  - **Response:** 201 Created on success
  - **Example:** `curl -X POST --data "file content" http://localhost:4221/files/newfile.txt`

### 5. WebSocket Test Page
- **Path:** `/websocket-test`
- **Method:** GET
- **Response:** HTML page for testing WebSocket functionality
- **Example:** Visit `http://localhost:4221/websocket-test` in browser

## WebSocket Support

### WebSocket Endpoints
- **Path:** Any path starting with `/ws` (e.g., `/ws`, `/ws/chat`, `/ws/room1`)
- **Protocol:** WebSocket (RFC 6455 compliant)
- **Features:**
  - Real-time bidirectional communication
  - Message broadcasting to all connected clients
  - Ping/Pong heartbeat support
  - Proper connection lifecycle management

### WebSocket Frame Types Supported
- **Text Frames (0x1):** For sending/receiving text messages
- **Close Frames (0x8):** For graceful connection closure
- **Ping Frames (0x9):** For connection health checks
- **Pong Frames (0xA):** For responding to ping frames

### WebSocket Features
- **Automatic Broadcasting:** Messages sent by one client are broadcast to all other connected clients
- **Echo Functionality:** Server echoes back received messages with "Echo: " prefix
- **Welcome Messages:** New connections receive a welcome message with connection path
- **Connection Management:** Thread-safe connection list using `CopyOnWriteArrayList`

### Error Handling
- **404 Not Found:** For unknown paths or non-existent files
- **400 Bad Request:** For malformed requests

## Prerequisites

- Java 21 or higher
- Maven 3.6 or higher

## Key Features

- **Multi-threaded Architecture:** Handles multiple concurrent connections
- **HTTP/1.1 Compliance:** Proper HTTP response formatting with persistent connections
- **WebSocket Support:** RFC 6455 compliant real-time bidirectional communication
- **Persistent Connections:** Supports keep-alive connections for multiple requests
- **Gzip Compression:** Automatic compression when supported by clients
- **File Operations:** Serve existing files and create new ones
- **Header Parsing:** Extracts and processes HTTP headers (Accept-Encoding, User-Agent, etc.)
- **Request Body Handling:** Supports POST requests with content
- **Socket Reuse:** Configured for development with SO_REUSEADDR
- **Connection Management:** Graceful handling of both HTTP and WebSocket connections

## Building the Project

```bash
mvn clean package
```

## Running the Server

After building the project with `mvn package`, run:

```bash
java -jar target/networking-http-server.jar
```

You could also use the schell script:
```bash
./run.sh
```

## Testing the Server

Once the server is running, you should see:
```
Logs from your program will appear here!
accepted new connection
```

The server listens on **port 4221**. Test the endpoints:

### Basic Tests
```bash
# Root endpoint
curl http://localhost:4221/

# Echo endpoint
curl http://localhost:4221/echo/hello

# User-Agent endpoint
curl -H "User-Agent: TestClient/1.0" http://localhost:4221/user-agent
```

### File Operations
```bash
# Create a file with POST
curl -X POST --data "Hello, World!" http://localhost:4221/files/greeting.txt

# Retrieve the file with GET
curl http://localhost:4221/files/greeting.txt

# Test non-existent file (returns 404)
curl http://localhost:4221/files/nonexistent.txt
```

### Compression Testing
```bash
# Test gzip compression support
curl -H "Accept-Encoding: gzip" http://localhost:4221/echo/hello

# Test multiple encodings
curl -H "Accept-Encoding: invalid, gzip, deflate" http://localhost:4221/echo/test

# Verify compression with hexdump
curl -H "Accept-Encoding: gzip" http://localhost:4221/echo/abc | hexdump -C
```

### Persistent Connection Testing
```bash
# Test multiple requests on same connection
curl --http1.1 -v http://localhost:4221/echo/banana --next http://localhost:4221/user-agent -H "User-Agent: blueberry/apple-blueberry"

# Test connection reuse
curl --http1.1 -v http://localhost:4221/ --next http://localhost:4221/echo/persistent
```

### WebSocket Testing
```bash
# Test WebSocket in browser
# Visit: http://localhost:4221/websocket-test

# Test WebSocket upgrade with curl
curl -i -N \
  -H "Connection: Upgrade" \
  -H "Upgrade: websocket" \
  -H "Sec-WebSocket-Version: 13" \
  -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" \
  http://localhost:4221/ws

