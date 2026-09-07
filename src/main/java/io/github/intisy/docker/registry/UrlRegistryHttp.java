package io.github.intisy.docker.registry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * The real transport: {@code HttpURLConnection}, because the library targets Java 8 and
 * {@code java.net.http} is not available to it.
 *
 * @implNote acquires a bearer token lazily on a 401 and retries once, caching per scope. Docker
 * Hub answers 401 with a challenge on the very first anonymous request, so without the retry every
 * pull would need the caller to know the flow. The retry is skipped when the request carries a
 * body: a body stream cannot be replayed once consumed, so a write cannot be transparently
 * retried after a challenge.
 *
 * @author Finn Birich
 */
public final class UrlRegistryHttp implements RegistryHttp {
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 300_000;
    private static final int STREAM_CHUNK_BYTES = 1024 * 1024;

    private final Map<String, String> tokensByScope = new HashMap<String, String>();

    @Override
    public RegistryResponse send(String method, String url, Map<String, String> headers, RegistryBody body)
            throws IOException {
        RegistryResponse first = sendOnce(method, url, headers, body, tokenFor(url));
        if (first.status() != 401 || body.isPresent()) {
            return first;
        }
        AuthChallenge challenge = AuthChallenge.parse(first.header("WWW-Authenticate"));
        if (challenge == null) {
            return first;
        }
        String scope = scopeFor(url);
        String token = fetchToken(challenge, scope);
        tokensByScope.put(scope, token);
        return sendOnce(method, url, headers, body, token);
    }

    private String tokenFor(String url) {
        return tokensByScope.get(scopeFor(url));
    }

    /**
     * @implNote derives the pull scope from the request path, so that one client can walk several
     * repositories without the caller tracking tokens. The scope is deliberately pull-only:
     * anonymous pull from Docker Hub is the only authenticated flow this client implements.
     * Supporting a registry that requires a token for writes would mean threading the HTTP method
     * through and requesting {@code pull,push}.
     */
    static String scopeFor(String url) {
        int v2 = url.indexOf("/v2/");
        if (v2 < 0) {
            return "";
        }
        String path = url.substring(v2 + "/v2/".length());
        int marker = path.indexOf("/manifests/");
        if (marker < 0) {
            marker = path.indexOf("/blobs/");
        }
        if (marker < 0) {
            return "";
        }
        return "repository:" + path.substring(0, marker) + ":pull";
    }

    private String fetchToken(AuthChallenge challenge, String scope) throws IOException {
        RegistryResponse response = sendOnce("GET", challenge.tokenUrl(scope),
                new HashMap<String, String>(), RegistryBody.none(), null);
        if (!response.isSuccessful()) {
            throw new IOException("token request failed: HTTP " + response.status() + ": " + response.text());
        }
        String body = response.text();
        String token = jsonStringField(body, "token");
        if (token == null) {
            token = jsonStringField(body, "access_token");
        }
        if (token == null) {
            throw new IOException("token endpoint returned no token field: " + body);
        }
        return token;
    }

    /**
     * @implNote a two-field read rather than a gson dependency at this layer, so the transport
     * stays free of any model. The token document has exactly one field this needs.
     */
    static String jsonStringField(String json, String field) {
        String needle = "\"" + field + "\"";
        int at = json.indexOf(needle);
        if (at < 0) {
            return null;
        }
        int colon = json.indexOf(':', at + needle.length());
        int open = json.indexOf('"', colon + 1);
        int close = json.indexOf('"', open + 1);
        return open < 0 || close < 0 ? null : json.substring(open + 1, close);
    }

    private RegistryResponse sendOnce(String method, String url, Map<String, String> headers,
                                      RegistryBody body, String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (body.isPresent()) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(body.length());
                writeBody(connection, body);
            }
            int status = connection.getResponseCode();
            byte[] responseBody = readBody(connection, status);
            Map<String, String> responseHeaders = new HashMap<String, String>();
            for (Map.Entry<String, java.util.List<String>> entry : connection.getHeaderFields().entrySet()) {
                if (entry.getKey() != null && !entry.getValue().isEmpty()) {
                    responseHeaders.put(entry.getKey(), entry.getValue().get(0));
                }
            }
            return new RegistryResponse(status, responseHeaders, responseBody);
        } finally {
            connection.disconnect();
        }
    }

    private static void writeBody(HttpURLConnection connection, RegistryBody body) throws IOException {
        OutputStream out = connection.getOutputStream();
        try {
            InputStream in = body.openStream();
            try {
                byte[] buffer = new byte[STREAM_CHUNK_BYTES];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            } finally {
                in.close();
            }
        } finally {
            out.close();
        }
    }

    /**
     * @implNote a non-2xx response body arrives on the error stream, and reading the input stream
     * instead throws, which would replace the registry's own explanation with an IOException that
     * says nothing.
     */
    private static byte[] readBody(HttpURLConnection connection, int status) throws IOException {
        InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (in == null) {
            return new byte[0];
        }
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[STREAM_CHUNK_BYTES];
            int read;
            while ((read = in.read(buffer)) != -1) {
                collected.write(buffer, 0, read);
            }
        } finally {
            in.close();
        }
        return collected.toByteArray();
    }
}
