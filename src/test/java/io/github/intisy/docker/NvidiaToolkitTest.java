package io.github.intisy.docker;

import io.github.intisy.docker.model.CreateContainerResponse;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * Tests for NVIDIA Container Toolkit detection and GPU container support.
 * Requires Windows with WSL2 and optionally an NVIDIA GPU.
 *
 * @author Finn Birich
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NvidiaToolkitTest {
    private static final Logger log = LoggerFactory.getLogger(NvidiaToolkitTest.class);

    private static WindowsDockerProvider provider;
    private static boolean isWindows;
    private static boolean hasWsl2;

    @BeforeAll
    static void setup() throws IOException, InterruptedException {
        isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        assumeTrue(isWindows, "Skipping: Not running on Windows");

        provider = new WindowsDockerProvider();

        try {
            provider.start();
            hasWsl2 = true;
        } catch (Exception e) {
            log.warn("Could not start WSL2 Docker: {}", e.getMessage());
            hasWsl2 = false;
        }
    }

    @AfterAll
    static void cleanup() {
        if (provider != null) {
            try {
                provider.stop();
            } catch (Exception e) {
                log.warn("Error stopping provider: {}", e.getMessage());
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("GPU detection does not throw")
    void testGpuDetectionDoesNotThrow() {
        assumeTrue(hasWsl2, "Skipping: WSL2 not available");

        boolean gpuAvailable = assertDoesNotThrow(() -> provider.isNvidiaGpuAvailable());
        log.info("NVIDIA GPU available: {}", gpuAvailable);
    }

    @Test
    @Order(2)
    @DisplayName("Toolkit detection does not throw")
    void testToolkitDetectionDoesNotThrow() {
        assumeTrue(hasWsl2, "Skipping: WSL2 not available");

        boolean installed = assertDoesNotThrow(() -> provider.isNvidiaContainerToolkitInstalled());
        log.info("NVIDIA Container Toolkit installed: {}", installed);
    }

    @Test
    @Order(3)
    @DisplayName("ensureNvidiaContainerToolkit installs if GPU present")
    void testEnsureNvidiaContainerToolkit() {
        assumeTrue(hasWsl2, "Skipping: WSL2 not available");

        boolean gpuAvailable = provider.isNvidiaGpuAvailable();
        boolean toolkitInstalledBefore = provider.isNvidiaContainerToolkitInstalled();

        if (!gpuAvailable) {
            assertDoesNotThrow(() -> provider.ensureNvidiaContainerToolkit());
            return;
        }

        if (toolkitInstalledBefore) {
            assertDoesNotThrow(() -> provider.ensureNvidiaContainerToolkit());
            return;
        }

        try {
            provider.ensureNvidiaContainerToolkit();
            assertTrue(provider.isNvidiaContainerToolkitInstalled(),
                    "NVIDIA Container Toolkit should be installed after ensureNvidiaContainerToolkit()");
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Passwordless sudo")) {
                assumeTrue(false, "Skipping: Passwordless sudo not configured");
            }
            fail("Failed to install NVIDIA Container Toolkit: " + e.getMessage());
        }
    }

    @Test
    @Order(4)
    @DisplayName("GPU container runs nvidia-smi successfully")
    void testGpuContainerCreation() {
        assumeTrue(hasWsl2, "Skipping: WSL2 not available");
        assumeTrue(provider.isNvidiaGpuAvailable(), "Skipping: No NVIDIA GPU available");
        assumeTrue(provider.isNvidiaContainerToolkitInstalled(), "Skipping: NVIDIA toolkit not installed");

        DockerClient client = provider.getClient();

        try {
            client.pullImage("nvidia/cuda:12.0.0-base-ubuntu22.04").exec(10, java.util.concurrent.TimeUnit.MINUTES);

            CreateContainerResponse response = client.createContainer("nvidia/cuda:12.0.0-base-ubuntu22.04")
                    .withName("nvidia-test-" + System.currentTimeMillis())
                    .withCmd("nvidia-smi")
                    .withHostConfig(new io.github.intisy.docker.model.HostConfig()
                            .addDeviceRequest(io.github.intisy.docker.model.DeviceRequest.requestAllGpus()))
                    .exec();

            String containerId = response.getId();

            client.startContainer(containerId).exec();
            client.waitContainer(containerId).exec();

            String logs = client.logs(containerId)
                    .withStdout(true)
                    .withStderr(true)
                    .exec();

            client.removeContainer(containerId).exec();

            assertTrue(logs.contains("NVIDIA") || logs.contains("GPU"),
                    "nvidia-smi output should contain GPU information");

        } catch (Exception e) {
            fail("GPU container test failed: " + e.getMessage());
        }
    }
}
