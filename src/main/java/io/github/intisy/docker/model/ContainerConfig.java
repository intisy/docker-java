package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for creating a container.
 *
 * @author Finn Birich
 */
public class ContainerConfig {
    @SerializedName("Hostname")
    private String hostname;

    @SerializedName("Domainname")
    private String domainname;

    @SerializedName("User")
    private String user;

    @SerializedName("AttachStdin")
    private Boolean attachStdin;

    @SerializedName("AttachStdout")
    private Boolean attachStdout;

    @SerializedName("AttachStderr")
    private Boolean attachStderr;

    @SerializedName("ExposedPorts")
    private Map<String, Object> exposedPorts;

    @SerializedName("Tty")
    private Boolean tty;

    @SerializedName("OpenStdin")
    private Boolean openStdin;

    @SerializedName("StdinOnce")
    private Boolean stdinOnce;

    @SerializedName("Env")
    private List<String> env;

    @SerializedName("Cmd")
    private List<String> cmd;

    @SerializedName("Entrypoint")
    private List<String> entrypoint;

    @SerializedName("Image")
    private String image;

    @SerializedName("Labels")
    private Map<String, String> labels;

    @SerializedName("Volumes")
    private Map<String, Object> volumes;

    @SerializedName("WorkingDir")
    private String workingDir;

    @SerializedName("NetworkDisabled")
    private Boolean networkDisabled;

    @SerializedName("MacAddress")
    private String macAddress;

    @SerializedName("StopSignal")
    private String stopSignal;

    @SerializedName("StopTimeout")
    private Integer stopTimeout;

    @SerializedName("HostConfig")
    private HostConfig hostConfig;

    @SerializedName("NetworkingConfig")
    private NetworkingConfig networkingConfig;

    public ContainerConfig() {
    }

    public String getHostname() {
        return hostname;
    }

    public ContainerConfig setHostname(String hostname) {
        this.hostname = hostname;
        return this;
    }

    public String getDomainname() {
        return domainname;
    }

    public ContainerConfig setDomainname(String domainname) {
        this.domainname = domainname;
        return this;
    }

    public String getUser() {
        return user;
    }

    public ContainerConfig setUser(String user) {
        this.user = user;
        return this;
    }

    public Boolean getAttachStdin() {
        return attachStdin;
    }

    public ContainerConfig setAttachStdin(Boolean attachStdin) {
        this.attachStdin = attachStdin;
        return this;
    }

    public Boolean getAttachStdout() {
        return attachStdout;
    }

    public ContainerConfig setAttachStdout(Boolean attachStdout) {
        this.attachStdout = attachStdout;
        return this;
    }

    public Boolean getAttachStderr() {
        return attachStderr;
    }

    public ContainerConfig setAttachStderr(Boolean attachStderr) {
        this.attachStderr = attachStderr;
        return this;
    }

    public Map<String, Object> getExposedPorts() {
        return exposedPorts;
    }

    public ContainerConfig setExposedPorts(Map<String, Object> exposedPorts) {
        this.exposedPorts = exposedPorts;
        return this;
    }

    public ContainerConfig addExposedPort(ExposedPort port) {
        if (this.exposedPorts == null) {
            this.exposedPorts = new HashMap<>();
        }
        this.exposedPorts.put(port.toString(), new HashMap<>());
        return this;
    }

    public Boolean getTty() {
        return tty;
    }

    public ContainerConfig setTty(Boolean tty) {
        this.tty = tty;
        return this;
    }

    public Boolean getOpenStdin() {
        return openStdin;
    }

    public ContainerConfig setOpenStdin(Boolean openStdin) {
        this.openStdin = openStdin;
        return this;
    }

    public Boolean getStdinOnce() {
        return stdinOnce;
    }

    public ContainerConfig setStdinOnce(Boolean stdinOnce) {
        this.stdinOnce = stdinOnce;
        return this;
    }

    public List<String> getEnv() {
        return env;
    }

    public ContainerConfig setEnv(List<String> env) {
        this.env = env;
        return this;
    }

    public ContainerConfig addEnv(String key, String value) {
        if (this.env == null) {
            this.env = new ArrayList<>();
        }
        this.env.add(key + "=" + value);
        return this;
    }

    public List<String> getCmd() {
        return cmd;
    }

    public ContainerConfig setCmd(List<String> cmd) {
        this.cmd = cmd;
        return this;
    }

    public ContainerConfig setCmd(String... cmd) {
        this.cmd = new ArrayList<>();
        for (String c : cmd) {
            this.cmd.add(c);
        }
        return this;
    }

    public List<String> getEntrypoint() {
        return entrypoint;
    }

    public ContainerConfig setEntrypoint(List<String> entrypoint) {
        this.entrypoint = entrypoint;
        return this;
    }

    public ContainerConfig setEntrypoint(String... entrypoint) {
        this.entrypoint = new ArrayList<>();
        for (String e : entrypoint) {
            this.entrypoint.add(e);
        }
        return this;
    }

    public String getImage() {
        return image;
    }

    public ContainerConfig setImage(String image) {
        this.image = image;
        return this;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public ContainerConfig setLabels(Map<String, String> labels) {
        this.labels = labels;
        return this;
    }

    public ContainerConfig addLabel(String key, String value) {
        if (this.labels == null) {
            this.labels = new HashMap<>();
        }
        this.labels.put(key, value);
        return this;
    }

    public Map<String, Object> getVolumes() {
        return volumes;
    }

    public ContainerConfig setVolumes(Map<String, Object> volumes) {
        this.volumes = volumes;
        return this;
    }

    public ContainerConfig addVolume(String path) {
        if (this.volumes == null) {
            this.volumes = new HashMap<>();
        }
        this.volumes.put(path, new HashMap<>());
        return this;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    public ContainerConfig setWorkingDir(String workingDir) {
        this.workingDir = workingDir;
        return this;
    }

    public Boolean getNetworkDisabled() {
        return networkDisabled;
    }

    public ContainerConfig setNetworkDisabled(Boolean networkDisabled) {
        this.networkDisabled = networkDisabled;
        return this;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public ContainerConfig setMacAddress(String macAddress) {
        this.macAddress = macAddress;
        return this;
    }

    public String getStopSignal() {
        return stopSignal;
    }

    public ContainerConfig setStopSignal(String stopSignal) {
        this.stopSignal = stopSignal;
        return this;
    }

    public Integer getStopTimeout() {
        return stopTimeout;
    }

    public ContainerConfig setStopTimeout(Integer stopTimeout) {
        this.stopTimeout = stopTimeout;
        return this;
    }

    public HostConfig getHostConfig() {
        return hostConfig;
    }

    public ContainerConfig setHostConfig(HostConfig hostConfig) {
        this.hostConfig = hostConfig;
        return this;
    }

    public NetworkingConfig getNetworkingConfig() {
        return networkingConfig;
    }

    public ContainerConfig setNetworkingConfig(NetworkingConfig networkingConfig) {
        this.networkingConfig = networkingConfig;
        return this;
    }

    /**
     * Network configuration for container creation.
     */
    public static class NetworkingConfig {
        @SerializedName("EndpointsConfig")
        private Map<String, EndpointConfig> endpointsConfig;

        public NetworkingConfig() {
        }

        public Map<String, EndpointConfig> getEndpointsConfig() {
            return endpointsConfig;
        }

        public NetworkingConfig setEndpointsConfig(Map<String, EndpointConfig> endpointsConfig) {
            this.endpointsConfig = endpointsConfig;
            return this;
        }

        public NetworkingConfig addEndpoint(String networkName, EndpointConfig config) {
            if (this.endpointsConfig == null) {
                this.endpointsConfig = new HashMap<>();
            }
            this.endpointsConfig.put(networkName, config);
            return this;
        }
    }

    /**
     * Endpoint configuration for a network.
     */
    public static class EndpointConfig {
        @SerializedName("IPAMConfig")
        private IPAMConfig ipamConfig;

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

        public IPAMConfig getIpamConfig() {
            return ipamConfig;
        }

        public EndpointConfig setIpamConfig(IPAMConfig ipamConfig) {
            this.ipamConfig = ipamConfig;
            return this;
        }

        public List<String> getLinks() {
            return links;
        }

        public EndpointConfig setLinks(List<String> links) {
            this.links = links;
            return this;
        }

        public List<String> getAliases() {
            return aliases;
        }

        public EndpointConfig setAliases(List<String> aliases) {
            this.aliases = aliases;
            return this;
        }

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
     * IPAM configuration for an endpoint.
     */
    public static class IPAMConfig {
        @SerializedName("IPv4Address")
        private String ipv4Address;

        @SerializedName("IPv6Address")
        private String ipv6Address;

        @SerializedName("LinkLocalIPs")
        private List<String> linkLocalIPs;

        public String getIpv4Address() {
            return ipv4Address;
        }

        public IPAMConfig setIpv4Address(String ipv4Address) {
            this.ipv4Address = ipv4Address;
            return this;
        }

        public String getIpv6Address() {
            return ipv6Address;
        }

        public IPAMConfig setIpv6Address(String ipv6Address) {
            this.ipv6Address = ipv6Address;
            return this;
        }

        public List<String> getLinkLocalIPs() {
            return linkLocalIPs;
        }

        public IPAMConfig setLinkLocalIPs(List<String> linkLocalIPs) {
            this.linkLocalIPs = linkLocalIPs;
            return this;
        }
    }
}
