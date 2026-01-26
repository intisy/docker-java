package io.github.intisy.docker;

import io.github.intisy.docker.model.*;
import org.junit.jupiter.api.*;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for model classes (no Docker daemon required).
 *
 * @author Finn Birich
 */
public class ModelTest {

    @Test
    @DisplayName("ExposedPort - TCP port creation")
    void testExposedPortTcp() {
        ExposedPort port = ExposedPort.tcp(8080);
        
        assertEquals(8080, port.getPort());
        assertEquals("tcp", port.getProtocol());
        assertEquals("8080/tcp", port.toString());
    }

    @Test
    @DisplayName("ExposedPort - UDP port creation")
    void testExposedPortUdp() {
        ExposedPort port = ExposedPort.udp(53);
        
        assertEquals(53, port.getPort());
        assertEquals("udp", port.getProtocol());
        assertEquals("53/udp", port.toString());
    }

    @Test
    @DisplayName("ExposedPort - parse from string")
    void testExposedPortParse() {
        ExposedPort tcpPort = ExposedPort.parse("80/tcp");
        assertEquals(80, tcpPort.getPort());
        assertEquals("tcp", tcpPort.getProtocol());

        ExposedPort udpPort = ExposedPort.parse("53/udp");
        assertEquals(53, udpPort.getPort());
        assertEquals("udp", udpPort.getProtocol());
    }

    @Test
    @DisplayName("PortBinding - empty binding")
    void testPortBindingEmpty() {
        PortBinding binding = PortBinding.empty();
        
        assertNull(binding.getHostIp());
        assertNull(binding.getHostPort());
    }

    @Test
    @DisplayName("PortBinding - with port")
    void testPortBindingWithPort() {
        PortBinding binding = PortBinding.of(8080);
        
        assertNull(binding.getHostIp());
        assertEquals("8080", binding.getHostPort());
        assertEquals(8080, binding.getHostPortAsInt());
    }

    @Test
    @DisplayName("PortBinding - with IP and port")
    void testPortBindingWithIpAndPort() {
        PortBinding binding = PortBinding.of("127.0.0.1", 8080);
        
        assertEquals("127.0.0.1", binding.getHostIp());
        assertEquals("8080", binding.getHostPort());
        assertEquals("127.0.0.1:8080", binding.toString());
    }

    @Test
    @DisplayName("Mount - bind mount creation")
    void testMountBind() {
        Mount mount = Mount.bind("/host/path", "/container/path");
        
        assertEquals("bind", mount.getType());
        assertEquals("/host/path", mount.getSource());
        assertEquals("/container/path", mount.getTarget());
    }

    @Test
    @DisplayName("Mount - volume mount creation")
    void testMountVolume() {
        Mount mount = Mount.volume("my-volume", "/data");
        
        assertEquals("volume", mount.getType());
        assertEquals("my-volume", mount.getSource());
        assertEquals("/data", mount.getTarget());
    }

    @Test
    @DisplayName("Mount - tmpfs mount creation")
    void testMountTmpfs() {
        Mount mount = Mount.tmpfs("/tmp");
        
        assertEquals("tmpfs", mount.getType());
        assertEquals("/tmp", mount.getTarget());
    }

    @Test
    @DisplayName("HostConfig - port binding")
    void testHostConfigPortBinding() {
        HostConfig config = new HostConfig();
        ExposedPort exposedPort = ExposedPort.tcp(80);
        PortBinding binding = PortBinding.of(8080);
        
        config.addPortBinding(exposedPort, binding);
        
        assertNotNull(config.getPortBindings());
        assertTrue(config.getPortBindings().containsKey("80/tcp"));
        assertEquals(1, config.getPortBindings().get("80/tcp").size());
    }

    @Test
    @DisplayName("HostConfig - bind mounts")
    void testHostConfigBinds() {
        HostConfig config = new HostConfig();
        
        config.addBind("/host/path:/container/path")
              .addBind("/data:/data:ro");
        
        assertNotNull(config.getBinds());
        assertEquals(2, config.getBinds().size());
    }

    @Test
    @DisplayName("HostConfig - restart policy")
    void testHostConfigRestartPolicy() {
        assertEquals("no", HostConfig.RestartPolicy.noRestart().getName());
        assertEquals("always", HostConfig.RestartPolicy.alwaysRestart().getName());
        assertEquals("unless-stopped", HostConfig.RestartPolicy.unlessStopped().getName());
        
        HostConfig.RestartPolicy onFailure = HostConfig.RestartPolicy.onFailure(5);
        assertEquals("on-failure", onFailure.getName());
        assertEquals(Integer.valueOf(5), onFailure.getMaximumRetryCount());
    }

    @Test
    @DisplayName("ContainerConfig - environment variables")
    void testContainerConfigEnv() {
        ContainerConfig config = new ContainerConfig();
        
        config.addEnv("KEY1", "value1")
              .addEnv("KEY2", "value2");
        
        assertNotNull(config.getEnv());
        assertEquals(2, config.getEnv().size());
        assertTrue(config.getEnv().contains("KEY1=value1"));
        assertTrue(config.getEnv().contains("KEY2=value2"));
    }

    @Test
    @DisplayName("ContainerConfig - command")
    void testContainerConfigCmd() {
        ContainerConfig config = new ContainerConfig();
        
        config.setCmd("echo", "hello", "world");
        
        assertNotNull(config.getCmd());
        assertEquals(3, config.getCmd().size());
        assertEquals("echo", config.getCmd().get(0));
    }

    @Test
    @DisplayName("ContainerConfig - labels")
    void testContainerConfigLabels() {
        ContainerConfig config = new ContainerConfig();
        
        config.addLabel("app", "myapp")
              .addLabel("version", "1.0");
        
        assertNotNull(config.getLabels());
        assertEquals(2, config.getLabels().size());
        assertEquals("myapp", config.getLabels().get("app"));
    }

    @Test
    @DisplayName("ContainerConfig - exposed ports")
    void testContainerConfigExposedPorts() {
        ContainerConfig config = new ContainerConfig();
        
        config.addExposedPort(ExposedPort.tcp(80))
              .addExposedPort(ExposedPort.tcp(443));
        
        assertNotNull(config.getExposedPorts());
        assertEquals(2, config.getExposedPorts().size());
        assertTrue(config.getExposedPorts().containsKey("80/tcp"));
        assertTrue(config.getExposedPorts().containsKey("443/tcp"));
    }

    @Test
    @DisplayName("ExecConfig - command and options")
    void testExecConfig() {
        ExecConfig config = new ExecConfig();
        
        config.setCmd("ls", "-la")
              .setAttachStdout(true)
              .setAttachStderr(true)
              .setTty(false)
              .setUser("root")
              .setWorkingDir("/app")
              .addEnv("PATH", "/usr/bin");
        
        assertEquals(Arrays.asList("ls", "-la"), config.getCmd());
        assertTrue(config.getAttachStdout());
        assertTrue(config.getAttachStderr());
        assertFalse(config.getTty());
        assertEquals("root", config.getUser());
        assertEquals("/app", config.getWorkingDir());
        assertTrue(config.getEnv().contains("PATH=/usr/bin"));
    }

    @Test
    @DisplayName("PullResponse - progress parsing")
    void testPullResponseProgress() {
        PullResponse.ProgressDetail detail = new PullResponse.ProgressDetail();
        
        assertEquals(0, detail.getPercentage());
    }

    @Test
    @DisplayName("ExposedPort - equality")
    void testExposedPortEquality() {
        ExposedPort port1 = ExposedPort.tcp(80);
        ExposedPort port2 = ExposedPort.tcp(80);
        ExposedPort port3 = ExposedPort.udp(80);
        
        assertEquals(port1, port2);
        assertNotEquals(port1, port3);
        assertEquals(port1.hashCode(), port2.hashCode());
    }
}
