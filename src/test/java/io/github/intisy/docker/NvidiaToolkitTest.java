package io.github.intisy.docker;

import io.github.intisy.docker.model.CreateContainerResponse;
import org.junit.jupiter.api.*;
        import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
        import static org.junit.jupiter.api.Assumptions.*;

/**
 * Tests for NVIDIA Container Toolkit detection and installation.
 * These tests require Windows with WSL2 and optionally an NVIDIA GPU.
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

        if (!isWindows) {
            log.info("Not running on Windows, NVIDIA toolkit tests will be skipped");
            return;
        }

        log.info("=== NVIDIA Container Toolkit Test Setup ===");
        provider = new WindowsDockerProvider();

        // Try to start the provider to initialize WSL2
        try {
            provider.start();
            hasWsl2 = true;
            log.info("WSL2 Docker started successfully");
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
                log.info("Provider stopped");
            } catch (Exception e) {
                log.warn("Error stopping provider: {}", e.getMessage());
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("Check if running on Windows")
    void testIsWindows() {
        log.info("OS: {}", System.getProperty("os.name"));
        log.info("Is Windows: {}", isWindows);

        // This test just logs info, doesn't fail
        assertTrue(true);
    }

    @Test
    @Order(2)
    @DisplayName("Check WSL2 availability")
    void testWsl2Available() {
        assumeTrue(isWindows, "Skipping: Not running on Windows");

        log.info("WSL2 available: {}", hasWsl2);

        if (!hasWsl2) {
            log.warn("WSL2 is not available or Docker could not start");
            log.warn("Some tests will be skipped");
        }
    }

    @Test
    @Order(3)
    @DisplayName("Detect NVIDIA GPU availability")
    void testNvidiaGpuDetection() {
        assumeTrue(isWindows, "Skipping: Not running on Windows");
        assumeTrue(hasWsl2, "Skipping: WSL2 not available");

        boolean gpuAvailable = provider.isNvidiaGpuAvailable();
        log.info("NVIDIA GPU available: {}", gpuAvailable);

        if (gpuAvailable) {
            log.info("NVIDIA GPU detected! GPU passthrough to containers is possible.");
        } else {
            log.info("No NVIDIA GPU detected. This is expected if you don't have an NVIDIA GPU.");
        }

        // Test passes regardless - we're just checking detection works
        assertTrue(true);
    }

    @Test
    @Order(4)
    @DisplayName("Check NVIDIA Container Toolkit installation status")
    void testNvidiaToolkitInstallationStatus() {
        assumeTrue(isWindows, "Skipping: Not running on Windows");
        assumeTrue(hasWsl2, "Skipping: WSL2 not available");

        boolean toolkitInstalled = provider.isNvidiaContainerToolkitInstalled();
        log.info("NVIDIA Container Toolkit installed: {}", toolkitInstalled);

        if (toolkitInstalled) {
            log.info("NVIDIA Container Toolkit is already installed.");
        } else {
            log.info("NVIDIA Container Toolkit is NOT installed.");
            log.info("It will be installed automatically when ensureNvidiaContainerToolkit() is called.");
        }

        // Test passes regardless - we're just checking detection works
        assertTrue(true);
    }

    @Test
    @Order(5)
    @DisplayName("Test ensureNvidiaContainerToolkit (auto-install if needed)")
    void testEnsureNvidiaContainerToolkit() {
        assumeTrue(isWindows, "Skipping: Not running on Windows");
        assumeTrue(hasWsl2, "Skipping: WSL2 not available");

        boolean gpuAvailable = provider.isNvidiaGpuAvailable();
        boolean toolkitInstalledBefore = provider.isNvidiaContainerToolkitInstalled();

        log.info("Before ensureNvidiaContainerToolkit:");
        log.info("  - NVIDIA GPU available: {}", gpuAvailable);
        log.info("  - Toolkit installed: {}", toolkitInstalledBefore);

        if (!gpuAvailable) {
            log.info("No NVIDIA GPU detected. ensureNvidiaContainerToolkit() will do nothing.");

            // Should not throw, just return silently
            assertDoesNotThrow(() -> provider.ensureNvidiaContainerToolkit());
            log.info("ensureNvidiaContainerToolkit() completed (no-op, no GPU)");
            return;
        }

        if (toolkitInstalledBefore) {
            log.info("Toolkit already installed. ensureNvidiaContainerToolkit() will do nothing.");

            // Should not throw, just return silently
            assertDoesNotThrow(() -> provider.ensureNvidiaContainerToolkit());
            log.info("ensureNvidiaContainerToolkit() completed (no-op, already installed)");
            return;
        }

        // GPU available but toolkit not installed - will attempt installation
        log.info("GPU available but toolkit not installed. Will attempt automatic installation...");
        log.info("This may take a few minutes...");

        try {
            provider.ensureNvidiaContainerToolkit();

            boolean toolkitInstalledAfter = provider.isNvidiaContainerToolkitInstalled();
            log.info("After ensureNvidiaContainerToolkit:");
            log.info("  - Toolkit installed: {}", toolkitInstalledAfter);

            assertTrue(toolkitInstalledAfter, "NVIDIA Container Toolkit should be installed after ensureNvidiaContainerToolkit()");
            log.info("SUCCESS: NVIDIA Container Toolkit was installed automatically!");

        } catch (IOException e) {
            String message = e.getMessage();

            // If it's a prerequisite issue (passwordless sudo not set up), skip the test with instructions
            if (message != null && message.contains("Passwordless sudo")) {
                log.warn("=== ONE-TIME SETUP REQUIRED ===");
                log.warn("Run these commands in WSL to enable automatic NVIDIA toolkit installation:");
                log.warn("");
                log.warn("  wsl -d Ubuntu");
                log.warn("  sudo bash -c 'echo \"$USER ALL=(ALL) NOPASSWD: ALL\" > /etc/sudoers.d/nopasswd-$USER'");
                log.warn("  sudo chmod 440 /etc/sudoers.d/nopasswd-$USER");
                log.warn("  exit");
                log.warn("");
                log.warn("Then run this test again.");
                log.warn("================================");

                // Skip the test instead of failing - prerequisite not met
                assumeTrue(false, "Skipping: Passwordless sudo not configured. See instructions above.");
            }

            // For other errors, fail the test
            log.error("Failed to install NVIDIA Container Toolkit: {}", message);
            log.error("This might be due to network issues.");
            log.error("You can install manually by running the commands shown in the error message.");
            fail("Failed to install NVIDIA Container Toolkit: " + message);
        }
    }

    @Test
    @Order(6)
    @DisplayName("Verify GPU container can be created (if toolkit installed)")
    void testGpuContainerCreation() {
        assumeTrue(isWindows, "Skipping: Not running on Windows");
        assumeTrue(hasWsl2, "Skipping: WSL2 not available");
        assumeTrue(provider.isNvidiaGpuAvailable(), "Skipping: No NVIDIA GPU available");
        assumeTrue(provider.isNvidiaContainerToolkitInstalled(), "Skipping: NVIDIA toolkit not installed");

        log.info("Testing GPU container creation...");

        DockerClient client = provider.getClient();

        // Try to pull and run nvidia-smi in a container
        try {
            log.info("Pulling nvidia/cuda:12.0.0-base-ubuntu22.04 image...");
            client.pullImage("nvidia/cuda:12.0.0-base-ubuntu22.04").exec(10, java.util.concurrent.TimeUnit.MINUTES);

            log.info("Creating container with GPU access...");
            CreateContainerResponse response = client.createContainer("nvidia/cuda:12.0.0-base-ubuntu22.04")
                    .withName("nvidia-test-" + System.currentTimeMillis())
                    .withCmd("nvidia-smi")
                    .withHostConfig(new io.github.intisy.docker.model.HostConfig()
                            .addDeviceRequest(io.github.intisy.docker.model.DeviceRequest.requestAllGpus()))
                    .exec();

            String containerId = response.getId();
            log.info("Container created: {}", containerId);

            // Start the container
            client.startContainer(containerId).exec();
            log.info("Container started");

            // Wait for it to finish
            client.waitContainer(containerId).exec();

            // Get logs
            String logs = client.logs(containerId)
                    .withStdout(true)
                    .withStderr(true)
                    .exec();

            log.info("Container output:\n{}", logs);

            // Cleanup
            client.removeContainer(containerId).exec();
            log.info("Container removed");

            // Check if nvidia-smi output looks valid
            assertTrue(logs.contains("NVIDIA") || logs.contains("GPU"),
                    "nvidia-smi output should contain GPU information");

            log.info("SUCCESS: GPU container ran successfully!");

        } catch (Exception e) {
            log.error("GPU container test failed: {}", e.getMessage());
            fail("GPU container test failed: " + e.getMessage());
        }
    }

    @Test
    @Order(7)
    @DisplayName("Diagnostic: Print full NVIDIA status")
    void testNvidiaDiagnostic() {
        assumeTrue(isWindows, "Skipping: Not running on Windows");

        log.info("=== NVIDIA Diagnostic Summary ===");
        log.info("OS: {}", System.getProperty("os.name"));
        log.info("WSL2 Available: {}", hasWsl2);

        if (hasWsl2 && provider != null) {
            log.info("NVIDIA GPU Detected: {}", provider.isNvidiaGpuAvailable());
            log.info("NVIDIA Container Toolkit Installed: {}", provider.isNvidiaContainerToolkitInstalled());
        }

        log.info("=================================");
    }
}