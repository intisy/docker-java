package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Host configuration for a container.
 *
 * @author Finn Birich
 */
public class HostConfig {
    @SerializedName("Binds")
    private List<String> binds;

    @SerializedName("PortBindings")
    private Map<String, List<PortBinding>> portBindings;

    @SerializedName("PublishAllPorts")
    private Boolean publishAllPorts;

    @SerializedName("Privileged")
    private Boolean privileged;

    @SerializedName("NetworkMode")
    private String networkMode;

    @SerializedName("RestartPolicy")
    private RestartPolicy restartPolicy;

    @SerializedName("AutoRemove")
    private Boolean autoRemove;

    @SerializedName("Mounts")
    private List<Mount> mounts;

    @SerializedName("Memory")
    private Long memory;

    @SerializedName("MemorySwap")
    private Long memorySwap;

    @SerializedName("CpuShares")
    private Long cpuShares;

    @SerializedName("CpuPeriod")
    private Long cpuPeriod;

    @SerializedName("CpuQuota")
    private Long cpuQuota;

    @SerializedName("Dns")
    private List<String> dns;

    @SerializedName("DnsSearch")
    private List<String> dnsSearch;

    @SerializedName("ExtraHosts")
    private List<String> extraHosts;

    @SerializedName("VolumesFrom")
    private List<String> volumesFrom;

    @SerializedName("CapAdd")
    private List<String> capAdd;

    @SerializedName("CapDrop")
    private List<String> capDrop;

    @SerializedName("PidMode")
    private String pidMode;

    @SerializedName("IpcMode")
    private String ipcMode;

    @SerializedName("ShmSize")
    private Long shmSize;

    @SerializedName("DeviceRequests")
    private List<DeviceRequest> deviceRequests;

    @SerializedName("Devices")
    private List<DeviceMapping> devices;

    @SerializedName("Runtime")
    private String runtime;

    public HostConfig() {
    }

    public List<String> getBinds() {
        return binds;
    }

    public HostConfig setBinds(List<String> binds) {
        this.binds = binds;
        return this;
    }

    public HostConfig addBind(String bind) {
        if (this.binds == null) {
            this.binds = new ArrayList<>();
        }
        this.binds.add(bind);
        return this;
    }

    public Map<String, List<PortBinding>> getPortBindings() {
        return portBindings;
    }

    public HostConfig setPortBindings(Map<String, List<PortBinding>> portBindings) {
        this.portBindings = portBindings;
        return this;
    }

    public HostConfig addPortBinding(ExposedPort exposedPort, PortBinding binding) {
        if (this.portBindings == null) {
            this.portBindings = new HashMap<>();
        }
        this.portBindings.computeIfAbsent(exposedPort.toString(), k -> new ArrayList<>()).add(binding);
        return this;
    }

    public Boolean getPublishAllPorts() {
        return publishAllPorts;
    }

    public HostConfig setPublishAllPorts(Boolean publishAllPorts) {
        this.publishAllPorts = publishAllPorts;
        return this;
    }

    public Boolean getPrivileged() {
        return privileged;
    }

    public HostConfig setPrivileged(Boolean privileged) {
        this.privileged = privileged;
        return this;
    }

    public String getNetworkMode() {
        return networkMode;
    }

    public HostConfig setNetworkMode(String networkMode) {
        this.networkMode = networkMode;
        return this;
    }

    public RestartPolicy getRestartPolicy() {
        return restartPolicy;
    }

    public HostConfig setRestartPolicy(RestartPolicy restartPolicy) {
        this.restartPolicy = restartPolicy;
        return this;
    }

    public Boolean getAutoRemove() {
        return autoRemove;
    }

    public HostConfig setAutoRemove(Boolean autoRemove) {
        this.autoRemove = autoRemove;
        return this;
    }

    public List<Mount> getMounts() {
        return mounts;
    }

    public HostConfig setMounts(List<Mount> mounts) {
        this.mounts = mounts;
        return this;
    }

    public HostConfig addMount(Mount mount) {
        if (this.mounts == null) {
            this.mounts = new ArrayList<>();
        }
        this.mounts.add(mount);
        return this;
    }

    public Long getMemory() {
        return memory;
    }

    public HostConfig setMemory(Long memory) {
        this.memory = memory;
        return this;
    }

    public Long getMemorySwap() {
        return memorySwap;
    }

    public HostConfig setMemorySwap(Long memorySwap) {
        this.memorySwap = memorySwap;
        return this;
    }

    public Long getCpuShares() {
        return cpuShares;
    }

    public HostConfig setCpuShares(Long cpuShares) {
        this.cpuShares = cpuShares;
        return this;
    }

    public Long getCpuPeriod() {
        return cpuPeriod;
    }

    public HostConfig setCpuPeriod(Long cpuPeriod) {
        this.cpuPeriod = cpuPeriod;
        return this;
    }

    public Long getCpuQuota() {
        return cpuQuota;
    }

    public HostConfig setCpuQuota(Long cpuQuota) {
        this.cpuQuota = cpuQuota;
        return this;
    }

    public List<String> getDns() {
        return dns;
    }

    public HostConfig setDns(List<String> dns) {
        this.dns = dns;
        return this;
    }

    public List<String> getDnsSearch() {
        return dnsSearch;
    }

    public HostConfig setDnsSearch(List<String> dnsSearch) {
        this.dnsSearch = dnsSearch;
        return this;
    }

    public List<String> getExtraHosts() {
        return extraHosts;
    }

    public HostConfig setExtraHosts(List<String> extraHosts) {
        this.extraHosts = extraHosts;
        return this;
    }

    public HostConfig addExtraHost(String host) {
        if (this.extraHosts == null) {
            this.extraHosts = new ArrayList<>();
        }
        this.extraHosts.add(host);
        return this;
    }

    public List<String> getVolumesFrom() {
        return volumesFrom;
    }

    public HostConfig setVolumesFrom(List<String> volumesFrom) {
        this.volumesFrom = volumesFrom;
        return this;
    }

    public List<String> getCapAdd() {
        return capAdd;
    }

    public HostConfig setCapAdd(List<String> capAdd) {
        this.capAdd = capAdd;
        return this;
    }

    public List<String> getCapDrop() {
        return capDrop;
    }

    public HostConfig setCapDrop(List<String> capDrop) {
        this.capDrop = capDrop;
        return this;
    }

    public String getPidMode() {
        return pidMode;
    }

    public HostConfig setPidMode(String pidMode) {
        this.pidMode = pidMode;
        return this;
    }

    public String getIpcMode() {
        return ipcMode;
    }

    public HostConfig setIpcMode(String ipcMode) {
        this.ipcMode = ipcMode;
        return this;
    }

    public Long getShmSize() {
        return shmSize;
    }

    public HostConfig setShmSize(Long shmSize) {
        this.shmSize = shmSize;
        return this;
    }

    public List<DeviceRequest> getDeviceRequests() {
        return deviceRequests;
    }

    public HostConfig setDeviceRequests(List<DeviceRequest> deviceRequests) {
        this.deviceRequests = deviceRequests;
        return this;
    }

    public HostConfig addDeviceRequest(DeviceRequest deviceRequest) {
        if (this.deviceRequests == null) {
            this.deviceRequests = new ArrayList<>();
        }
        this.deviceRequests.add(deviceRequest);
        return this;
    }

    public List<DeviceMapping> getDevices() {
        return devices;
    }

    public HostConfig setDevices(List<DeviceMapping> devices) {
        this.devices = devices;
        return this;
    }

    public HostConfig addDevice(DeviceMapping device) {
        if (this.devices == null) {
            this.devices = new ArrayList<>();
        }
        this.devices.add(device);
        return this;
    }

    public String getRuntime() {
        return runtime;
    }

    public HostConfig setRuntime(String runtime) {
        this.runtime = runtime;
        return this;
    }

    /**
     * Restart policy for a container.
     */
    public static class RestartPolicy {
        @SerializedName("Name")
        private String name;

        @SerializedName("MaximumRetryCount")
        private Integer maximumRetryCount;

        public RestartPolicy() {
        }

        public static RestartPolicy noRestart() {
            RestartPolicy policy = new RestartPolicy();
            policy.name = "no";
            return policy;
        }

        public static RestartPolicy alwaysRestart() {
            RestartPolicy policy = new RestartPolicy();
            policy.name = "always";
            return policy;
        }

        public static RestartPolicy unlessStopped() {
            RestartPolicy policy = new RestartPolicy();
            policy.name = "unless-stopped";
            return policy;
        }

        public static RestartPolicy onFailure(int maxRetries) {
            RestartPolicy policy = new RestartPolicy();
            policy.name = "on-failure";
            policy.maximumRetryCount = maxRetries;
            return policy;
        }

        public String getName() {
            return name;
        }

        public Integer getMaximumRetryCount() {
            return maximumRetryCount;
        }
    }
}
