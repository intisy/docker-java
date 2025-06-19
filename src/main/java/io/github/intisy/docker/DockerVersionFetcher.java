package io.github.intisy.docker;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class DockerVersionFetcher {
    private static final String GITHUB_API_URL = "https://api.github.com/repos/moby/moby/releases/latest";
    private static final String FALLBACK_VERSION = "26.1.4";
    private static String latestVersion;

    public static String getLatestVersion() {
        if (latestVersion == null) {
            try {
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
                }
            } catch (IOException e) {
                System.err.println("Could not fetch latest Docker version, falling back to " + FALLBACK_VERSION + ". Error: " + e.getMessage());
                latestVersion = FALLBACK_VERSION;
            }
        }
        return latestVersion;
    }
}
