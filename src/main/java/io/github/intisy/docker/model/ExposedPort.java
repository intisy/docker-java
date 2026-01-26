package io.github.intisy.docker.model;

import java.util.Objects;

/**
 * Represents an exposed port in a container.
 *
 * @author Finn Birich
 */
public class ExposedPort {
    private final int port;
    private final String protocol;

    public ExposedPort(int port, String protocol) {
        this.port = port;
        this.protocol = protocol;
    }

    public static ExposedPort tcp(int port) {
        return new ExposedPort(port, "tcp");
    }

    public static ExposedPort udp(int port) {
        return new ExposedPort(port, "udp");
    }

    public static ExposedPort parse(String spec) {
        String[] parts = spec.split("/");
        int port = Integer.parseInt(parts[0]);
        String protocol = parts.length > 1 ? parts[1] : "tcp";
        return new ExposedPort(port, protocol);
    }

    public int getPort() {
        return port;
    }

    public String getProtocol() {
        return protocol;
    }

    @Override
    public String toString() {
        return port + "/" + protocol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExposedPort that = (ExposedPort) o;
        return port == that.port && Objects.equals(protocol, that.protocol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(port, protocol);
    }
}
