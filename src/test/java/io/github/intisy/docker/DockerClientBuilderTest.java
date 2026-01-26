package io.github.intisy.docker;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DockerClient.Builder (no Docker daemon required).
 *
 * @author Finn Birich
 */
public class DockerClientBuilderTest {

    @Test
    @DisplayName("Builder creates client with default host")
    void testBuilderDefaultHost() {
        DockerClient client = DockerClient.builder().build();
        assertNotNull(client, "Client should not be null");
    }

    @Test
    @DisplayName("Builder creates client with Unix socket host")
    void testBuilderUnixSocket() {
        DockerClient client = DockerClient.builder()
                .withHost("unix:///var/run/docker.sock")
                .build();
        assertNotNull(client, "Client should not be null");
    }

    @Test
    @DisplayName("Builder creates client with named pipe host")
    void testBuilderNamedPipe() {
        DockerClient client = DockerClient.builder()
                .withHost("npipe:////./pipe/docker_engine")
                .build();
        assertNotNull(client, "Client should not be null");
    }

    @Test
    @DisplayName("Builder creates client with TCP host")
    void testBuilderTcp() {
        DockerClient client = DockerClient.builder()
                .withHost("tcp://localhost:2375")
                .build();
        assertNotNull(client, "Client should not be null");
    }

    @Test
    @DisplayName("Builder accepts custom timeout")
    void testBuilderTimeout() {
        DockerClient client = DockerClient.builder()
                .withHost("unix:///var/run/docker.sock")
                .withTimeout(60000)
                .build();
        assertNotNull(client, "Client should not be null");
    }

    @Test
    @DisplayName("createDefault returns client")
    void testCreateDefault() {
        DockerClient client = DockerClient.createDefault();
        assertNotNull(client, "Client should not be null");
    }

    @Test
    @DisplayName("Client can be closed")
    void testClientClose() {
        DockerClient client = DockerClient.builder().build();
        assertDoesNotThrow(client::close, "Close should not throw");
    }

    @Test
    @DisplayName("Client returns command objects")
    void testClientCommands() {
        DockerClient client = DockerClient.builder().build();

        assertNotNull(client.listContainers(), "listContainers should return command");
        assertNotNull(client.createContainer("image"), "createContainer should return command");
        assertNotNull(client.startContainer("id"), "startContainer should return command");
        assertNotNull(client.stopContainer("id"), "stopContainer should return command");
        assertNotNull(client.removeContainer("id"), "removeContainer should return command");
        assertNotNull(client.inspectContainer("id"), "inspectContainer should return command");
        assertNotNull(client.logs("id"), "logs should return command");

        assertNotNull(client.listImages(), "listImages should return command");
        assertNotNull(client.pullImage("image"), "pullImage should return command");
        assertNotNull(client.removeImage("id"), "removeImage should return command");
        assertNotNull(client.inspectImage("id"), "inspectImage should return command");

        assertNotNull(client.listVolumes(), "listVolumes should return command");
        assertNotNull(client.createVolume(), "createVolume should return command");
        assertNotNull(client.removeVolume("name"), "removeVolume should return command");

        assertNotNull(client.listNetworks(), "listNetworks should return command");
        assertNotNull(client.createNetwork(), "createNetwork should return command");
        assertNotNull(client.removeNetwork("id"), "removeNetwork should return command");

        assertNotNull(client.ping(), "ping should return command");
        assertNotNull(client.info(), "info should return command");
        assertNotNull(client.version(), "version should return command");
    }
}
