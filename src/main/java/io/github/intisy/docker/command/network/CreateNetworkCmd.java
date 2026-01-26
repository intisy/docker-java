package io.github.intisy.docker.command.network;

import com.google.gson.annotations.SerializedName;
import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command to create a network.
 *
 * @author Finn Birich
 */
public class CreateNetworkCmd {
    private final DockerHttpClient client;
    private final NetworkConfig config;

    public CreateNetworkCmd(DockerHttpClient client) {
        this.client = client;
        this.config = new NetworkConfig();
    }

    /**
     * Set the network name.
     */
    public CreateNetworkCmd withName(String name) {
        config.name = name;
        return this;
    }

    /**
     * Set the driver.
     */
    public CreateNetworkCmd withDriver(String driver) {
        config.driver = driver;
        return this;
    }

    /**
     * Set whether the network is internal.
     */
    public CreateNetworkCmd withInternal(boolean internal) {
        config.internal = internal;
        return this;
    }

    /**
     * Set whether the network is attachable.
     */
    public CreateNetworkCmd withAttachable(boolean attachable) {
        config.attachable = attachable;
        return this;
    }

    /**
     * Set whether the network is ingress.
     */
    public CreateNetworkCmd withIngress(boolean ingress) {
        config.ingress = ingress;
        return this;
    }

    /**
     * Enable IPv6.
     */
    public CreateNetworkCmd withEnableIPv6(boolean enableIPv6) {
        config.enableIPv6 = enableIPv6;
        return this;
    }

    /**
     * Check for duplicate networks.
     */
    public CreateNetworkCmd withCheckDuplicate(boolean checkDuplicate) {
        config.checkDuplicate = checkDuplicate;
        return this;
    }

    /**
     * Set driver options.
     */
    public CreateNetworkCmd withOptions(Map<String, String> options) {
        config.options = options;
        return this;
    }

    /**
     * Add a driver option.
     */
    public CreateNetworkCmd withOption(String key, String value) {
        if (config.options == null) {
            config.options = new HashMap<>();
        }
        config.options.put(key, value);
        return this;
    }

    /**
     * Set labels.
     */
    public CreateNetworkCmd withLabels(Map<String, String> labels) {
        config.labels = labels;
        return this;
    }

    /**
     * Add a label.
     */
    public CreateNetworkCmd withLabel(String key, String value) {
        if (config.labels == null) {
            config.labels = new HashMap<>();
        }
        config.labels.put(key, value);
        return this;
    }

    /**
     * Configure IPAM.
     */
    public CreateNetworkCmd withIpam(IPAMConfig ipam) {
        config.ipam = ipam;
        return this;
    }

    /**
     * Add a subnet configuration.
     */
    public CreateNetworkCmd withSubnet(String subnet) {
        return withSubnet(subnet, null);
    }

    /**
     * Add a subnet configuration with gateway.
     */
    public CreateNetworkCmd withSubnet(String subnet, String gateway) {
        if (config.ipam == null) {
            config.ipam = new IPAMConfig();
        }
        if (config.ipam.config == null) {
            config.ipam.config = new ArrayList<>();
        }
        IPAMPoolConfig poolConfig = new IPAMPoolConfig();
        poolConfig.subnet = subnet;
        poolConfig.gateway = gateway;
        config.ipam.config.add(poolConfig);
        return this;
    }

    /**
     * Execute the command.
     */
    public CreateNetworkResponse exec() {
        try {
            DockerResponse response = client.post("/networks/create", config);
            
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to create network: " + response.getBody(), response.getStatusCode());
            }

            return client.getGson().fromJson(response.getBody(), CreateNetworkResponse.class);
        } catch (IOException e) {
            throw new DockerException("Failed to create network", e);
        }
    }

    /**
     * Network creation configuration.
     */
    private static class NetworkConfig {
        @SerializedName("Name")
        String name;

        @SerializedName("CheckDuplicate")
        Boolean checkDuplicate;

        @SerializedName("Driver")
        String driver;

        @SerializedName("Internal")
        Boolean internal;

        @SerializedName("Attachable")
        Boolean attachable;

        @SerializedName("Ingress")
        Boolean ingress;

        @SerializedName("EnableIPv6")
        Boolean enableIPv6;

        @SerializedName("IPAM")
        IPAMConfig ipam;

        @SerializedName("Options")
        Map<String, String> options;

        @SerializedName("Labels")
        Map<String, String> labels;
    }

    /**
     * IPAM configuration.
     */
    public static class IPAMConfig {
        @SerializedName("Driver")
        String driver;

        @SerializedName("Options")
        Map<String, String> options;

        @SerializedName("Config")
        List<IPAMPoolConfig> config;

        public IPAMConfig withDriver(String driver) {
            this.driver = driver;
            return this;
        }

        public IPAMConfig withOptions(Map<String, String> options) {
            this.options = options;
            return this;
        }
    }

    /**
     * IPAM pool configuration.
     */
    public static class IPAMPoolConfig {
        @SerializedName("Subnet")
        String subnet;

        @SerializedName("IPRange")
        String ipRange;

        @SerializedName("Gateway")
        String gateway;

        @SerializedName("AuxiliaryAddresses")
        Map<String, String> auxiliaryAddresses;
    }

    /**
     * Response from creating a network.
     */
    public static class CreateNetworkResponse {
        @SerializedName("Id")
        private String id;

        @SerializedName("Warning")
        private String warning;

        public String getId() {
            return id;
        }

        public String getWarning() {
            return warning;
        }

        @Override
        public String toString() {
            return "CreateNetworkResponse{" +
                    "id='" + id + '\'' +
                    ", warning='" + warning + '\'' +
                    '}';
        }
    }
}
