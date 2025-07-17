# HTTP Server in Java

A multi-threaded HTTP server implementation in Java that supports concurrent connections, file serving, and various HTTP endpoints.

## Tech Stack

- **Language:** Java 21
- **Build Tool:** Maven 3.6+
- **Architecture:** Multi-threaded server with concurrent connection handling
- **Networking:** Java Socket API (`java.net.ServerSocket`, `java.net.Socket`)
- **I/O:** Java NIO for file operations (`java.nio.file.*`)
- **HTTP Protocol:** HTTP/1.1 compliant responses

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

### Error Handling
- **404 Not Found:** For unknown paths or non-existent files
- **400 Bad Request:** For malformed requests

## Prerequisites

- Java 21 or higher
- Maven 3.6 or higher

## Key Features

- **Multi-threaded Architecture:** Handles multiple concurrent connections
- **HTTP/1.1 Compliance:** Proper HTTP response formatting with persistent connections
- **Persistent Connections:** Supports keep-alive connections for multiple requests
- **Gzip Compression:** Automatic compression when supported by clients
- **File Operations:** Serve existing files and create new ones
- **Header Parsing:** Extracts and processes HTTP headers (Accept-Encoding, User-Agent, etc.)
- **Request Body Handling:** Supports POST requests with content
- **Socket Reuse:** Configured for development with SO_REUSEADDR

## Building the Project

```bash
mvn clean package
```

## Running the Server

After building the project with `mvn package`, run:

```bash
java -jar target/networking-http-server.jar
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

## Server Details

- **Port:** 4221
- **Protocol:** HTTP/1.1
- **Main Class:** `Main`
- **Socket Reuse:** Enabled (SO_REUSEADDR)
- **Threading:** Each connection handled in separate thread
- **File Directory:** `files/` (relative to project root)

## Implementation Details

### Request Processing Flow
1. **Connection Acceptance:** Server accepts incoming connections on port 4221
2. **Threading:** Each connection is handled in a separate thread for concurrency
3. **Request Parsing:** Extracts HTTP method, path, and headers from the request
4. **Routing:** Matches request path to appropriate handler
5. **Response Generation:** Creates HTTP-compliant response with proper headers
6. **Connection Cleanup:** Closes client socket after response

### HTTP Headers Supported
- **Content-Type:** Set to `text/plain` for text responses, `application/octet-stream` for files
- **Content-Length:** Always included with the exact byte count
- **Content-Encoding:** Set to `gzip` when compression is applied
- **Connection:** Handles keep-alive for persistent connections
- **Accept-Encoding:** Parsed to determine compression support
- **User-Agent:** Parsed from client requests for the `/user-agent` endpoint

### File Operations
- **File Storage:** All files are stored in the `files/` directory
- **File Reading:** Uses Java NIO for efficient file I/O
- **File Creation:** POST requests create new files with request body content
- **Error Handling:** Returns 404 for non-existent files, 201 for successful creation