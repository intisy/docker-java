package io.github.intisy.docker.command.volume;

import com.google.gson.annotations.SerializedName;
import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.model.Volume;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Finn Birich
 */
public class CreateVolumeCmd {
    private final DockerHttpClient client;
    private final VolumeConfig config;

    public CreateVolumeCmd(DockerHttpClient client) {
        this.client = client;
        this.config = new VolumeConfig();
    }

    public CreateVolumeCmd withName(String name) {
        config.name = name;
        return this;
    }

    public CreateVolumeCmd withDriver(String driver) {
        config.driver = driver;
        return this;
    }

    public CreateVolumeCmd withDriverOpts(Map<String, String> driverOpts) {
        config.driverOpts = driverOpts;
        return this;
    }

    public CreateVolumeCmd withDriverOpt(String key, String value) {
        if (config.driverOpts == null) {
            config.driverOpts = new HashMap<>();
        }
        config.driverOpts.put(key, value);
        return this;
    }

    public CreateVolumeCmd withLabels(Map<String, String> labels) {
        config.labels = labels;
        return this;
    }

    public CreateVolumeCmd withLabel(String key, String value) {
        if (config.labels == null) {
            config.labels = new HashMap<>();
        }
        config.labels.put(key, value);
        return this;
    }

    public Volume exec() {
        try {
            DockerResponse response = client.post("/volumes/create", config);
            
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to create volume: " + response.getBody(), response.getStatusCode());
            }

            return client.getGson().fromJson(response.getBody(), Volume.class);
        } catch (IOException e) {
            throw new DockerException("Failed to create volume", e);
        }
    }

    private static class VolumeConfig {
        @SerializedName("Name")
        String name;

        @SerializedName("Driver")
        String driver;

        @SerializedName("DriverOpts")
        Map<String, String> driverOpts;

        @SerializedName("Labels")
        Map<String, String> labels;
    }
}
