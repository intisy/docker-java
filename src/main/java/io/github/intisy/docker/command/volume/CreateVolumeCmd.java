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
 * Command to create a volume.
 *
 * @author Finn Birich
 */
public class CreateVolumeCmd {
    private final DockerHttpClient client;
    private final VolumeConfig config;

    public CreateVolumeCmd(DockerHttpClient client) {
        this.client = client;
        this.config = new VolumeConfig();
    }

    /**
     * Set the volume name.
     */
    public CreateVolumeCmd withName(String name) {
        config.name = name;
        return this;
    }

    /**
     * Set the driver to use.
     */
    public CreateVolumeCmd withDriver(String driver) {
        config.driver = driver;
        return this;
    }

    /**
     * Set driver-specific options.
     */
    public CreateVolumeCmd withDriverOpts(Map<String, String> driverOpts) {
        config.driverOpts = driverOpts;
        return this;
    }

    /**
     * Add a driver option.
     */
    public CreateVolumeCmd withDriverOpt(String key, String value) {
        if (config.driverOpts == null) {
            config.driverOpts = new HashMap<>();
        }
        config.driverOpts.put(key, value);
        return this;
    }

    /**
     * Set labels.
     */
    public CreateVolumeCmd withLabels(Map<String, String> labels) {
        config.labels = labels;
        return this;
    }

    /**
     * Add a label.
     */
    public CreateVolumeCmd withLabel(String key, String value) {
        if (config.labels == null) {
            config.labels = new HashMap<>();
        }
        config.labels.put(key, value);
        return this;
    }

    /**
     * Execute the command.
     */
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

    /**
     * Volume creation configuration.
     */
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
