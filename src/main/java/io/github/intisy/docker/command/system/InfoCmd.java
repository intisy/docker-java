package io.github.intisy.docker.command.system;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.model.SystemInfo;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;

/**
 * Command to get system information.
 *
 * @author Finn Birich
 */
public class InfoCmd {
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

            return client.getGson().fromJson(response.getBody(), SystemInfo.class);
        } catch (IOException e) {
            throw new DockerException("Failed to get system info", e);
        }
    }
}
