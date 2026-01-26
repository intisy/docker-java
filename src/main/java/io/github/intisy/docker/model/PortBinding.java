package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

/**
 * Represents a port binding configuration.
 *
 * @author Finn Birich
 */
public class PortBinding {
    @SerializedName("HostIp")
    private String hostIp;

    @SerializedName("HostPort")
    private String hostPort;

    public PortBinding() {
    }

    public PortBinding(String hostPort) {
        this.hostPort = hostPort;
    }

    public PortBinding(String hostIp, String hostPort) {
        this.hostIp = hostIp;
        this.hostPort = hostPort;
    }

    public static PortBinding empty() {
        return new PortBinding();
    }

    public static PortBinding of(int hostPort) {
        return new PortBinding(String.valueOf(hostPort));
    }

    public static PortBinding of(String hostIp, int hostPort) {
        return new PortBinding(hostIp, String.valueOf(hostPort));
    }

    public String getHostIp() {
        return hostIp;
    }

    public void setHostIp(String hostIp) {
        this.hostIp = hostIp;
    }

    public String getHostPort() {
        return hostPort;
    }

    public void setHostPort(String hostPort) {
        this.hostPort = hostPort;
    }

    public int getHostPortAsInt() {
        return hostPort != null ? Integer.parseInt(hostPort) : 0;
    }

    @Override
    public String toString() {
        if (hostIp != null && !hostIp.isEmpty()) {
            return hostIp + ":" + hostPort;
        }
        return hostPort != null ? hostPort : "";
    }
}
