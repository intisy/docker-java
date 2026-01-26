package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * Represents a Docker volume.
 *
 * @author Finn Birich
 */
public class Volume {
    @SerializedName("Name")
    private String name;

    @SerializedName("Driver")
    private String driver;

    @SerializedName("Mountpoint")
    private String mountpoint;

    @SerializedName("CreatedAt")
    private String createdAt;

    @SerializedName("Status")
    private Map<String, Object> status;

    @SerializedName("Labels")
    private Map<String, String> labels;

    @SerializedName("Scope")
    private String scope;

    @SerializedName("Options")
    private Map<String, String> options;

    @SerializedName("UsageData")
    private UsageData usageData;

    public String getName() {
        return name;
    }

    public String getDriver() {
        return driver;
    }

    public String getMountpoint() {
        return mountpoint;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public Map<String, Object> getStatus() {
        return status;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public String getScope() {
        return scope;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public UsageData getUsageData() {
        return usageData;
    }

    /**
     * Volume usage data.
     */
    public static class UsageData {
        @SerializedName("Size")
        private Long size;

        @SerializedName("RefCount")
        private Integer refCount;

        public Long getSize() {
            return size;
        }

        public Integer getRefCount() {
            return refCount;
        }
    }

    @Override
    public String toString() {
        return "Volume{" +
                "name='" + name + '\'' +
                ", driver='" + driver + '\'' +
                ", mountpoint='" + mountpoint + '\'' +
                ", scope='" + scope + '\'' +
                '}';
    }
}
