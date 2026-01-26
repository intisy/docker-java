package io.github.intisy.docker.command.volume;

import com.google.gson.annotations.SerializedName;
import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.model.Volume;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command to list volumes.
 *
 * @author Finn Birich
 */
public class ListVolumesCmd {
    private final DockerHttpClient client;
    private Map<String, List<String>> filters;

    public ListVolumesCmd(DockerHttpClient client) {
        this.client = client;
    }

    /**
     * Add a filter.
     */
    public ListVolumesCmd withFilter(String key, String... values) {
        if (this.filters == null) {
            this.filters = new HashMap<>();
        }
        this.filters.put(key, java.util.Arrays.asList(values));
        return this;
    }

    /**
     * Filter by dangling status.
     */
    public ListVolumesCmd withDanglingFilter(boolean dangling) {
        return withFilter("dangling", String.valueOf(dangling));
    }

    /**
     * Filter by driver.
     */
    public ListVolumesCmd withDriverFilter(String driver) {
        return withFilter("driver", driver);
    }

    /**
     * Filter by label.
     */
    public ListVolumesCmd withLabelFilter(String label) {
        return withFilter("label", label);
    }

    /**
     * Filter by name.
     */
    public ListVolumesCmd withNameFilter(String name) {
        return withFilter("name", name);
    }

    /**
     * Execute the command.
     */
    public VolumesResponse exec() {
        try {
            Map<String, String> queryParams = new HashMap<>();
            if (filters != null && !filters.isEmpty()) {
                queryParams.put("filters", client.getGson().toJson(filters));
            }

            DockerResponse response = client.get("/volumes", queryParams);
            
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to list volumes: " + response.getBody(), response.getStatusCode());
            }

            return client.getGson().fromJson(response.getBody(), VolumesResponse.class);
        } catch (IOException e) {
            throw new DockerException("Failed to list volumes", e);
        }
    }

    /**
     * Response containing list of volumes.
     */
    public static class VolumesResponse {
        @SerializedName("Volumes")
        private List<Volume> volumes;

        @SerializedName("Warnings")
        private List<String> warnings;

        public List<Volume> getVolumes() {
            return volumes;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }
}
