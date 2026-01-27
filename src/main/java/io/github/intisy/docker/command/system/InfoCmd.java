package io.github.intisy.docker.command.system;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.model.SystemInfo;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Command to get system information.
 *
 * @author Finn Birich
 */
public class InfoCmd {
    private static final Logger log = LoggerFactory.getLogger(InfoCmd.class);
    private final DockerHttpClient client;

    public InfoCmd(DockerHttpClient client) {
        this.client = client;
    }

    /**
     * Execute the command.
     */
    public SystemInfo exec() {
        try {
            DockerResponse response = client.get("/info");
            
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to get system info: " + response.getBody(), response.getStatusCode());
            }

            String body = response.getBody();
            
            try {
                return client.getGson().fromJson(body, SystemInfo.class);
            } catch (Exception e) {
                System.err.println("=== DOCKER INFO PARSE ERROR ===");
                System.err.println("Exception: " + e.getClass().getName() + ": " + e.getMessage());
                if (e.getCause() != null) {
                    System.err.println("Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
                }
                System.err.println("Response body (first 3000 chars):");
                System.err.println(body.length() > 3000 ? body.substring(0, 3000) + "..." : body);
                System.err.println("=== END DOCKER INFO PARSE ERROR ===");
                throw e;
            }
        } catch (IOException e) {
            throw new DockerException("Failed to get system info", e);
        }
    }
}
