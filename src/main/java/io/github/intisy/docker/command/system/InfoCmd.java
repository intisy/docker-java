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
            log.debug("Raw /info response (first 2000 chars): {}", 
                    body.length() > 2000 ? body.substring(0, 2000) + "..." : body);
            
            try {
                return client.getGson().fromJson(body, SystemInfo.class);
            } catch (Exception e) {
                log.error("Failed to parse /info response. Response body: {}", body);
                throw e;
            }
        } catch (IOException e) {
            throw new DockerException("Failed to get system info", e);
        }
    }
}
