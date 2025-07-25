package io.github.intisy.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

/**
 * @author Finn Birich
 */
public class ContainerTest {
    private final DockerProvider dockerProvider;
    private DockerClient dockerClient;
    private String containerId;

    public ContainerTest() {
        dockerProvider = DockerProvider.get();
    }

    public void initialize() throws Exception {
        dockerProvider.ensureInstalled();
        dockerProvider.start();
        this.dockerClient = dockerProvider.getClient();
    }

    public void pullAndRunContainer(String imageName, int containerPort) {
        try {
            String containerName = "docker-java-nginx-test";
            try {
                dockerClient.inspectContainerCmd(containerName).exec();
                System.out.println("Found existing container " + containerName + ". Removing it...");
                dockerClient.removeContainerCmd(containerName).withForce(true).exec();
            } catch (com.github.dockerjava.api.exception.NotFoundException ignored) {}

            System.out.println("Pulling image: " + imageName);
            dockerClient.pullImageCmd(imageName)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion(5, TimeUnit.MINUTES);

            ExposedPort tcpContainerPort = ExposedPort.tcp(containerPort);
            Ports portBindings = new Ports();
            portBindings.bind(tcpContainerPort, Ports.Binding.empty());

            this.containerId = dockerClient.createContainerCmd(imageName)
                    .withName(containerName)
                    .withHostConfig(new HostConfig().withPortBindings(portBindings))
                    .withExposedPorts(tcpContainerPort)
                    .exec()
                    .getId();

            dockerClient.startContainerCmd(this.containerId).exec();

            InspectContainerResponse inspectResponse = dockerClient.inspectContainerCmd(this.containerId).exec();
            Ports.Binding[] bindings = inspectResponse.getNetworkSettings().getPorts().getBindings().get(tcpContainerPort);
            if (bindings == null || bindings.length == 0) {
                throw new RuntimeException("Port bindings not found for container " + this.containerId);
            }
            int assignedHostPort = Integer.parseInt(bindings[0].getHostPortSpec());

            System.out.printf("Started container %s (%s). Port %d on host is mapped to %d in container.%n",
                    containerName, this.containerId.substring(0, 12), assignedHostPort, containerPort);
        } catch (Exception e) {
            System.err.println("A critical error occurred:");
            throw new RuntimeException(e);
        }
    }

    public void stopAndRemoveContainer() {
        if (containerId != null) {
            try {
                System.out.println("Attempting to stop and remove container " + containerId.substring(0, 12));
                dockerClient.stopContainerCmd(containerId).exec();
                dockerClient.removeContainerCmd(containerId).exec();
                System.out.println("Successfully removed container " + containerId.substring(0, 12));
            } catch (NotModifiedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void shutdown() {
        stopAndRemoveContainer();
        dockerProvider.stop();
    }

    public static void main(String[] args) {
        ContainerTest test = new ContainerTest();
        test.start();
    }

    @Test
    public void start() {
        System.out.println("Starting container test on OS: " + System.getProperty("os.name"));
        try {
            initialize();
            pullAndRunContainer("nginx:alpine", 80);
            System.out.println("Container started successfully. The application will now shut down.");
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        } catch (Exception e) {
            System.err.println("A critical error occurred:");
            shutdown();
            throw new RuntimeException(e);
        }
    }
}
