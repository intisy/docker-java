package io.github.intisy.docker.transport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * HTTP client for communicating with Docker daemon over Unix sockets or named pipes.
 *
 * @author Finn Birich
 */
public class DockerHttpClient implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(DockerHttpClient.class);
    
    private static final String API_VERSION = "v1.44";
    private static final int DEFAULT_TIMEOUT = 30000;

    private final String dockerHost;
    private final Gson gson;
    private final int timeout;
    private Socket currentSocket;

    public DockerHttpClient(String dockerHost) {
        this(dockerHost, DEFAULT_TIMEOUT);
    }

    public DockerHttpClient(String dockerHost, int timeoutMs) {
        this.dockerHost = dockerHost;
        this.timeout = timeoutMs;
        this.gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'")
                .create();
        log.debug("Created DockerHttpClient for host: {}", dockerHost);
    }

    public Gson getGson() {
        return gson;
    }

    public DockerResponse get(String path) throws IOException {
        return request("GET", path, null);
    }

    public DockerResponse get(String path, Map<String, String> queryParams) throws IOException {
        String fullPath = buildPathWithQuery(path, queryParams);
        return request("GET", fullPath, null);
    }

    public DockerResponse post(String path, Object body) throws IOException {
        String jsonBody = body != null ? gson.toJson(body) : null;
        return request("POST", path, jsonBody);
    }

    public DockerResponse post(String path) throws IOException {
        return request("POST", path, null);
    }

    public DockerResponse post(String path, Map<String, String> queryParams, Object body) throws IOException {
        String fullPath = buildPathWithQuery(path, queryParams);
        String jsonBody = body != null ? gson.toJson(body) : null;
        return request("POST", fullPath, jsonBody);
    }

    public DockerResponse delete(String path) throws IOException {
        return request("DELETE", path, null);
    }

    public DockerResponse delete(String path, Map<String, String> queryParams) throws IOException {
        String fullPath = buildPathWithQuery(path, queryParams);
        return request("DELETE", fullPath, null);
    }

    public void postStream(String path, Map<String, String> queryParams, StreamCallback<String> callback) throws IOException {
        String fullPath = buildPathWithQuery(path, queryParams);
        requestStream("POST", fullPath, null, callback);
    }

    public void getStream(String path, Map<String, String> queryParams, StreamCallback<String> callback) throws IOException {
        String fullPath = buildPathWithQuery(path, queryParams);
        requestStream("GET", fullPath, null, callback);
    }

    private String buildPathWithQuery(String path, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return path;
        }
        StringBuilder sb = new StringBuilder(path);
        sb.append("?");
        boolean first = true;
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            sb.append(urlEncode(entry.getKey())).append("=").append(urlEncode(entry.getValue()));
            first = false;
        }
        return sb.toString();
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private DockerResponse request(String method, String path, String body) throws IOException {
        log.trace("{} {}", method, path);
        if (dockerHost.startsWith("unix://")) {
            return unixSocketRequest(method, path, body);
        } else if (dockerHost.startsWith("npipe://")) {
            return namedPipeRequest(method, path, body);
        } else if (dockerHost.startsWith("tcp://") || dockerHost.startsWith("http://")) {
            return tcpRequest(method, path, body);
        } else {
            throw new IOException("Unsupported Docker host: " + dockerHost);
        }
    }

    private void requestStream(String method, String path, String body, StreamCallback<String> callback) throws IOException {
        log.trace("{} {} (streaming)", method, path);
        if (dockerHost.startsWith("unix://")) {
            unixSocketRequestStream(method, path, body, callback);
        } else if (dockerHost.startsWith("npipe://")) {
            namedPipeRequestStream(method, path, body, callback);
        } else if (dockerHost.startsWith("tcp://") || dockerHost.startsWith("http://")) {
            tcpRequestStream(method, path, body, callback);
        } else {
            throw new IOException("Unsupported Docker host: " + dockerHost);
        }
    }

    private DockerResponse unixSocketRequest(String method, String path, String body) throws IOException {
        String socketPath = dockerHost.substring(7);
        
        File socketFile = new File(socketPath);
        AFUNIXSocketAddress address = AFUNIXSocketAddress.of(socketFile);
        
        try (AFUNIXSocket socket = AFUNIXSocket.newInstance()) {
            socket.connect(address);
            socket.setSoTimeout(timeout);
            
            return sendHttpRequest(socket, method, path, body);
        }
    }

    private void unixSocketRequestStream(String method, String path, String body, StreamCallback<String> callback) throws IOException {
        String socketPath = dockerHost.substring(7);
        
        File socketFile = new File(socketPath);
        AFUNIXSocketAddress address = AFUNIXSocketAddress.of(socketFile);
        
        AFUNIXSocket socket = AFUNIXSocket.newInstance();
        try {
            socket.connect(address);
            socket.setSoTimeout(0);
            this.currentSocket = socket;
            
            sendHttpRequestStream(socket, method, path, body, callback);
        } finally {
            this.currentSocket = null;
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private DockerResponse namedPipeRequest(String method, String path, String body) throws IOException {
        String pipePath = dockerHost.substring(8);
        pipePath = pipePath.replace("/", "\\");
        if (!pipePath.startsWith("\\\\.\\pipe\\")) {
            pipePath = "\\\\.\\pipe\\" + pipePath;
        }
        
        try (RandomAccessFile pipe = new RandomAccessFile(pipePath, "rw")) {
            return sendHttpRequestViaPipe(pipe, method, path, body);
        }
    }

    private void namedPipeRequestStream(String method, String path, String body, StreamCallback<String> callback) throws IOException {
        String pipePath = dockerHost.substring(8);
        pipePath = pipePath.replace("/", "\\");
        if (!pipePath.startsWith("\\\\.\\pipe\\")) {
            pipePath = "\\\\.\\pipe\\" + pipePath;
        }
        
        try (RandomAccessFile pipe = new RandomAccessFile(pipePath, "rw")) {
            sendHttpRequestStreamViaPipe(pipe, method, path, body, callback);
        }
    }

    private DockerResponse tcpRequest(String method, String path, String body) throws IOException {
        String host = dockerHost.replace("tcp://", "http://").replace("http://", "");
        URL url = new URL("http://" + host + "/" + API_VERSION + path);
        log.debug("TCP request: {} {} (timeout: {}ms)", method, url, timeout);
        
        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setRequestProperty("Host", "docker");
            conn.setRequestProperty("Content-Type", "application/json");
            
            if (body != null) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            
            int statusCode = conn.getResponseCode();
            log.debug("TCP response status: {}", statusCode);
            Map<String, List<String>> headers = conn.getHeaderFields();
            
            String responseBody;
            try (InputStream is = statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
                if (is != null) {
                    responseBody = readStream(is);
                } else {
                    responseBody = "";
                }
            }
            
            return new DockerResponse(statusCode, headers, responseBody);
        } catch (IOException e) {
            log.debug("TCP request failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    private void tcpRequestStream(String method, String path, String body, StreamCallback<String> callback) throws IOException {
        String host = dockerHost.replace("tcp://", "http://").replace("http://", "");
        URL url = new URL("http://" + host + "/" + API_VERSION + path);
        
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(0);
        conn.setRequestProperty("Host", "docker");
        conn.setRequestProperty("Content-Type", "application/json");
        
        if (body != null) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        
        int statusCode = conn.getResponseCode();
        if (statusCode >= 400) {
            String errorBody;
            try (InputStream is = conn.getErrorStream()) {
                errorBody = is != null ? readStream(is) : "";
            }
            callback.onError(new IOException("HTTP " + statusCode + ": " + errorBody));
            return;
        }
        
        try (InputStream is = conn.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && !callback.isCancelled()) {
                if (!line.isEmpty()) {
                    callback.onNext(line);
                }
            }
            callback.onComplete();
        } catch (IOException e) {
            callback.onError(e);
        }
    }

    private DockerResponse sendHttpRequest(Socket socket, String method, String path, String body) throws IOException {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        
        StringBuilder request = new StringBuilder();
        request.append(method).append(" /").append(API_VERSION).append(path).append(" HTTP/1.1\r\n");
        request.append("Host: docker\r\n");
        request.append("Content-Type: application/json\r\n");
        request.append("Connection: close\r\n");
        
        byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (bodyBytes.length > 0) {
            request.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
        }
        request.append("\r\n");
        
        out.write(request.toString().getBytes(StandardCharsets.UTF_8));
        if (bodyBytes.length > 0) {
            out.write(bodyBytes);
        }
        out.flush();
        
        return parseHttpResponse(in);
    }

    private void sendHttpRequestStream(Socket socket, String method, String path, String body, StreamCallback<String> callback) throws IOException {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        
        StringBuilder request = new StringBuilder();
        request.append(method).append(" /").append(API_VERSION).append(path).append(" HTTP/1.1\r\n");
        request.append("Host: docker\r\n");
        request.append("Content-Type: application/json\r\n");
        request.append("Connection: close\r\n");
        
        byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (bodyBytes.length > 0) {
            request.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
        }
        request.append("\r\n");
        
        out.write(request.toString().getBytes(StandardCharsets.UTF_8));
        if (bodyBytes.length > 0) {
            out.write(bodyBytes);
        }
        out.flush();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String statusLine = reader.readLine();
        if (statusLine == null) {
            callback.onError(new IOException("No response from server"));
            return;
        }
        
        int statusCode = parseStatusCode(statusLine);
        
        Map<String, List<String>> headers = new HashMap<>();
        boolean isChunked = false;
        String headerLine;
        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
            int colonIndex = headerLine.indexOf(':');
            if (colonIndex > 0) {
                String name = headerLine.substring(0, colonIndex).trim();
                String value = headerLine.substring(colonIndex + 1).trim();
                headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
                if (name.equalsIgnoreCase("Transfer-Encoding") && value.equalsIgnoreCase("chunked")) {
                    isChunked = true;
                }
            }
        }
        
        if (statusCode >= 400) {
            StringBuilder errorBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                errorBody.append(line);
            }
            callback.onError(new IOException("HTTP " + statusCode + ": " + errorBody.toString()));
            return;
        }
        
        try {
            if (isChunked) {
                readChunkedStream(reader, callback);
            } else {
                String line;
                while ((line = reader.readLine()) != null && !callback.isCancelled()) {
                    if (!line.isEmpty()) {
                        callback.onNext(line);
                    }
                }
            }
            callback.onComplete();
        } catch (IOException e) {
            callback.onError(e);
        }
    }
    
    /**
     * Read a chunked transfer encoding stream and pass JSON objects to the callback.
     */
    private void readChunkedStream(BufferedReader reader, StreamCallback<String> callback) throws IOException {
        while (!callback.isCancelled()) {
            String chunkSizeLine = reader.readLine();
            if (chunkSizeLine == null) {
                break;
            }
            
            String sizeStr = chunkSizeLine.trim();
            if (sizeStr.isEmpty()) {
                continue;
            }
            
            int semicolonIndex = sizeStr.indexOf(';');
            if (semicolonIndex > 0) {
                sizeStr = sizeStr.substring(0, semicolonIndex);
            }
            
            int chunkSize;
            try {
                chunkSize = Integer.parseInt(sizeStr, 16);
            } catch (NumberFormatException e) {
                if (!chunkSizeLine.isEmpty()) {
                    callback.onNext(chunkSizeLine);
                }
                continue;
            }
            
            if (chunkSize == 0) {
                break;
            }
            
            char[] chunkData = new char[chunkSize];
            int totalRead = 0;
            while (totalRead < chunkSize) {
                int read = reader.read(chunkData, totalRead, chunkSize - totalRead);
                if (read == -1) break;
                totalRead += read;
            }
            
            String data = new String(chunkData, 0, totalRead).trim();
            if (!data.isEmpty()) {
                for (String line : data.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        callback.onNext(trimmed);
                    }
                }
            }
            
            reader.readLine();
        }
    }

    private DockerResponse sendHttpRequestViaPipe(RandomAccessFile pipe, String method, String path, String body) throws IOException {
        StringBuilder request = new StringBuilder();
        request.append(method).append(" /").append(API_VERSION).append(path).append(" HTTP/1.1\r\n");
        request.append("Host: docker\r\n");
        request.append("Content-Type: application/json\r\n");
        request.append("Connection: close\r\n");
        
        byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (bodyBytes.length > 0) {
            request.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
        }
        request.append("\r\n");
        
        pipe.write(request.toString().getBytes(StandardCharsets.UTF_8));
        if (bodyBytes.length > 0) {
            pipe.write(bodyBytes);
        }
        
        return parseHttpResponseFromPipe(pipe);
    }

    private void sendHttpRequestStreamViaPipe(RandomAccessFile pipe, String method, String path, String body, StreamCallback<String> callback) throws IOException {
        StringBuilder request = new StringBuilder();
        request.append(method).append(" /").append(API_VERSION).append(path).append(" HTTP/1.1\r\n");
        request.append("Host: docker\r\n");
        request.append("Content-Type: application/json\r\n");
        
        byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (bodyBytes.length > 0) {
            request.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
        }
        request.append("\r\n");
        
        pipe.write(request.toString().getBytes(StandardCharsets.UTF_8));
        if (bodyBytes.length > 0) {
            pipe.write(bodyBytes);
        }
        
        StringBuilder headerBuilder = new StringBuilder();
        int ch;
        int newlineCount = 0;
        while ((ch = pipe.read()) != -1) {
            headerBuilder.append((char) ch);
            if (ch == '\n') {
                newlineCount++;
                if (newlineCount >= 2 || headerBuilder.toString().endsWith("\r\n\r\n")) {
                    break;
                }
            } else if (ch != '\r') {
                newlineCount = 0;
            }
        }
        
        String headerStr = headerBuilder.toString();
        String[] headerLines = headerStr.split("\r\n");
        if (headerLines.length == 0) {
            callback.onError(new IOException("No response from server"));
            return;
        }
        
        int statusCode = parseStatusCode(headerLines[0]);
        if (statusCode >= 400) {
            StringBuilder errorBody = new StringBuilder();
            while ((ch = pipe.read()) != -1) {
                errorBody.append((char) ch);
            }
            callback.onError(new IOException("HTTP " + statusCode + ": " + errorBody.toString()));
            return;
        }
        
        try {
            StringBuilder lineBuilder = new StringBuilder();
            while ((ch = pipe.read()) != -1 && !callback.isCancelled()) {
                if (ch == '\n') {
                    String line = lineBuilder.toString().trim();
                    if (!line.isEmpty()) {
                        callback.onNext(line);
                    }
                    lineBuilder.setLength(0);
                } else if (ch != '\r') {
                    lineBuilder.append((char) ch);
                }
            }
            callback.onComplete();
        } catch (IOException e) {
            callback.onError(e);
        }
    }

    private DockerResponse parseHttpResponse(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        
        String statusLine = reader.readLine();
        if (statusLine == null) {
            throw new IOException("No response from server");
        }
        int statusCode = parseStatusCode(statusLine);
        log.trace("Response status: {}", statusCode);
        
        Map<String, List<String>> headers = new HashMap<>();
        boolean isChunked = false;
        int contentLength = -1;
        String headerLine;
        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
            int colonIndex = headerLine.indexOf(':');
            if (colonIndex > 0) {
                String name = headerLine.substring(0, colonIndex).trim();
                String value = headerLine.substring(colonIndex + 1).trim();
                headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
                
                if (name.equalsIgnoreCase("Transfer-Encoding") && value.equalsIgnoreCase("chunked")) {
                    isChunked = true;
                }
                if (name.equalsIgnoreCase("Content-Length")) {
                    try {
                        contentLength = Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        log.trace("Response isChunked={}, contentLength={}", isChunked, contentLength);
        
        String body;
        if (isChunked) {
            log.trace("Reading chunked body...");
            body = readChunkedBody(reader);
            log.trace("Chunked body read complete, length={}", body.length());
        } else if (contentLength > 0) {
            log.trace("Reading fixed-length body, contentLength={}", contentLength);
            char[] buffer = new char[contentLength];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int read = reader.read(buffer, totalRead, contentLength - totalRead);
                if (read == -1) break;
                totalRead += read;
            }
            body = new String(buffer, 0, totalRead);
            log.trace("Fixed-length body read complete");
        } else {
            log.trace("Reading body until EOF...");
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            body = sb.toString();
            log.trace("EOF body read complete, length={}", body.length());
        }
        
        return new DockerResponse(statusCode, headers, body);
    }
    
    /**
     * Read a chunked transfer encoding body.
     * Format: &lt;size-in-hex&gt;\r\n&lt;chunk-data&gt;\r\n... 0\r\n\r\n
     */
    private String readChunkedBody(BufferedReader reader) throws IOException {
        StringBuilder body = new StringBuilder();
        
        while (true) {
            String chunkSizeLine = reader.readLine();
            if (chunkSizeLine == null || chunkSizeLine.isEmpty()) {
                break;
            }
            
            String sizeStr = chunkSizeLine.trim();
            if (sizeStr.isEmpty()) {
                continue;
            }
            
            int semicolonIndex = sizeStr.indexOf(';');
            if (semicolonIndex > 0) {
                sizeStr = sizeStr.substring(0, semicolonIndex);
            }
            
            int chunkSize;
            try {
                chunkSize = Integer.parseInt(sizeStr, 16);
            } catch (NumberFormatException e) {
                break;
            }
            
            if (chunkSize == 0) {
                break;
            }
            
            char[] chunkData = new char[chunkSize];
            int totalRead = 0;
            while (totalRead < chunkSize) {
                int read = reader.read(chunkData, totalRead, chunkSize - totalRead);
                if (read == -1) break;
                totalRead += read;
            }
            body.append(chunkData, 0, totalRead);
            
            reader.readLine();
        }
        
        return body.toString();
    }

    private DockerResponse parseHttpResponseFromPipe(RandomAccessFile pipe) throws IOException {
        StringBuilder responseBuilder = new StringBuilder();
        int ch;
        while ((ch = pipe.read()) != -1) {
            responseBuilder.append((char) ch);
            String current = responseBuilder.toString();
            if (current.contains("\r\n\r\n")) {
                break;
            }
        }
        
        String headerPart = responseBuilder.toString();
        String[] parts = headerPart.split("\r\n\r\n", 2);
        String[] headerLines = parts[0].split("\r\n");
        
        if (headerLines.length == 0) {
            throw new IOException("No response from server");
        }
        
        int statusCode = parseStatusCode(headerLines[0]);
        
        Map<String, List<String>> headers = new HashMap<>();
        int contentLength = -1;
        boolean isChunked = false;
        for (int i = 1; i < headerLines.length; i++) {
            String headerLine = headerLines[i];
            int colonIndex = headerLine.indexOf(':');
            if (colonIndex > 0) {
                String name = headerLine.substring(0, colonIndex).trim();
                String value = headerLine.substring(colonIndex + 1).trim();
                headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
                if (name.equalsIgnoreCase("Content-Length")) {
                    try {
                        contentLength = Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {}
                }
                if (name.equalsIgnoreCase("Transfer-Encoding") && value.equalsIgnoreCase("chunked")) {
                    isChunked = true;
                }
            }
        }
        
        String body;
        String initialBody = parts.length > 1 ? parts[1] : "";
        
        if (isChunked) {
            body = readChunkedBodyFromPipe(pipe, initialBody);
        } else if (contentLength > 0) {
            StringBuilder sb = new StringBuilder(initialBody);
            int remaining = contentLength - initialBody.length();
            for (int i = 0; i < remaining; i++) {
                int c = pipe.read();
                if (c == -1) break;
                sb.append((char) c);
            }
            body = sb.toString();
        } else {
            StringBuilder sb = new StringBuilder(initialBody);
            while ((ch = pipe.read()) != -1) {
                sb.append((char) ch);
            }
            body = sb.toString();
        }
        
        return new DockerResponse(statusCode, headers, body);
    }
    
    /**
     * Read a chunked transfer encoding body from a pipe.
     */
    private String readChunkedBodyFromPipe(RandomAccessFile pipe, String initialData) throws IOException {
        StringBuilder body = new StringBuilder();
        StringBuilder lineBuilder = new StringBuilder(initialData);
        
        while (true) {
            while (!lineBuilder.toString().contains("\n")) {
                int c = pipe.read();
                if (c == -1) {
                    return body.toString();
                }
                lineBuilder.append((char) c);
            }
            
            String data = lineBuilder.toString();
            int newlineIndex = data.indexOf('\n');
            String chunkSizeLine = data.substring(0, newlineIndex).trim();
            lineBuilder = new StringBuilder(data.substring(newlineIndex + 1));
            
            int semicolonIndex = chunkSizeLine.indexOf(';');
            if (semicolonIndex > 0) {
                chunkSizeLine = chunkSizeLine.substring(0, semicolonIndex);
            }
            
            int chunkSize;
            try {
                chunkSize = Integer.parseInt(chunkSizeLine.trim(), 16);
            } catch (NumberFormatException e) {
                break;
            }
            
            if (chunkSize == 0) {
                break;
            }
            
            while (lineBuilder.length() < chunkSize) {
                int c = pipe.read();
                if (c == -1) break;
                lineBuilder.append((char) c);
            }
            
            body.append(lineBuilder.substring(0, Math.min(chunkSize, lineBuilder.length())));
            lineBuilder = new StringBuilder(lineBuilder.length() > chunkSize ? lineBuilder.substring(chunkSize) : "");
            
            while (lineBuilder.length() < 2) {
                int c = pipe.read();
                if (c == -1) break;
                lineBuilder.append((char) c);
            }
            if (lineBuilder.length() >= 2) {
                lineBuilder = new StringBuilder(lineBuilder.substring(lineBuilder.toString().startsWith("\r\n") ? 2 : (lineBuilder.toString().startsWith("\n") ? 1 : 0)));
            }
        }
        
        return body.toString();
    }

    private int parseStatusCode(String statusLine) {
        String[] parts = statusLine.split(" ");
        if (parts.length >= 2) {
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return 500;
            }
        }
        return 500;
    }

    private String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    @Override
    public void close() throws IOException {
        if (currentSocket != null) {
            currentSocket.close();
        }
    }
}
