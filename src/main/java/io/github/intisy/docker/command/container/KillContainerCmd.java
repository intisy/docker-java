package io.github.intisy.docker.command.container;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Command to kill a container.
 *
 * @author Finn Birich
 */
public class KillContainerCmd {
    private final DockerHttpClient client;
    private final String containerId;
    private String signal;

    public KillContainerCmd(DockerHttpClient client, String containerId) {
        this.client = client;
        this.containerId = containerId;
    }

    /**
     * Set the signal to send (default: SIGKILL).
     */
    public KillContainerCmd withSignal(String signal) {
        this.signal = signal;
        return this;
    }

    /**
     * Execute the command.
     */
    public void exec() {
        try {
            Map<String, String> queryParams = new HashMap<>();
            if (signal != null) {
                queryParams.put("signal", signal);
            }

            DockerResponse response = client.post("/containers/" + containerId + "/kill", queryParams, null);
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Container not found: " + containerId);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to kill container: " + response.getBody(), response.getStatusCode());
            }
        } catch (IOException e) {
            throw new DockerException("Failed to kill container", e);
        }
    }
}
