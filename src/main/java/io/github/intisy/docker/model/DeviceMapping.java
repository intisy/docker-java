package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

/**
 * @author Finn Birich
 */
public class DeviceMapping {
    @SerializedName("PathOnHost")
    private String pathOnHost;

    @SerializedName("PathInContainer")
    private String pathInContainer;

    @SerializedName("CgroupPermissions")
    private String cgroupPermissions;

    public DeviceMapping() {
    }

    public DeviceMapping(String pathOnHost, String pathInContainer, String cgroupPermissions) {
        this.pathOnHost = pathOnHost;
        this.pathInContainer = pathInContainer;
        this.cgroupPermissions = cgroupPermissions;
    }

    /**
     * Create a device mapping with read-write-mknod permissions.
      *
      * @param pathOnHost the device path on the host
      * @param pathInContainer the device path in the container
      * @return the device mapping
     */
    public static DeviceMapping of(String pathOnHost, String pathInContainer) {
        return new DeviceMapping(pathOnHost, pathInContainer, "rwm");
    }

    public String getPathOnHost() {
        return pathOnHost;
    }

    public DeviceMapping setPathOnHost(String pathOnHost) {
        this.pathOnHost = pathOnHost;
        return this;
    }

    public String getPathInContainer() {
        return pathInContainer;
    }

    public DeviceMapping setPathInContainer(String pathInContainer) {
        this.pathInContainer = pathInContainer;
        return this;
    }

    public String getCgroupPermissions() {
        return cgroupPermissions;
    }

    public DeviceMapping setCgroupPermissions(String cgroupPermissions) {
        this.cgroupPermissions = cgroupPermissions;
        return this;
    }
}
