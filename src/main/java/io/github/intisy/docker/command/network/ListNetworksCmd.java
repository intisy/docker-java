package io.github.intisy.docker.command.network;

import com.google.gson.reflect.TypeToken;
import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.model.Network;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command to list networks.
 *
 * @author Finn Birich
 */
public class ListNetworksCmd {
    private final DockerHttpClient client;
    private Map<String, List<String>> filters;

    public ListNetworksCmd(DockerHttpClient client) {
        this.client = client;
    }

    /**
     * Add a filter.
     */
    public ListNetworksCmd withFilter(String key, String... values) {
        if (this.filters == null) {
            this.filters = new HashMap<>();
        }
        this.filters.put(key, java.util.Arrays.asList(values));
        return this;
    }

    /**
     * Filter by driver.
     */
    public ListNetworksCmd withDriverFilter(String driver) {
        return withFilter("driver", driver);
    }

    /**
     * Filter by ID.
     */
    public ListNetworksCmd withIdFilter(String id) {
        return withFilter("id", id);
    }

    /**
     * Filter by label.
     */
    public ListNetworksCmd withLabelFilter(String label) {
        return withFilter("label", label);
    }

    /**
     * Filter by name.
     */
    public ListNetworksCmd withNameFilter(String name) {
        return withFilter("name", name);
    }

    /**
     * Filter by scope (swarm, global, or local).
     */
    public ListNetworksCmd withScopeFilter(String scope) {
        return withFilter("scope", scope);
    }

    /**
     * Filter by type (custom or builtin).
     */
    public ListNetworksCmd withTypeFilter(String type) {
        return withFilter("type", type);
    }

    /**
     * Execute the command.
     */
    public List<Network> exec() {
        try {
            Map<String, String> queryParams = new HashMap<>();
            if (filters != null && !filters.isEmpty()) {
                queryParams.put("filters", client.getGson().toJson(filters));
            }

            DockerResponse response = client.get("/networks", queryParams);
            
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to list networks: " + response.getBody(), response.getStatusCode());
            }

            Type listType = new TypeToken<List<Network>>() {}.getType();
            return client.getGson().fromJson(response.getBody(), listType);
        } catch (IOException e) {
            throw new DockerException("Failed to list networks", e);
        }
    }
}
