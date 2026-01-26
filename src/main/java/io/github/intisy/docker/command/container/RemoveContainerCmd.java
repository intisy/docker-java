package io.github.intisy.docker.command.container;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Command to remove a container.
 *
 * @author Finn Birich
 */
public class RemoveContainerCmd {
    private final DockerHttpClient client;
    private final String containerId;
    private boolean force = false;
    private boolean removeVolumes = false;
    private boolean removeLinks = false;

    public RemoveContainerCmd(DockerHttpClient client, String containerId) {
        this.client = client;
        this.containerId = containerId;
    }

    /**
     * Force remove a running container.
     */
    public RemoveContainerCmd withForce(boolean force) {
        this.force = force;
        return this;
    }

    /**
     * Remove associated volumes.
     */
    public RemoveContainerCmd withRemoveVolumes(boolean removeVolumes) {
        this.removeVolumes = removeVolumes;
        return this;
    }

    /**
     * Remove associated links.
     */
    public RemoveContainerCmd withRemoveLinks(boolean removeLinks) {
        this.removeLinks = removeLinks;
        return this;
    }

    /**
     * Execute the command.
     */
    public void exec() {
        try {
            Map<String, String> queryParams = new HashMap<>();
            if (force) {
                queryParams.put("force", "true");
            }
            if (removeVolumes) {
                queryParams.put("v", "true");
            }
            if (removeLinks) {
                queryParams.put("link", "true");
            }

            DockerResponse response = client.delete("/containers/" + containerId, queryParams);
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Container not found: " + containerId);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to remove container: " + response.getBody(), response.getStatusCode());
            }
        } catch (IOException e) {
            throw new DockerException("Failed to remove container", e);
        }
    }
}
