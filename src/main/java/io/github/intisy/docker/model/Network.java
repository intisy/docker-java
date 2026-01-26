package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * Represents a Docker network.
 *
 * @author Finn Birich
 */
public class Network {
    @SerializedName("Name")
    private String name;

    @SerializedName("Id")
    private String id;

    @SerializedName("Created")
    private String created;

    @SerializedName("Scope")
    private String scope;

    @SerializedName("Driver")
    private String driver;

    @SerializedName("EnableIPv6")
    private Boolean enableIPv6;

    @SerializedName("IPAM")
    private IPAM ipam;

    @SerializedName("Internal")
    private Boolean internal;

    @SerializedName("Attachable")
    private Boolean attachable;

    @SerializedName("Ingress")
    private Boolean ingress;

    @SerializedName("Containers")
    private Map<String, NetworkContainer> containers;

    @SerializedName("Options")
    private Map<String, String> options;

    @SerializedName("Labels")
    private Map<String, String> labels;

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public String getCreated() {
        return created;
    }

    public String getScope() {
        return scope;
    }

    public String getDriver() {
        return driver;
    }

    public Boolean getEnableIPv6() {
        return enableIPv6;
    }

    public IPAM getIpam() {
        return ipam;
    }

    public Boolean getInternal() {
        return internal;
    }

    public Boolean getAttachable() {
        return attachable;
    }

    public Boolean getIngress() {
        return ingress;
    }

    public Map<String, NetworkContainer> getContainers() {
        return containers;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    /**
     * IPAM configuration for a network.
     */
    public static class IPAM {
        @SerializedName("Driver")
        private String driver;

        @SerializedName("Options")
        private Map<String, String> options;

        @SerializedName("Config")
        private List<IPAMConfig> config;

        public String getDriver() {
            return driver;
        }

        public Map<String, String> getOptions() {
            return options;
        }

        public List<IPAMConfig> getConfig() {
            return config;
        }
    }

    /**
     * IPAM configuration entry.
     */
    public static class IPAMConfig {
        @SerializedName("Subnet")
        private String subnet;

        @SerializedName("IPRange")
        private String ipRange;

        @SerializedName("Gateway")
        private String gateway;

        @SerializedName("AuxiliaryAddresses")
        private Map<String, String> auxiliaryAddresses;

        public String getSubnet() {
            return subnet;
        }

        public String getIpRange() {
            return ipRange;
        }

        public String getGateway() {
            return gateway;
        }

        public Map<String, String> getAuxiliaryAddresses() {
            return auxiliaryAddresses;
        }
    }

    /**
     * Container connected to a network.
     */
    public static class NetworkContainer {
        @SerializedName("Name")
        private String name;

        @SerializedName("EndpointID")
        private String endpointId;

        @SerializedName("MacAddress")
        private String macAddress;

        @SerializedName("IPv4Address")
        private String ipv4Address;

        @SerializedName("IPv6Address")
        private String ipv6Address;

        public String getName() {
            return name;
        }

        public String getEndpointId() {
            return endpointId;
        }

        public String getMacAddress() {
            return macAddress;
        }

        public String getIpv4Address() {
            return ipv4Address;
        }

        public String getIpv6Address() {
            return ipv6Address;
        }
    }

    @Override
    public String toString() {
        return "Network{" +
                "name='" + name + '\'' +
                ", id='" + (id != null ? id.substring(0, Math.min(12, id.length())) : "null") + '\'' +
                ", driver='" + driver + '\'' +
                ", scope='" + scope + '\'' +
                '}';
    }
}
