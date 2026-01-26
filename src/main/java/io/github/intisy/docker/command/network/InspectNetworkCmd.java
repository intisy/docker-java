package io.github.intisy.docker.command.network;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.model.Network;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Command to inspect a network.
 *
 * @author Finn Birich
 */
public class InspectNetworkCmd {
    private final DockerHttpClient client;
    private final String networkId;
    private boolean verbose = false;
    private String scope;

    public InspectNetworkCmd(DockerHttpClient client, String networkId) {
        this.client = client;
        this.networkId = networkId;
    }

    /**
     * Show detailed information.
     */
    public InspectNetworkCmd withVerbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    /**
     * Filter by scope (swarm, global, or local).
     */
    public InspectNetworkCmd withScope(String scope) {
        this.scope = scope;
        return this;
    }

    /**
     * Execute the command.
     */
    public Network exec() {
        try {
            Map<String, String> queryParams = new HashMap<>();
            if (verbose) {
                queryParams.put("verbose", "true");
            }
            if (scope != null) {
                queryParams.put("scope", scope);
            }

            DockerResponse response = client.get("/networks/" + networkId, queryParams);
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Network not found: " + networkId);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to inspect network: " + response.getBody(), response.getStatusCode());
            }

            return client.getGson().fromJson(response.getBody(), Network.class);
        } catch (IOException e) {
            throw new DockerException("Failed to inspect network", e);
        }
    }
}
