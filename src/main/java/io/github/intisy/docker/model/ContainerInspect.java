package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * Detailed information about a container from docker inspect.
 *
 * @author Finn Birich
 */
public class ContainerInspect {
    @SerializedName("Id")
    private String id;

    @SerializedName("Created")
    private String created;

    @SerializedName("Path")
    private String path;

    @SerializedName("Args")
    private List<String> args;

    @SerializedName("State")
    private ContainerState state;

    @SerializedName("Image")
    private String image;

    @SerializedName("ResolvConfPath")
    private String resolvConfPath;

    @SerializedName("HostnamePath")
    private String hostnamePath;

    @SerializedName("HostsPath")
    private String hostsPath;

    @SerializedName("LogPath")
    private String logPath;

    @SerializedName("Name")
    private String name;

    @SerializedName("RestartCount")
    private Integer restartCount;

    @SerializedName("Driver")
    private String driver;

    @SerializedName("Platform")
    private String platform;

    @SerializedName("MountLabel")
    private String mountLabel;

    @SerializedName("ProcessLabel")
    private String processLabel;

    @SerializedName("AppArmorProfile")
    private String appArmorProfile;

    @SerializedName("ExecIDs")
    private List<String> execIds;

    @SerializedName("HostConfig")
    private HostConfig hostConfig;

    @SerializedName("Config")
    private ContainerConfig config;

    @SerializedName("NetworkSettings")
    private NetworkSettings networkSettings;

    @SerializedName("Mounts")
    private List<Container.MountPoint> mounts;

    public String getId() {
        return id;
    }

    public String getCreated() {
        return created;
    }

    public String getPath() {
        return path;
    }

    public List<String> getArgs() {
        return args;
    }

    public ContainerState getState() {
        return state;
    }

    public String getImage() {
        return image;
    }

    public String getResolvConfPath() {
        return resolvConfPath;
    }

    public String getHostnamePath() {
        return hostnamePath;
    }

    public String getHostsPath() {
        return hostsPath;
    }

    public String getLogPath() {
        return logPath;
    }

    public String getName() {
        return name;
    }

    public Integer getRestartCount() {
        return restartCount;
    }

    public String getDriver() {
        return driver;
    }

    public String getPlatform() {
        return platform;
    }

    public String getMountLabel() {
        return mountLabel;
    }

    public String getProcessLabel() {
        return processLabel;
    }

    public String getAppArmorProfile() {
        return appArmorProfile;
    }

    public List<String> getExecIds() {
        return execIds;
    }

    public HostConfig getHostConfig() {
        return hostConfig;
    }

    public ContainerConfig getConfig() {
        return config;
    }

    public NetworkSettings getNetworkSettings() {
        return networkSettings;
    }

    public List<Container.MountPoint> getMounts() {
        return mounts;
    }

    /**
     * Container state information.
     */
    public static class ContainerState {
        @SerializedName("Status")
        private String status;

        @SerializedName("Running")
        private Boolean running;

        @SerializedName("Paused")
        private Boolean paused;

        @SerializedName("Restarting")
        private Boolean restarting;

        @SerializedName("OOMKilled")
        private Boolean oomKilled;

        @SerializedName("Dead")
        private Boolean dead;

        @SerializedName("Pid")
        private Integer pid;

        @SerializedName("ExitCode")
        private Integer exitCode;

        @SerializedName("Error")
        private String error;

        @SerializedName("StartedAt")
        private String startedAt;

        @SerializedName("FinishedAt")
        private String finishedAt;

        public String getStatus() {
            return status;
        }

        public Boolean getRunning() {
            return running;
        }

        public Boolean getPaused() {
            return paused;
        }

        public Boolean getRestarting() {
            return restarting;
        }

        public Boolean getOomKilled() {
            return oomKilled;
        }

        public Boolean getDead() {
            return dead;
        }

        public Integer getPid() {
            return pid;
        }

        public Integer getExitCode() {
            return exitCode;
        }

        public String getError() {
            return error;
        }

        public String getStartedAt() {
            return startedAt;
        }

        public String getFinishedAt() {
            return finishedAt;
        }
    }

    /**
     * Network settings for an inspected container.
     */
    public static class NetworkSettings {
        @SerializedName("Bridge")
        private String bridge;

        @SerializedName("SandboxID")
        private String sandboxId;

        @SerializedName("HairpinMode")
        private Boolean hairpinMode;

        @SerializedName("LinkLocalIPv6Address")
        private String linkLocalIPv6Address;

        @SerializedName("LinkLocalIPv6PrefixLen")
        private Integer linkLocalIPv6PrefixLen;

        @SerializedName("Ports")
        private Map<String, List<PortBinding>> ports;

        @SerializedName("SandboxKey")
        private String sandboxKey;

        @SerializedName("SecondaryIPAddresses")
        private List<Object> secondaryIPAddresses;

        @SerializedName("SecondaryIPv6Addresses")
        private List<Object> secondaryIPv6Addresses;

        @SerializedName("EndpointID")
        private String endpointId;

        @SerializedName("Gateway")
        private String gateway;

        @SerializedName("GlobalIPv6Address")
        private String globalIPv6Address;

        @SerializedName("GlobalIPv6PrefixLen")
        private Integer globalIPv6PrefixLen;

        @SerializedName("IPAddress")
        private String ipAddress;

        @SerializedName("IPPrefixLen")
        private Integer ipPrefixLen;

        @SerializedName("IPv6Gateway")
        private String ipv6Gateway;

        @SerializedName("MacAddress")
        private String macAddress;

        @SerializedName("Networks")
        private Map<String, Container.NetworkInfo> networks;

        public String getBridge() {
            return bridge;
        }

        public String getSandboxId() {
            return sandboxId;
        }

        public Boolean getHairpinMode() {
            return hairpinMode;
        }

        public Map<String, List<PortBinding>> getPorts() {
            return ports;
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

        public Map<String, Container.NetworkInfo> getNetworks() {
            return networks;
        }
    }

    @Override
    public String toString() {
        return "ContainerInspect{" +
                "id='" + (id != null ? id.substring(0, Math.min(12, id.length())) : "null") + '\'' +
                ", name='" + name + '\'' +
                ", state=" + (state != null ? state.status : "null") +
                ", image='" + image + '\'' +
                '}';
    }
}
