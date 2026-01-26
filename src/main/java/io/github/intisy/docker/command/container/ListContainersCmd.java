package io.github.intisy.docker.command.container;

import com.google.gson.reflect.TypeToken;
import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.model.Container;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command to list containers.
 *
 * @author Finn Birich
 */
public class ListContainersCmd {
    private final DockerHttpClient client;
    private boolean showAll = false;
    private Integer limit;
    private boolean showSize = false;
    private Map<String, List<String>> filters;

    public ListContainersCmd(DockerHttpClient client) {
        this.client = client;
    }

    /**
     * Show all containers (default shows just running).
     */
    public ListContainersCmd withShowAll(boolean showAll) {
        this.showAll = showAll;
        return this;
    }

    /**
     * Limit the number of containers returned.
     */
    public ListContainersCmd withLimit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Return size information.
     */
    public ListContainersCmd withShowSize(boolean showSize) {
        this.showSize = showSize;
        return this;
    }

    /**
     * Add a filter.
     */
    public ListContainersCmd withFilter(String key, String... values) {
        if (this.filters == null) {
            this.filters = new HashMap<>();
        }
        this.filters.put(key, java.util.Arrays.asList(values));
        return this;
    }

    /**
     * Filter by status (created, restarting, running, removing, paused, exited, dead).
     */
    public ListContainersCmd withStatusFilter(String status) {
        return withFilter("status", status);
    }

    /**
     * Filter by name.
     */
    public ListContainersCmd withNameFilter(String name) {
        return withFilter("name", name);
    }

    /**
     * Filter by label.
     */
    public ListContainersCmd withLabelFilter(String label) {
        return withFilter("label", label);
    }

    /**
     * Execute the command.
     */
    public List<Container> exec() {
        try {
            Map<String, String> queryParams = new HashMap<>();
            if (showAll) {
                queryParams.put("all", "true");
            }
            if (limit != null) {
                queryParams.put("limit", String.valueOf(limit));
            }
            if (showSize) {
                queryParams.put("size", "true");
            }
            if (filters != null && !filters.isEmpty()) {
                queryParams.put("filters", client.getGson().toJson(filters));
            }

            DockerResponse response = client.get("/containers/json", queryParams);
            
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to list containers: " + response.getBody(), response.getStatusCode());
            }

            Type listType = new TypeToken<List<Container>>() {}.getType();
            return client.getGson().fromJson(response.getBody(), listType);
        } catch (IOException e) {
            throw new DockerException("Failed to list containers", e);
        }
    }
}
