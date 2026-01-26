package io.github.intisy.docker.command.container;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.model.ContainerInspect;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Command to inspect a container.
 *
 * @author Finn Birich
 */
public class InspectContainerCmd {
    private final DockerHttpClient client;
    private final String containerId;
    private boolean showSize = false;

    public InspectContainerCmd(DockerHttpClient client, String containerId) {
        this.client = client;
        this.containerId = containerId;
    }

    /**
     * Return size information.
     */
    public InspectContainerCmd withSize(boolean showSize) {
        this.showSize = showSize;
        return this;
    }

    /**
     * Execute the command.
     */
    public ContainerInspect exec() {
        try {
            Map<String, String> queryParams = new HashMap<>();
            if (showSize) {
                queryParams.put("size", "true");
            }

            DockerResponse response = client.get("/containers/" + containerId + "/json", queryParams);
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Container not found: " + containerId);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to inspect container: " + response.getBody(), response.getStatusCode());
            }

            return client.getGson().fromJson(response.getBody(), ContainerInspect.class);
        } catch (IOException e) {
            throw new DockerException("Failed to inspect container", e);
        }
    }
}
