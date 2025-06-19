package io.github.intisy.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;

import java.util.concurrent.TimeUnit;

/**
 * @author Finn Birich
 */
public class EmbeddedDockerManager {

    private final DockerProvider provider;

    public EmbeddedDockerManager() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("nix") || osName.contains("nux")) {
            this.provider = new LinuxDockerProvider();
        } else if (osName.contains("win")) {
            this.provider = new WindowsDockerProvider();
        } else if (osName.contains("mac")) {
            throw new UnsupportedOperationException("macOS is not yet supported.");
        } else {
            throw new UnsupportedOperationException("Unsupported Operating System: " + osName);
        }
    }

    public void initialize() throws Exception {
        provider.ensureInstalled();
        provider.start();
    }

    public void pullAndRunContainer(String imageName, int hostPort, int containerPort) throws InterruptedException {
        DockerClient client = provider.getClient();
        client.pullImageCmd(imageName)
                .withTag("latest")
                .exec(new PullImageResultCallback())
                .awaitCompletion(5, TimeUnit.MINUTES);

        ExposedPort tcpContainerPort = ExposedPort.tcp(containerPort);
        Ports portBindings = new Ports();
        portBindings.bind(tcpContainerPort, Ports.Binding.bindPort(hostPort));

        String containerId = client.createContainerCmd(imageName + ":latest")
                .withHostConfig(new HostConfig().withPortBindings(portBindings))
                .withExposedPorts(tcpContainerPort)
                .exec()
                .getId();

        client.startContainerCmd(containerId).exec();
        System.out.printf("Started container %s. Port %d on host is mapped to %d in container.%n",
                containerId.substring(0, 12), hostPort, containerPort);
    }

    public void shutdown() {
        provider.stop();
    }

    public static void main(String[] args) {
        EmbeddedDockerManager manager = new EmbeddedDockerManager();
        try {
            manager.initialize();
            manager.pullAndRunContainer("nginx:alpine", 8080, 80);
            System.out.println("Application is running. Press Ctrl+C to stop.");
            Runtime.getRuntime().addShutdownHook(new Thread(manager::shutdown));

        } catch (Exception e) {
            System.err.println("A critical error occurred:");
            e.printStackTrace();
            manager.shutdown();
        }
    }
}