package io.github.intisy.docker.command.network;

import com.google.gson.annotations.SerializedName;
import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Command to connect a container to a network.
 *
 * @author Finn Birich
 */
public class ConnectNetworkCmd {
    private final DockerHttpClient client;
    private final String networkId;
    private final ConnectConfig config;

    public ConnectNetworkCmd(DockerHttpClient client, String networkId) {
        this.client = client;
        this.networkId = networkId;
        this.config = new ConnectConfig();
    }

    /**
     * Set the container to connect.
     */
    public ConnectNetworkCmd withContainerId(String containerId) {
        config.container = containerId;
        return this;
    }

    /**
     * Configure endpoint settings.
     */
    public ConnectNetworkCmd withEndpointConfig(EndpointConfig endpointConfig) {
        config.endpointConfig = endpointConfig;
        return this;
    }

    /**
     * Set IPv4 address for the container.
     */
    public ConnectNetworkCmd withIPv4Address(String ipv4Address) {
        if (config.endpointConfig == null) {
            config.endpointConfig = new EndpointConfig();
        }
        if (config.endpointConfig.ipamConfig == null) {
            config.endpointConfig.ipamConfig = new IPAMConfig();
        }
        config.endpointConfig.ipamConfig.ipv4Address = ipv4Address;
        return this;
    }

    /**
     * Set IPv6 address for the container.
     */
    public ConnectNetworkCmd withIPv6Address(String ipv6Address) {
        if (config.endpointConfig == null) {
            config.endpointConfig = new EndpointConfig();
        }
        if (config.endpointConfig.ipamConfig == null) {
            config.endpointConfig.ipamConfig = new IPAMConfig();
        }
        config.endpointConfig.ipamConfig.ipv6Address = ipv6Address;
        return this;
    }

    /**
     * Add network aliases for the container.
     */
    public ConnectNetworkCmd withAliases(String... aliases) {
        if (config.endpointConfig == null) {
            config.endpointConfig = new EndpointConfig();
        }
        if (config.endpointConfig.aliases == null) {
            config.endpointConfig.aliases = new ArrayList<>();
        }
        for (String alias : aliases) {
            config.endpointConfig.aliases.add(alias);
        }
        return this;
    }

    /**
     * Execute the command.
     */
    public void exec() {
        try {
            DockerResponse response = client.post("/networks/" + networkId + "/connect", config);
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Network or container not found: " + networkId);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to connect container to network: " + response.getBody(), response.getStatusCode());
            }
        } catch (IOException e) {
            throw new DockerException("Failed to connect container to network", e);
        }
    }

    /**
     * Connection configuration.
     */
    private static class ConnectConfig {
        @SerializedName("Container")
        String container;

        @SerializedName("EndpointConfig")
        EndpointConfig endpointConfig;
    }

    /**
     * Endpoint configuration.
     */
    public static class EndpointConfig {
        @SerializedName("IPAMConfig")
        IPAMConfig ipamConfig;

        @SerializedName("Links")
        List<String> links;

        @SerializedName("Aliases")
        List<String> aliases;
    }

    /**
     * IPAM configuration for endpoint.
     */
    public static class IPAMConfig {
        @SerializedName("IPv4Address")
        String ipv4Address;

        @SerializedName("IPv6Address")
        String ipv6Address;

        @SerializedName("LinkLocalIPs")
        List<String> linkLocalIPs;
    }
}
