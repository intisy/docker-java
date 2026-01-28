package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents a device request for container access to host devices (e.g., GPUs).
 *
 * @author Finn Birich
 */
public class DeviceRequest {
    @SerializedName("Driver")
    private String driver;

    @SerializedName("Count")
    private Integer count;

    @SerializedName("DeviceIDs")
    private List<String> deviceIDs;

    @SerializedName("Capabilities")
    private List<List<String>> capabilities;

    @SerializedName("Options")
    private Map<String, String> options;

    public DeviceRequest() {
    }

    /**
     * Create a device request for all NVIDIA GPUs.
     */
    public static DeviceRequest requestAllGpus() {
        return new DeviceRequest()
                .withDriver("nvidia")
                .withCount(-1)
                .withCapabilities(Collections.singletonList(Collections.singletonList("gpu")));
    }

    /**
     * Create a device request for a specific number of GPUs.
     */
    public static DeviceRequest requestGpus(int count) {
        return new DeviceRequest()
                .withDriver("nvidia")
                .withCount(count)
                .withCapabilities(Collections.singletonList(Collections.singletonList("gpu")));
    }

    /**
     * Create a device request for specific GPU device IDs.
     */
    public static DeviceRequest requestGpusByIds(List<String> deviceIds) {
        return new DeviceRequest()
                .withDriver("nvidia")
                .withDeviceIDs(deviceIds)
                .withCapabilities(Collections.singletonList(Collections.singletonList("gpu")));
    }

    public String getDriver() {
        return driver;
    }

    public DeviceRequest setDriver(String driver) {
        this.driver = driver;
        return this;
    }

    public DeviceRequest withDriver(String driver) {
        this.driver = driver;
        return this;
    }

    public Integer getCount() {
        return count;
    }

    public DeviceRequest setCount(Integer count) {
        this.count = count;
        return this;
    }

    public DeviceRequest withCount(Integer count) {
        this.count = count;
        return this;
    }

    public List<String> getDeviceIDs() {
        return deviceIDs;
    }

    public DeviceRequest setDeviceIDs(List<String> deviceIDs) {
        this.deviceIDs = deviceIDs;
        return this;
    }

    public DeviceRequest withDeviceIDs(List<String> deviceIDs) {
        this.deviceIDs = deviceIDs;
        return this;
    }

    public List<List<String>> getCapabilities() {
        return capabilities;
    }

    public DeviceRequest setCapabilities(List<List<String>> capabilities) {
        this.capabilities = capabilities;
        return this;
    }

    public DeviceRequest withCapabilities(List<List<String>> capabilities) {
        this.capabilities = capabilities;
        return this;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public DeviceRequest setOptions(Map<String, String> options) {
        this.options = options;
        return this;
    }

    public DeviceRequest withOptions(Map<String, String> options) {
        this.options = options;
        return this;
    }
}
