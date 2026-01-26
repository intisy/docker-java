package io.github.intisy.docker.command.network;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;

/**
 * Command to remove a network.
 *
 * @author Finn Birich
 */
public class RemoveNetworkCmd {
    private final DockerHttpClient client;
    private final String networkId;

    public RemoveNetworkCmd(DockerHttpClient client, String networkId) {
        this.client = client;
        this.networkId = networkId;
    }

    /**
     * Execute the command.
     */
    public void exec() {
        try {
            DockerResponse response = client.delete("/networks/" + networkId);
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Network not found: " + networkId);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to remove network: " + response.getBody(), response.getStatusCode());
            }
        } catch (IOException e) {
            throw new DockerException("Failed to remove network", e);
        }
    }
}
