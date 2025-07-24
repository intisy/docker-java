package io.github.intisy.docker;

import com.github.dockerjava.api.DockerClient;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Finn Birich
 */
public abstract class DockerProvider {
    protected static final Path DOCKER_DIR = Paths.get(System.getProperty("user.home"), ".docker-java");
    static DockerProvider get() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return new WindowsDockerProvider();
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            return new LinuxDockerProvider();
//        } else if (os.contains("mac")) {
//            return new MacDockerProvider();
        } else {
            throw new UnsupportedOperationException("Unsupported operating system: " + os);
        }
    }

    public abstract void start() throws IOException, InterruptedException;
    public abstract DockerClient getClient();
    public abstract void stop();
    public abstract void ensureInstalled() throws IOException;
}