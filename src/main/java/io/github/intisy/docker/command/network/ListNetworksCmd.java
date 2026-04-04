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
 * @author Finn Birich
 */
public class ListNetworksCmd {
    private final DockerHttpClient client;
    private Map<String, List<String>> filters;

    public ListNetworksCmd(DockerHttpClient client) {
        this.client = client;
    }

    public ListNetworksCmd withFilter(String key, String... values) {
        if (this.filters == null) {
            this.filters = new HashMap<>();
        }
        this.filters.put(key, java.util.Arrays.asList(values));
        return this;
    }

    public ListNetworksCmd withDriverFilter(String driver) {
        return withFilter("driver", driver);
    }

    public ListNetworksCmd withIdFilter(String id) {
        return withFilter("id", id);
    }

    public ListNetworksCmd withLabelFilter(String label) {
        return withFilter("label", label);
    }

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
