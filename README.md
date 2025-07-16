# HTTP Server in Java

A simple HTTP server implementation in Java that listens on port 4221 and handles basic HTTP requests.

## Prerequisites

- Java 21 or higher
- Maven 3.6 or higher

## Project Structure

```
├── pom.xml                 # Maven configuration file
├── README.md              # This file
├── src/
│   └── main/
│       └── java/
│           └── Main.java  # Main server implementation
└── target/                # Compiled classes and JAR files
```

## Building the Project

```bash
mvn clean compile
```

## Running the Server

After building the project with `mvn package`, run:

```bash
java -jar target/networking-http-server.jar
```

## Testing the Server

Once the server is running, you should see the message:
```
Logs from your program will appear here!
accepted new connection
```

The server will be listening on **port 4221**. You can test it using:

### Using curl
```bash
curl http://localhost:4221/
```

### Using a web browser
Open your browser and navigate to:
```
http://localhost:4221/
```

## Server Details

- **Port:** 4221
- **Protocol:** HTTP/1.1
- **Main Class:** `Main`
- **Socket Reuse:** Enabled (SO_REUSEADDR)

## Development

To make changes to the server:

1. Edit the `src/main/java/Main.java` file
2. Rebuild the project: `mvn clean package`
3. Run the updated server using one of the methods above

## Troubleshooting

### Port Already in Use
If you get an "Address already in use" error, either:
- Kill the existing process using port 4221
- Wait a few seconds and try again (the server sets SO_REUSEADDR)

### Java Version Issues
Make sure you're using Java 21 or higher:
```bash
java -version
```

### Maven Issues
Verify Maven is installed and configured:
```bash
mvn -version
```

## Notes

- This is a basic HTTP server implementation
- The server accepts one connection at a time
- Logs and debug information will appear in the console
- The server socket is configured to reuse addresses to avoid binding issues during development