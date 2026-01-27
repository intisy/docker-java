package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * Docker system information.
 *
 * @author Finn Birich
 */
public class SystemInfo {
    @SerializedName("ID")
    private String id;

    @SerializedName("Containers")
    private Integer containers;

    @SerializedName("ContainersRunning")
    private Integer containersRunning;

    @SerializedName("ContainersPaused")
    private Integer containersPaused;

    @SerializedName("ContainersStopped")
    private Integer containersStopped;

    @SerializedName("Images")
    private Integer images;

    @SerializedName("Driver")
    private String driver;

    @SerializedName("DriverStatus")
    private List<List<String>> driverStatus;

    @SerializedName("Plugins")
    private Object plugins;

    @SerializedName("MemoryLimit")
    private Boolean memoryLimit;

    @SerializedName("SwapLimit")
    private Boolean swapLimit;

    @SerializedName("KernelMemory")
    private Boolean kernelMemory;

    @SerializedName("CpuCfsPeriod")
    private Boolean cpuCfsPeriod;

    @SerializedName("CpuCfsQuota")
    private Boolean cpuCfsQuota;

    @SerializedName("CPUShares")
    private Boolean cpuShares;

    @SerializedName("CPUSet")
    private Boolean cpuSet;

    @SerializedName("IPv4Forwarding")
    private Boolean ipv4Forwarding;

    @SerializedName("BridgeNfIptables")
    private Boolean bridgeNfIptables;

    @SerializedName("BridgeNfIp6tables")
    private Boolean bridgeNfIp6tables;

    @SerializedName("Debug")
    private Boolean debug;

    @SerializedName("NFd")
    private Integer nFd;

    @SerializedName("OomKillDisable")
    private Boolean oomKillDisable;

    @SerializedName("NGoroutines")
    private Integer nGoroutines;

    @SerializedName("SystemTime")
    private String systemTime;

    @SerializedName("LoggingDriver")
    private String loggingDriver;

    @SerializedName("CgroupDriver")
    private String cgroupDriver;

    @SerializedName("NEventsListener")
    private Integer nEventsListener;

    @SerializedName("KernelVersion")
    private String kernelVersion;

    @SerializedName("OperatingSystem")
    private String operatingSystem;

    @SerializedName("OSType")
    private String osType;

    @SerializedName("Architecture")
    private String architecture;

    @SerializedName("NCPU")
    private Integer ncpu;

    @SerializedName("MemTotal")
    private Long memTotal;

    @SerializedName("DockerRootDir")
    private String dockerRootDir;

    @SerializedName("HttpProxy")
    private String httpProxy;

    @SerializedName("HttpsProxy")
    private String httpsProxy;

    @SerializedName("NoProxy")
    private String noProxy;

    @SerializedName("Name")
    private String name;

    @SerializedName("Labels")
    private List<String> labels;

    @SerializedName("ExperimentalBuild")
    private Boolean experimentalBuild;

    @SerializedName("ServerVersion")
    private String serverVersion;

    @SerializedName("Runtimes")
    private Map<String, Object> runtimes;

    @SerializedName("DefaultRuntime")
    private String defaultRuntime;

    @SerializedName("LiveRestoreEnabled")
    private Boolean liveRestoreEnabled;

    @SerializedName("Isolation")
    private String isolation;

    public String getId() {
        return id;
    }

    public Integer getContainers() {
        return containers;
    }

    public Integer getContainersRunning() {
        return containersRunning;
    }

    public Integer getContainersPaused() {
        return containersPaused;
    }

    public Integer getContainersStopped() {
        return containersStopped;
    }

    public Integer getImages() {
        return images;
    }

    public String getDriver() {
        return driver;
    }

    public Object getPlugins() {
        return plugins;
    }

    public Boolean getMemoryLimit() {
        return memoryLimit;
    }

    public Boolean getSwapLimit() {
        return swapLimit;
    }

    public Boolean getDebug() {
        return debug;
    }

    public String getKernelVersion() {
        return kernelVersion;
    }

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public String getOsType() {
        return osType;
    }

    public String getArchitecture() {
        return architecture;
    }

    public Integer getNcpu() {
        return ncpu;
    }

    public Long getMemTotal() {
        return memTotal;
    }

    public String getDockerRootDir() {
        return dockerRootDir;
    }

    public String getName() {
        return name;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public String getDefaultRuntime() {
        return defaultRuntime;
    }

    @Override
    public String toString() {
        return "SystemInfo{" +
                "name='" + name + '\'' +
                ", serverVersion='" + serverVersion + '\'' +
                ", containers=" + containers +
                ", images=" + images +
                ", osType='" + osType + '\'' +
                ", architecture='" + architecture + '\'' +
                '}';
    }
}
