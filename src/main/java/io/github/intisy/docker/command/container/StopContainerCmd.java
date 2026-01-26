package io.github.intisy.docker.command.container;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotModifiedException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Command to stop a container.
 *
 * @author Finn Birich
 */
public class StopContainerCmd {
    private final DockerHttpClient client;
    private final String containerId;
    private Integer timeout;

    public StopContainerCmd(DockerHttpClient client, String containerId) {
        this.client = client;
        this.containerId = containerId;
    }

    /**
     * Set timeout in seconds before killing the container.
     */
    public StopContainerCmd withTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Execute the command.
     */
    public void exec() {
        try {
            Map<String, String> queryParams = new HashMap<>();
            if (timeout != null) {
                queryParams.put("t", String.valueOf(timeout));
            }

            DockerResponse response = client.post("/containers/" + containerId + "/stop", queryParams, null);
            
            if (response.getStatusCode() == 304) {
                throw new NotModifiedException("Container already stopped: " + containerId);
            }
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Container not found: " + containerId);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to stop container: " + response.getBody(), response.getStatusCode());
            }
        } catch (IOException e) {
            throw new DockerException("Failed to stop container", e);
        }
    }
}
