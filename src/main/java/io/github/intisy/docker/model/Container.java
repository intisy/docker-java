package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * Represents a Docker container.
 *
 * @author Finn Birich
 */
public class Container {
    @SerializedName("Id")
    private String id;

    @SerializedName("Names")
    private List<String> names;

    @SerializedName("Image")
    private String image;

    @SerializedName("ImageID")
    private String imageId;

    @SerializedName("Command")
    private String command;

    @SerializedName("Created")
    private Long created;

    @SerializedName("State")
    private String state;

    @SerializedName("Status")
    private String status;

    @SerializedName("Ports")
    private List<Port> ports;

    @SerializedName("Labels")
    private Map<String, String> labels;

    @SerializedName("SizeRw")
    private Long sizeRw;

    @SerializedName("SizeRootFs")
    private Long sizeRootFs;

    @SerializedName("HostConfig")
    private ContainerHostConfig hostConfig;

    @SerializedName("NetworkSettings")
    private ContainerNetworkSettings networkSettings;

    @SerializedName("Mounts")
    private List<MountPoint> mounts;

    public String getId() {
        return id;
    }

    public List<String> getNames() {
        return names;
    }

    public String getImage() {
        return image;
    }

    public String getImageId() {
        return imageId;
    }

    public String getCommand() {
        return command;
    }

    public Long getCreated() {
        return created;
    }

    public String getState() {
        return state;
    }

    public String getStatus() {
        return status;
    }

    public List<Port> getPorts() {
        return ports;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public Long getSizeRw() {
        return sizeRw;
    }

    public Long getSizeRootFs() {
        return sizeRootFs;
    }

    public ContainerHostConfig getHostConfig() {
        return hostConfig;
    }

    public ContainerNetworkSettings getNetworkSettings() {
        return networkSettings;
    }

    public List<MountPoint> getMounts() {
        return mounts;
    }

    /**
     * Port information for a container.
     */
    public static class Port {
        @SerializedName("IP")
        private String ip;

        @SerializedName("PrivatePort")
        private Integer privatePort;

        @SerializedName("PublicPort")
        private Integer publicPort;

        @SerializedName("Type")
        private String type;

        public String getIp() {
            return ip;
        }

        public Integer getPrivatePort() {
            return privatePort;
        }

        public Integer getPublicPort() {
            return publicPort;
        }

        public String getType() {
            return type;
        }
    }

    /**
     * Host config summary for container listing.
     */
    public static class ContainerHostConfig {
        @SerializedName("NetworkMode")
        private String networkMode;

        public String getNetworkMode() {
            return networkMode;
        }
    }

    /**
     * Network settings for container listing.
     */
    public static class ContainerNetworkSettings {
        @SerializedName("Networks")
        private Map<String, NetworkInfo> networks;

        public Map<String, NetworkInfo> getNetworks() {
            return networks;
        }
    }

    /**
     * Network info.
     */
    public static class NetworkInfo {
        @SerializedName("IPAMConfig")
        private Object ipamConfig;

        @SerializedName("Links")
        private List<String> links;

        @SerializedName("Aliases")
        private List<String> aliases;

        @SerializedName("NetworkID")
        private String networkId;

        @SerializedName("EndpointID")
        private String endpointId;

        @SerializedName("Gateway")
        private String gateway;

        @SerializedName("IPAddress")
        private String ipAddress;

        @SerializedName("IPPrefixLen")
        private Integer ipPrefixLen;

        @SerializedName("MacAddress")
        private String macAddress;

        public String getNetworkId() {
            return networkId;
        }

        public String getEndpointId() {
            return endpointId;
        }

        public String getGateway() {
            return gateway;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public Integer getIpPrefixLen() {
            return ipPrefixLen;
        }

        public String getMacAddress() {
            return macAddress;
        }
    }

    /**
     * Mount point for a container.
     */
    public static class MountPoint {
        @SerializedName("Type")
        private String type;

        @SerializedName("Name")
        private String name;

        @SerializedName("Source")
        private String source;

        @SerializedName("Destination")
        private String destination;

        @SerializedName("Driver")
        private String driver;

        @SerializedName("Mode")
        private String mode;

        @SerializedName("RW")
        private Boolean rw;

        @SerializedName("Propagation")
        private String propagation;

        public String getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public String getSource() {
            return source;
        }

        public String getDestination() {
            return destination;
        }

        public String getDriver() {
            return driver;
        }

        public String getMode() {
            return mode;
        }

        public Boolean getRw() {
            return rw;
        }

        public String getPropagation() {
            return propagation;
        }
    }

    @Override
    public String toString() {
        return "Container{" +
                "id='" + (id != null ? id.substring(0, Math.min(12, id.length())) : "null") + '\'' +
                ", names=" + names +
                ", image='" + image + '\'' +
                ", state='" + state + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
