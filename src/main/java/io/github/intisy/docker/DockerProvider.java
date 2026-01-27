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
 * <p>
 * The base directory for storing Docker data can be configured using {@link #setBaseDirectory(Path)}
 * before creating any providers. By default, it uses {@code ~/.docker-java/}.
 *
 * @author Finn Birich
 */
public abstract class DockerProvider {
    private static final Path DEFAULT_DOCKER_DIR = Paths.get(System.getProperty("user.home"), ".docker-java");
    private static final String DEFAULT_WSL_BASE = ".docker-java";
    
    private static Path baseDirectory = DEFAULT_DOCKER_DIR;
    private static String wslBaseDirectory = DEFAULT_WSL_BASE;
    
    /**
     * Set the base directory for storing Docker data and instances.
     * This must be called before creating any DockerProvider instances.
     * <p>
     * Example:
     * <pre>{@code
     * DockerProvider.setBaseDirectory(Paths.get("/custom/path/docker-java"));
     * DockerProvider provider = DockerProvider.get();
     * }</pre>
     *
     * @param path The base directory path
     */
    public static void setBaseDirectory(Path path) {
        baseDirectory = path;
    }
    
    /**
     * Get the current base directory for storing Docker data.
     *
     * @return The base directory path
     */
    public static Path getBaseDirectory() {
        return baseDirectory;
    }
    
    /**
     * Set the base directory path for WSL2 (Windows Subsystem for Linux).
     * This is used when running Docker in WSL2 mode on Windows.
     * The path is relative to the WSL user's home directory.
     * <p>
     * Example:
     * <pre>{@code
     * DockerProvider.setWslBaseDirectory(".my-docker-data");
     * // Results in ~/. my-docker-data/ inside WSL2
     * }</pre>
     *
     * @param path The WSL base directory path (relative to home)
     */
    public static void setWslBaseDirectory(String path) {
        wslBaseDirectory = path;
    }
    
    /**
     * Get the WSL base directory path.
     *
     * @return The WSL base directory path (relative to home)
     */
    public static String getWslBaseDirectory() {
        return wslBaseDirectory;
    }
    
    /**
     * Reset the base directory to the default ({@code ~/.docker-java/}).
     */
    public static void resetBaseDirectory() {
        baseDirectory = DEFAULT_DOCKER_DIR;
        wslBaseDirectory = DEFAULT_WSL_BASE;
    }
    
    /**
     * @deprecated Use {@link #getBaseDirectory()} instead
     */
    @Deprecated
    protected static final Path DOCKER_DIR = DEFAULT_DOCKER_DIR;

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
