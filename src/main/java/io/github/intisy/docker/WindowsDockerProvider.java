package io.github.intisy.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PingCmd;
import com.github.dockerjava.core.DockerClientBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Finn Birich
 */
public class WindowsDockerProvider implements DockerProvider {
    private static final Path DOCKER_DESKTOP_PATH = Paths.get("C:", "Program Files", "Docker", "Docker", "Docker Desktop.exe");

    @Override
    public void ensureInstalled() {
        if (Files.exists(DOCKER_DESKTOP_PATH)) {
            return;
        }
        System.out.println("Docker Desktop not found. Please download and install Docker Desktop for Windows from: https://www.docker.com/products/docker-desktop");
        throw new UnsupportedOperationException("Automatic installation on Windows is not supported. Please install Docker Desktop manually.");
    }

    @Override
    public void start() {
        try (PingCmd pingCmd = getClient().pingCmd()) {
            pingCmd.exec();
        } catch (Exception e) {
            System.err.println("Could not connect to Docker Desktop. Please ensure it is running.");
            throw new RuntimeException("Docker Desktop is not running.", e);
        }
    }

    @Override
    public DockerClient getClient() {
        return DockerClientBuilder.getInstance().build();
    }

    @Override
    public void stop() {}
}