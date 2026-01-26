package io.github.intisy.docker.command.container;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.model.WaitResponse;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;

/**
 * Command to wait for a container to exit.
 *
 * @author Finn Birich
 */
public class WaitContainerCmd {
    private final DockerHttpClient client;
    private final String containerId;
    private String condition;

    public WaitContainerCmd(DockerHttpClient client, String containerId) {
        this.client = client;
        this.containerId = containerId;
    }

    /**
     * Wait condition: 'not-running' (default), 'next-exit', or 'removed'.
     */
    public WaitContainerCmd withCondition(String condition) {
        this.condition = condition;
        return this;
    }

    /**
     * Execute the command.
     */
    public WaitResponse exec() {
        try {
            String path = "/containers/" + containerId + "/wait";
            if (condition != null) {
                path += "?condition=" + condition;
            }

            DockerResponse response = client.post(path);
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Container not found: " + containerId);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to wait for container: " + response.getBody(), response.getStatusCode());
            }

            return client.getGson().fromJson(response.getBody(), WaitResponse.class);
        } catch (IOException e) {
            throw new DockerException("Failed to wait for container", e);
        }
    }
}
