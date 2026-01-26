package io.github.intisy.docker;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Fetches the latest Docker version from GitHub.
 *
 * @author Finn Birich
 */
public class DockerVersionFetcher {
    private static final Logger log = LoggerFactory.getLogger(DockerVersionFetcher.class);
    
    private static final String GITHUB_API_URL = "https://api.github.com/repos/moby/moby/releases/latest";
    private static final String FALLBACK_VERSION = "26.1.4";
    private static String latestVersion;

    public static String getLatestVersion() {
        if (latestVersion == null) {
            try {
                log.debug("Fetching latest Docker version from GitHub...");
                URL url = new URL(GITHUB_API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

                if (conn.getResponseCode() != 200) {
                    throw new IOException("Failed to fetch latest version from GitHub: " + conn.getResponseCode());
                }

                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                    JsonObject jsonObject = new Gson().fromJson(reader, JsonObject.class);
                    latestVersion = jsonObject.get("tag_name").getAsString().replace("v", "");
                    log.debug("Latest Docker version: {}", latestVersion);
                }
            } catch (IOException e) {
                log.warn("Could not fetch latest Docker version, falling back to {}: {}", FALLBACK_VERSION, e.getMessage());
                latestVersion = FALLBACK_VERSION;
            }
        }
        return latestVersion;
    }
    
    /**
     * Reset the cached version (useful for testing).
     */
    public static void resetCache() {
        latestVersion = null;
    }
}
