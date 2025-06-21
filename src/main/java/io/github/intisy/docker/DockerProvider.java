package io.github.intisy.docker;

import com.github.dockerjava.api.DockerClient;
import java.io.IOException;

/**
 * @author Finn Birich
 */
public interface DockerProvider {
    static DockerProvider get() {
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

    void ensureInstalled() throws IOException, InterruptedException;
    void start() throws IOException, InterruptedException;
    DockerClient getClient();
    void stop();
}