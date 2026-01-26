package io.github.intisy.docker;

import io.github.intisy.docker.command.container.*;
import io.github.intisy.docker.command.image.*;
import io.github.intisy.docker.command.network.*;
import io.github.intisy.docker.command.system.*;
import io.github.intisy.docker.command.volume.*;
import io.github.intisy.docker.transport.DockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;

/**
 * Docker client for communicating with the Docker daemon.
 * <p>
 * Example usage:
 * <pre>{@code
 * DockerClient client = DockerClient.builder()
 *     .withHost("unix:///var/run/docker.sock")
 *     .build();
 *
 * // List containers
 * List<Container> containers = client.listContainers().withShowAll(true).exec();
 *
 * // Pull and run a container
 * client.pullImage("nginx:alpine").exec(5, TimeUnit.MINUTES);
 * CreateContainerResponse response = client.createContainer("nginx:alpine")
 *     .withName("my-nginx")
 *     .exec();
 * client.startContainer(response.getId()).exec();
 *
 * // Clean up
 * client.stopContainer(response.getId()).exec();
 * client.removeContainer(response.getId()).exec();
 * }</pre>
 *
 * @author Finn Birich
 */
public class DockerClient implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(DockerClient.class);
    
    private final DockerHttpClient httpClient;

    private DockerClient(DockerHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static DockerClient createDefault() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return builder().withHost("npipe:////./pipe/docker_engine").build();
        } else {
            return builder().withHost("unix:///var/run/docker.sock").build();
        }
    }

    // ==================== Container Commands ====================

    public ListContainersCmd listContainers() {
        return new ListContainersCmd(httpClient);
    }

    public CreateContainerCmd createContainer(String image) {
        return new CreateContainerCmd(httpClient, image);
    }

    public StartContainerCmd startContainer(String containerId) {
        return new StartContainerCmd(httpClient, containerId);
    }

    public StopContainerCmd stopContainer(String containerId) {
        return new StopContainerCmd(httpClient, containerId);
    }

    public RestartContainerCmd restartContainer(String containerId) {
        return new RestartContainerCmd(httpClient, containerId);
    }

    public KillContainerCmd killContainer(String containerId) {
        return new KillContainerCmd(httpClient, containerId);
    }

    public PauseContainerCmd pauseContainer(String containerId) {
        return new PauseContainerCmd(httpClient, containerId);
    }

    public UnpauseContainerCmd unpauseContainer(String containerId) {
        return new UnpauseContainerCmd(httpClient, containerId);
    }

    public RemoveContainerCmd removeContainer(String containerId) {
        return new RemoveContainerCmd(httpClient, containerId);
    }

    public InspectContainerCmd inspectContainer(String containerId) {
        return new InspectContainerCmd(httpClient, containerId);
    }

    public LogsContainerCmd logs(String containerId) {
        return new LogsContainerCmd(httpClient, containerId);
    }

    public WaitContainerCmd waitContainer(String containerId) {
        return new WaitContainerCmd(httpClient, containerId);
    }

    public ExecCreateCmd execCreate(String containerId) {
        return new ExecCreateCmd(httpClient, containerId);
    }

    public ExecStartCmd execStart(String execId) {
        return new ExecStartCmd(httpClient, execId);
    }

    public ExecInspectCmd execInspect(String execId) {
        return new ExecInspectCmd(httpClient, execId);
    }

    // ==================== Image Commands ====================

    public ListImagesCmd listImages() {
        return new ListImagesCmd(httpClient);
    }

    public PullImageCmd pullImage(String image) {
        return new PullImageCmd(httpClient, image);
    }

    public RemoveImageCmd removeImage(String imageId) {
        return new RemoveImageCmd(httpClient, imageId);
    }

    public InspectImageCmd inspectImage(String imageId) {
        return new InspectImageCmd(httpClient, imageId);
    }

    public TagImageCmd tagImage(String imageId) {
        return new TagImageCmd(httpClient, imageId);
    }

    public BuildImageCmd buildImage() {
        return new BuildImageCmd(httpClient);
    }

    // ==================== Volume Commands ====================

    public ListVolumesCmd listVolumes() {
        return new ListVolumesCmd(httpClient);
    }

    public CreateVolumeCmd createVolume() {
        return new CreateVolumeCmd(httpClient);
    }

    public RemoveVolumeCmd removeVolume(String volumeName) {
        return new RemoveVolumeCmd(httpClient, volumeName);
    }

    public InspectVolumeCmd inspectVolume(String volumeName) {
        return new InspectVolumeCmd(httpClient, volumeName);
    }

    // ==================== Network Commands ====================

    public ListNetworksCmd listNetworks() {
        return new ListNetworksCmd(httpClient);
    }

    public CreateNetworkCmd createNetwork() {
        return new CreateNetworkCmd(httpClient);
    }

    public RemoveNetworkCmd removeNetwork(String networkId) {
        return new RemoveNetworkCmd(httpClient, networkId);
    }

    public InspectNetworkCmd inspectNetwork(String networkId) {
        return new InspectNetworkCmd(httpClient, networkId);
    }

    public ConnectNetworkCmd connectNetwork(String networkId) {
        return new ConnectNetworkCmd(httpClient, networkId);
    }

    public DisconnectNetworkCmd disconnectNetwork(String networkId) {
        return new DisconnectNetworkCmd(httpClient, networkId);
    }

    // ==================== System Commands ====================

    public PingCmd ping() {
        return new PingCmd(httpClient);
    }

    public InfoCmd info() {
        return new InfoCmd(httpClient);
    }

    public VersionCmd version() {
        return new VersionCmd(httpClient);
    }

    @Override
    public void close() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    public static class Builder {
        private String dockerHost;
        private int timeout = 30000;

        private Builder() {
        }

        public Builder withHost(String dockerHost) {
            this.dockerHost = dockerHost;
            return this;
        }

        public Builder withTimeout(int timeoutMs) {
            this.timeout = timeoutMs;
            return this;
        }

        public DockerClient build() {
            if (dockerHost == null) {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    dockerHost = "npipe:////./pipe/docker_engine";
                } else {
                    dockerHost = "unix:///var/run/docker.sock";
                }
            }
            log.debug("Building DockerClient for host: {}", dockerHost);
            DockerHttpClient httpClient = new DockerHttpClient(dockerHost, timeout);
            return new DockerClient(httpClient);
        }
    }
}
