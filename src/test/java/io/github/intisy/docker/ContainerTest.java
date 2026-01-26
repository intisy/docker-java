package io.github.intisy.docker;

import io.github.intisy.docker.command.network.CreateNetworkCmd;
import io.github.intisy.docker.command.volume.ListVolumesCmd;
import io.github.intisy.docker.WindowsDockerProvider;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.model.*;
import io.github.intisy.docker.transport.StreamCallback;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the Docker Java library.
 *
 * @author Finn Birich
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ContainerTest {
    private static final Logger log = LoggerFactory.getLogger(ContainerTest.class);
    
    private static DockerProvider dockerProvider;
    private static DockerClient dockerClient;
    private static String testContainerId;

    @BeforeAll
    static void setUp() throws Exception {
        log.info("Setting up Docker provider...");
        dockerProvider = DockerProvider.get();
        log.info("Using provider: {} (instance: {})", dockerProvider.getClass().getSimpleName(), dockerProvider.getInstanceId());
        
        dockerProvider.start();
        dockerClient = dockerProvider.getClient();
        
        // Try ping with retries since dockerd might still be initializing
        int maxRetries = 10;
        Exception lastException = null;
        for (int i = 0; i < maxRetries; i++) {
            try {
                Thread.sleep(1000); // Wait a bit before each attempt
                dockerClient.ping().execOrThrow();
                log.info("Docker daemon is ready (attempt {})", i + 1);
                lastException = null;
                break;
            } catch (Exception e) {
                lastException = e;
                log.debug("Ping attempt {} failed: {}", i + 1, e.getMessage());
            }
        }
        if (lastException != null) {
            log.error("Ping failed after {} attempts: {}", maxRetries, lastException.getMessage());
            // Dump dockerd log for diagnostics
            if (dockerProvider instanceof WindowsDockerProvider) {
                try {
                    ProcessBuilder pb = new ProcessBuilder("wsl", "-d", "Ubuntu", "--", "cat", "/tmp/docker-java-" + dockerProvider.getInstanceId() + ".log");
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                        String line;
                        log.error("=== DOCKERD LOG START ===");
                        while ((line = reader.readLine()) != null) {
                            log.error("dockerd: {}", line);
                        }
                        log.error("=== DOCKERD LOG END ===");
                    }
                    p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.error("Failed to read dockerd log: {}", e.getMessage());
                }
            }
            throw lastException;
        }
    }

    @AfterAll
    static void tearDown() {
        log.info("Tearing down Docker provider...");
        if (testContainerId != null) {
            try {
                dockerClient.removeContainer(testContainerId).withForce(true).exec();
            } catch (Exception e) {
                log.warn("Failed to remove test container: {}", e.getMessage());
            }
        }
        if (dockerProvider != null) {
            dockerProvider.stop();
        }
    }

    // ==================== System Tests ====================

    @Test
    @Order(1)
    @DisplayName("Ping Docker daemon")
    void testPing() {
        boolean result = dockerClient.ping().exec();
        assertTrue(result, "Ping should succeed");
    }

    @Test
    @Order(2)
    @DisplayName("Get Docker version")
    void testVersion() {
        Version version = dockerClient.version().exec();
        
        assertNotNull(version, "Version should not be null");
        assertNotNull(version.getVersion(), "Docker version should not be null");
        assertNotNull(version.getApiVersion(), "API version should not be null");
        
        log.info("Docker version: {}, API version: {}", version.getVersion(), version.getApiVersion());
    }

    @Test
    @Order(3)
    @DisplayName("Get system info")
    void testInfo() {
        SystemInfo info = dockerClient.info().exec();
        
        assertNotNull(info, "SystemInfo should not be null");
        assertNotNull(info.getServerVersion(), "Server version should not be null");
        assertTrue(info.getContainers() >= 0, "Container count should be non-negative");
        assertTrue(info.getImages() >= 0, "Image count should be non-negative");
        
        log.info("Server: {}, Containers: {}, Images: {}", 
                info.getServerVersion(), info.getContainers(), info.getImages());
    }

    // ==================== Image Tests ====================

    @Test
    @Order(10)
    @DisplayName("List images")
    void testListImages() {
        List<Image> images = dockerClient.listImages().exec();
        
        assertNotNull(images, "Images list should not be null");
        log.info("Found {} images", images.size());
    }

    @Test
    @Order(11)
    @DisplayName("Pull image with progress")
    void testPullImage() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean hasProgress = new AtomicBoolean(false);

        dockerClient.pullImage("alpine:latest").exec(new StreamCallback<PullResponse>() {
            @Override
            public void onNext(PullResponse item) {
                hasProgress.set(true);
                log.debug("Pull progress: {}", item);
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Pull error", throwable);
                latch.countDown();
            }

            @Override
            public void onComplete() {
                completed.set(true);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.MINUTES), "Pull should complete within timeout");
        assertTrue(completed.get(), "Pull should complete successfully");
    }

    @Test
    @Order(12)
    @DisplayName("Inspect image")
    void testInspectImage() {
        ImageInspect inspect = dockerClient.inspectImage("alpine:latest").exec();
        
        assertNotNull(inspect, "ImageInspect should not be null");
        assertNotNull(inspect.getId(), "Image ID should not be null");
        
        log.info("Image ID: {}, Size: {} bytes", inspect.getId(), inspect.getSize());
    }

    // ==================== Container Tests ====================

    @Test
    @Order(20)
    @DisplayName("Create container")
    void testCreateContainer() {
        CreateContainerResponse response = dockerClient.createContainer("alpine:latest")
                .withName("docker-java-test-" + System.currentTimeMillis())
                .withCmd("sleep", "300")
                .withLabel("test", "docker-java")
                .exec();

        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getId(), "Container ID should not be null");
        
        testContainerId = response.getId();
        log.info("Created container: {}", testContainerId);
    }

    @Test
    @Order(21)
    @DisplayName("Start container")
    void testStartContainer() {
        assertNotNull(testContainerId, "Container ID should be set from previous test");
        
        assertDoesNotThrow(() -> dockerClient.startContainer(testContainerId).exec());
        
        ContainerInspect inspect = dockerClient.inspectContainer(testContainerId).exec();
        assertTrue(inspect.getState().getRunning(), "Container should be running");
        
        log.info("Container {} is running", testContainerId.substring(0, 12));
    }

    @Test
    @Order(22)
    @DisplayName("List containers")
    void testListContainers() {
        List<Container> containers = dockerClient.listContainers()
                .withShowAll(true)
                .withLabelFilter("test=docker-java")
                .exec();

        assertNotNull(containers, "Containers list should not be null");
        assertTrue(containers.size() >= 1, "Should have at least one test container");
        
        log.info("Found {} containers with test label", containers.size());
    }

    @Test
    @Order(23)
    @DisplayName("Inspect container")
    void testInspectContainer() {
        assertNotNull(testContainerId, "Container ID should be set");
        
        ContainerInspect inspect = dockerClient.inspectContainer(testContainerId).exec();
        
        assertNotNull(inspect, "Inspect should not be null");
        assertEquals(testContainerId, inspect.getId(), "Container ID should match");
        assertNotNull(inspect.getState(), "State should not be null");
        
        log.info("Container state: {}", inspect.getState().getStatus());
    }

    @Test
    @Order(24)
    @DisplayName("Execute command in container")
    void testExecInContainer() {
        assertNotNull(testContainerId, "Container ID should be set");
        
        ExecCreateResponse execResponse = dockerClient.execCreate(testContainerId)
                .withCmd("echo", "hello from docker-java")
                .withAttachStdout(true)
                .exec();

        assertNotNull(execResponse, "Exec response should not be null");
        assertNotNull(execResponse.getId(), "Exec ID should not be null");
        
        log.info("Created exec instance: {}", execResponse.getId());
    }

    @Test
    @Order(25)
    @DisplayName("Get container logs")
    void testContainerLogs() {
        assertNotNull(testContainerId, "Container ID should be set");
        
        String logs = dockerClient.logs(testContainerId)
                .withStdout(true)
                .withStderr(true)
                .withTail(10)
                .exec();

        assertNotNull(logs, "Logs should not be null");
        log.info("Container logs length: {} chars", logs.length());
    }

    @Test
    @Order(26)
    @DisplayName("Stop container")
    void testStopContainer() {
        assertNotNull(testContainerId, "Container ID should be set");
        
        assertDoesNotThrow(() -> dockerClient.stopContainer(testContainerId).withTimeout(5).exec());
        
        ContainerInspect inspect = dockerClient.inspectContainer(testContainerId).exec();
        assertFalse(inspect.getState().getRunning(), "Container should not be running");
        
        log.info("Container {} stopped", testContainerId.substring(0, 12));
    }

    @Test
    @Order(27)
    @DisplayName("Remove container")
    void testRemoveContainer() {
        assertNotNull(testContainerId, "Container ID should be set");
        
        assertDoesNotThrow(() -> dockerClient.removeContainer(testContainerId).exec());
        
        assertThrows(NotFoundException.class, () -> 
                dockerClient.inspectContainer(testContainerId).exec());
        
        log.info("Container {} removed", testContainerId.substring(0, 12));
        testContainerId = null;
    }

    // ==================== Volume Tests ====================

    @Test
    @Order(30)
    @DisplayName("Create and remove volume")
    void testVolumeLifecycle() {
        String volumeName = "docker-java-test-vol-" + System.currentTimeMillis();
        
        Volume volume = dockerClient.createVolume()
                .withName(volumeName)
                .withLabel("test", "docker-java")
                .exec();

        assertNotNull(volume, "Volume should not be null");
        assertEquals(volumeName, volume.getName(), "Volume name should match");
        
        log.info("Created volume: {}", volumeName);

        Volume inspected = dockerClient.inspectVolume(volumeName).exec();
        assertNotNull(inspected, "Inspected volume should not be null");
        
        assertDoesNotThrow(() -> dockerClient.removeVolume(volumeName).exec());
        log.info("Removed volume: {}", volumeName);
    }

    @Test
    @Order(31)
    @DisplayName("List volumes")
    void testListVolumes() {
        ListVolumesCmd.VolumesResponse response = dockerClient.listVolumes().exec();
        
        assertNotNull(response, "Response should not be null");
        log.info("Found {} volumes", response.getVolumes() != null ? response.getVolumes().size() : 0);
    }

    // ==================== Network Tests ====================

    @Test
    @Order(40)
    @DisplayName("Create and remove network")
    void testNetworkLifecycle() {
        String networkName = "docker-java-test-net-" + System.currentTimeMillis();
        
        CreateNetworkCmd.CreateNetworkResponse response = dockerClient.createNetwork()
                .withName(networkName)
                .withDriver("bridge")
                .withLabel("test", "docker-java")
                .exec();

        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getId(), "Network ID should not be null");
        
        log.info("Created network: {} ({})", networkName, response.getId());

        Network inspected = dockerClient.inspectNetwork(response.getId()).exec();
        assertNotNull(inspected, "Inspected network should not be null");
        assertEquals(networkName, inspected.getName(), "Network name should match");
        
        assertDoesNotThrow(() -> dockerClient.removeNetwork(response.getId()).exec());
        log.info("Removed network: {}", networkName);
    }

    @Test
    @Order(41)
    @DisplayName("List networks")
    void testListNetworks() {
        List<Network> networks = dockerClient.listNetworks().exec();
        
        assertNotNull(networks, "Networks should not be null");
        assertTrue(networks.size() >= 1, "Should have at least one network (bridge)");
        
        log.info("Found {} networks", networks.size());
    }

    // ==================== Exception Tests ====================

    @Test
    @Order(50)
    @DisplayName("NotFoundException for non-existent container")
    void testNotFoundExceptionContainer() {
        assertThrows(NotFoundException.class, () -> 
                dockerClient.inspectContainer("nonexistent-container-12345").exec());
    }

    @Test
    @Order(51)
    @DisplayName("NotFoundException for non-existent image")
    void testNotFoundExceptionImage() {
        assertThrows(NotFoundException.class, () -> 
                dockerClient.inspectImage("nonexistent/image:tag").exec());
    }

    @Test
    @Order(52)
    @DisplayName("NotFoundException for non-existent volume")
    void testNotFoundExceptionVolume() {
        assertThrows(NotFoundException.class, () -> 
                dockerClient.inspectVolume("nonexistent-volume-12345").exec());
    }

    @Test
    @Order(53)
    @DisplayName("NotFoundException for non-existent network")
    void testNotFoundExceptionNetwork() {
        assertThrows(NotFoundException.class, () -> 
                dockerClient.inspectNetwork("nonexistent-network-12345").exec());
    }
}
