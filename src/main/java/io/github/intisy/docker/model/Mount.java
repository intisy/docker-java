package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

/**
 * Represents a mount point for a container.
 *
 * @author Finn Birich
 */
public class Mount {
    @SerializedName("Type")
    private String type;

    @SerializedName("Source")
    private String source;

    @SerializedName("Target")
    private String target;

    @SerializedName("ReadOnly")
    private Boolean readOnly;

    @SerializedName("Consistency")
    private String consistency;

    public Mount() {
    }

    public static Mount bind(String source, String target) {
        Mount mount = new Mount();
        mount.type = "bind";
        mount.source = source;
        mount.target = target;
        return mount;
    }

    public static Mount volume(String volumeName, String target) {
        Mount mount = new Mount();
        mount.type = "volume";
        mount.source = volumeName;
        mount.target = target;
        return mount;
    }

    public static Mount tmpfs(String target) {
        Mount mount = new Mount();
        mount.type = "tmpfs";
        mount.target = target;
        return mount;
    }

    public String getType() {
        return type;
    }

    public Mount setType(String type) {
        this.type = type;
        return this;
    }

    public String getSource() {
        return source;
    }

    public Mount setSource(String source) {
        this.source = source;
        return this;
    }

    public String getTarget() {
        return target;
    }

    public Mount setTarget(String target) {
        this.target = target;
        return this;
    }

    public Boolean getReadOnly() {
        return readOnly;
    }

    public Mount setReadOnly(Boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    public String getConsistency() {
        return consistency;
    }

    public Mount setConsistency(String consistency) {
        this.consistency = consistency;
        return this;
    }

    @Override
    public String toString() {
        return "Mount{" +
                "type='" + type + '\'' +
                ", source='" + source + '\'' +
                ", target='" + target + '\'' +
                ", readOnly=" + readOnly +
                '}';
    }
}
