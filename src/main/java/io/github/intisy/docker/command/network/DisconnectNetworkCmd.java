package io.github.intisy.docker.command.network;

import com.google.gson.annotations.SerializedName;
import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;

/**
 * Command to disconnect a container from a network.
 *
 * @author Finn Birich
 */
public class DisconnectNetworkCmd {
    private final DockerHttpClient client;
    private final String networkId;
    private final DisconnectConfig config;

    public DisconnectNetworkCmd(DockerHttpClient client, String networkId) {
        this.client = client;
        this.networkId = networkId;
        this.config = new DisconnectConfig();
    }

    /**
     * Set the container to disconnect.
     */
    public DisconnectNetworkCmd withContainerId(String containerId) {
        config.container = containerId;
        return this;
    }

    /**
     * Force disconnection.
     */
    public DisconnectNetworkCmd withForce(boolean force) {
        config.force = force;
        return this;
    }

    /**
     * Execute the command.
     */
    public void exec() {
        try {
            DockerResponse response = client.post("/networks/" + networkId + "/disconnect", config);
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Network or container not found: " + networkId);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to disconnect container from network: " + response.getBody(), response.getStatusCode());
            }
        } catch (IOException e) {
            throw new DockerException("Failed to disconnect container from network", e);
        }
    }

    /**
     * Disconnect configuration.
     */
    private static class DisconnectConfig {
        @SerializedName("Container")
        String container;

        @SerializedName("Force")
        Boolean force;
    }
}
