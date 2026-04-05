package io.github.intisy.docker.unit;

import io.github.intisy.docker.DockerProvider;
import io.github.intisy.docker.LinuxDockerProvider;
import io.github.intisy.docker.MacDockerProvider;
import io.github.intisy.docker.WindowsDockerProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DockerProvider factory and configuration (no Docker daemon required).
 *
 * @author Finn Birich
 */
@Tag("unit")
public class DockerProviderTest {

    @AfterEach
    void resetState() {
        DockerProvider.resetBaseDirectory();
    }

    @Test
    @DisplayName("get() returns a non-null provider for current OS")
    void testGetReturnsProvider() {
        DockerProvider provider = DockerProvider.get();
        assertNotNull(provider, "DockerProvider.get() should return a provider for the current OS");
    }

    @Test
    @DisplayName("get() returns correct provider type for current OS")
    void testGetReturnsCorrectType() {
        DockerProvider provider = DockerProvider.get();
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            assertInstanceOf(WindowsDockerProvider.class, provider, "Should return WindowsDockerProvider on Windows");
        } else if (os.contains("mac")) {
            assertInstanceOf(MacDockerProvider.class, provider, "Should return MacDockerProvider on macOS");
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            assertInstanceOf(LinuxDockerProvider.class, provider, "Should return LinuxDockerProvider on Linux");
        }
    }

    @Test
    @DisplayName("Each get() call returns a new instance")
    void testGetReturnsNewInstance() {
        DockerProvider provider1 = DockerProvider.get();
        DockerProvider provider2 = DockerProvider.get();

        assertNotSame(provider1, provider2, "Each get() call should return a new instance");
    }

    @Test
    @DisplayName("setBaseDirectory() and getBaseDirectory() work correctly")
    void testBaseDirectory() {
        Path customPath = Paths.get("/custom/docker/path");
        DockerProvider.setBaseDirectory(customPath);

        assertEquals(customPath, DockerProvider.getBaseDirectory(),
                "getBaseDirectory() should return the path set by setBaseDirectory()");
    }

    @Test
    @DisplayName("Default base directory is ~/.docker-java/")
    void testDefaultBaseDirectory() {
        Path expected = Paths.get(System.getProperty("user.home"), ".docker-java");
        assertEquals(expected, DockerProvider.getBaseDirectory(),
                "Default base directory should be ~/.docker-java/");
    }

    @Test
    @DisplayName("setWslBaseDirectory() and getWslBaseDirectory() work correctly")
    void testWslBaseDirectory() {
        DockerProvider.setWslBaseDirectory(".custom-wsl-dir");

        assertEquals(".custom-wsl-dir", DockerProvider.getWslBaseDirectory(),
                "getWslBaseDirectory() should return the path set by setWslBaseDirectory()");
    }

    @Test
    @DisplayName("Default WSL base directory is .docker-java")
    void testDefaultWslBaseDirectory() {
        assertEquals(".docker-java", DockerProvider.getWslBaseDirectory(),
                "Default WSL base directory should be .docker-java");
    }

    @Test
    @DisplayName("resetBaseDirectory() restores defaults")
    void testResetBaseDirectory() {
        Path expected = Paths.get(System.getProperty("user.home"), ".docker-java");

        DockerProvider.setBaseDirectory(Paths.get("/custom/path"));
        DockerProvider.setWslBaseDirectory(".custom-wsl");

        DockerProvider.resetBaseDirectory();

        assertEquals(expected, DockerProvider.getBaseDirectory(),
                "Base directory should be reset to default");
        assertEquals(".docker-java", DockerProvider.getWslBaseDirectory(),
                "WSL base directory should be reset to default");
    }

    @Test
    @DisplayName("Provider has a unique instance ID")
    void testInstanceId() {
        DockerProvider provider = DockerProvider.get();
        String instanceId = provider.getInstanceId();

        assertNotNull(instanceId, "Instance ID should not be null");
        assertFalse(instanceId.isEmpty(), "Instance ID should not be empty");
    }

    @Test
    @DisplayName("NVIDIA GPU methods have safe defaults")
    void testNvidiaDefaults() {
        DockerProvider provider = DockerProvider.get();

        assertFalse(provider.isNvidiaGpuAvailable(),
                "Base isNvidiaGpuAvailable() should return false by default");
        assertFalse(provider.isNvidiaContainerToolkitInstalled(),
                "Base isNvidiaContainerToolkitInstalled() should return false by default");
    }

    @Test
    @DisplayName("ensureNvidiaContainerToolkit() does not throw when no GPU")
    void testEnsureNvidiaNoOp() {
        DockerProvider provider = DockerProvider.get();

        assertDoesNotThrow(() -> provider.ensureNvidiaContainerToolkit(),
                "ensureNvidiaContainerToolkit() should not throw when no GPU is available");
    }
}
