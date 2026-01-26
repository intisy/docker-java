package io.github.intisy.docker;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Abstract base class for platform-specific Docker providers.
 * Handles Docker daemon installation, startup, and provides a client for interacting with Docker.
 * <p>
 * Each provider instance is isolated and can run simultaneously with other instances.
 * Use {@link #getInstanceId()} to get the unique identifier for this instance.
 *
 * @author Finn Birich
 */
public abstract class DockerProvider {
    protected static final Path DOCKER_DIR = Paths.get(System.getProperty("user.home"), ".docker-java");

    /**
     * Get the appropriate DockerProvider for the current operating system.
     */
    public static DockerProvider get() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return new WindowsDockerProvider();
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            return new LinuxDockerProvider();
        } else if (os.contains("mac")) {
            return new MacDockerProvider();
        } else {
            throw new UnsupportedOperationException("Unsupported operating system: " + os);
        }
    }

    /**
     * Get the unique instance ID for this provider.
     * Each provider instance has a unique ID to allow multiple instances to run simultaneously.
     *
     * @return The unique instance identifier
     */
    public abstract String getInstanceId();

    /**
     * Start the Docker daemon.
     * This will always start a new managed Docker daemon instance.
     * The daemon will use isolated paths to avoid conflicts with Docker Desktop or other instances.
     */
    public abstract void start() throws IOException, InterruptedException;

    /**
     * Get a DockerClient for interacting with the Docker daemon.
     * The client can be used to manage containers, images, volumes, networks, etc.
     */
    public abstract DockerClient getClient();

    /**
     * Stop the Docker daemon if it was started by this provider.
     * This will also clean up the instance-specific directories.
     */
    public abstract void stop();

    /**
     * Ensure Docker is installed.
     * Downloads and installs Docker if necessary.
     */
    public abstract void ensureInstalled() throws IOException;
}
