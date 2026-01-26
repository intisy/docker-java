package io.github.intisy.docker.command.container;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotModifiedException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;

/**
 * Command to start a container.
 *
 * @author Finn Birich
 */
public class StartContainerCmd {
    private final DockerHttpClient client;
    private final String containerId;
    private String detachKeys;

    public StartContainerCmd(DockerHttpClient client, String containerId) {
        this.client = client;
        this.containerId = containerId;
    }

    /**
     * Set detach keys.
     */
    public StartContainerCmd withDetachKeys(String detachKeys) {
        this.detachKeys = detachKeys;
        return this;
    }

    /**
     * Execute the command.
     */
    public void exec() {
        try {
            String path = "/containers/" + containerId + "/start";
            if (detachKeys != null) {
                path += "?detachKeys=" + detachKeys;
            }
            
            DockerResponse response = client.post(path);
            
            if (response.getStatusCode() == 304) {
                throw new NotModifiedException("Container already started: " + containerId);
            }
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Container not found: " + containerId);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to start container: " + response.getBody(), response.getStatusCode());
            }
        } catch (IOException e) {
            throw new DockerException("Failed to start container", e);
        }
    }
}
