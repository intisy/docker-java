package io.github.intisy.docker.command.container;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;

/**
 * Command to unpause a container.
 *
 * @author Finn Birich
 */
public class UnpauseContainerCmd {
    private final DockerHttpClient client;
    private final String containerId;

    public UnpauseContainerCmd(DockerHttpClient client, String containerId) {
        this.client = client;
        this.containerId = containerId;
    }

    /**
     * Execute the command.
     */
    public void exec() {
        try {
            DockerResponse response = client.post("/containers/" + containerId + "/unpause");
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Container not found: " + containerId);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to unpause container: " + response.getBody(), response.getStatusCode());
            }
        } catch (IOException e) {
            throw new DockerException("Failed to unpause container", e);
        }
    }
}
