package io.github.intisy.docker;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.github.intisy.docker.IOUtils.readAllBytes;

/**
 * macOS-specific Docker provider.
 * <p>
 * On macOS, Docker requires a Linux VM to run containers because containers
 * depend on Linux kernel features (namespaces, cgroups). This provider uses
 * Lima to create and manage a lightweight Linux VM where Docker can run.
 * <p>
 * Lima is automatically downloaded and installed if not present.
 * <p>
 * Supports multiple simultaneous instances by creating isolated Lima VMs.
 *
 * @author Finn Birich
 */
@SuppressWarnings({"ResultOfMethodCallIgnored", "BusyWait"})
public class MacDockerProvider extends DockerProvider {
    private static final Logger log = LoggerFactory.getLogger(MacDockerProvider.class);

    private static final String LIMA_VERSION = "2.0.3";
    private static final String LIMA_DOWNLOAD_URL = "https://github.com/lima-vm/lima/releases/download/v%s/lima-%s-Darwin-%s.tar.gz";
    private static final Path LIMA_DIR = DOCKER_DIR.resolve("lima");
    private static final Path LIMACTL_PATH = LIMA_DIR.resolve("bin/limactl");
    private static final Path LIMA_VERSION_FILE = DOCKER_DIR.resolve(".lima-version");

    private static final String LIMA_VM_PREFIX = "docker-java-";

    private final String instanceId;
    private final String vmName;

    private final Path instanceDir;

    private DockerClient dockerClient;
    private int dockerPort;
    private boolean vmStartedByUs = false;

    public MacDockerProvider() {
        this.instanceId = UUID.randomUUID().toString().substring(0, 8);
        this.vmName = LIMA_VM_PREFIX + instanceId;
        Path baseDir = getBaseDirectory();
        this.instanceDir = baseDir.resolve("instances").resolve(instanceId);
        log.debug("Created MacDockerProvider with instance ID: {}", instanceId);
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public void ensureInstalled() throws IOException {
        if (!isLimaInstalled()) {
            log.info("Lima not found. Downloading Lima {}...", LIMA_VERSION);
            downloadAndInstallLima();
        }

        if (!isLimaInstalled()) {
            throw new IOException("Failed to install Lima. Please install manually: brew install lima");
        }

        log.info("Lima is ready");
    }

    /**
     * Get the path to limactl (either local installation or system-wide).
     */
    private String getLimactlPath() {
        if (Files.exists(LIMACTL_PATH) && Files.isExecutable(LIMACTL_PATH)) {
            return LIMACTL_PATH.toString();
        }
        return "limactl";
    }

    /**
     * Check if Lima is installed on the system (either locally or system-wide).
     */
    private boolean isLimaInstalled() {
        if (Files.exists(LIMACTL_PATH)) {
            try {
                ProcessBuilder pb = new ProcessBuilder(LIMACTL_PATH.toString(), "--version");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                byte[] output = readAllBytes(process.getInputStream());
                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    log.debug("Local Lima version: {}", new String(output).trim());
                    return true;
                }
            } catch (IOException | InterruptedException e) {
                log.debug("Local Lima check failed: {}", e.getMessage());
            }
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("limactl", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            byte[] output = readAllBytes(process.getInputStream());
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.debug("System Lima version: {}", new String(output).trim());
                return true;
            }
            return false;
        } catch (IOException | InterruptedException e) {
            log.debug("System Lima check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Download and install Lima from GitHub releases.
     */
    @SuppressWarnings("deprecation")
    private void downloadAndInstallLima() throws IOException {
        String arch = getArch();
        String downloadUrl = String.format(LIMA_DOWNLOAD_URL, LIMA_VERSION, LIMA_VERSION, arch);

        log.info("Downloading Lima from {}...", downloadUrl);

        if (Files.exists(LIMA_DIR)) {
            try (java.util.stream.Stream<Path> walk = Files.walk(LIMA_DIR)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
        Files.createDirectories(LIMA_DIR);

        URL url = new URL(downloadUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Failed to download Lima from " + downloadUrl + ". Status code: " + responseCode);
        }

        try (InputStream is = connection.getInputStream();
             GzipCompressorInputStream gzis = new GzipCompressorInputStream(is);
             TarArchiveInputStream tis = new TarArchiveInputStream(gzis)) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                if (!tis.canReadEntryData(entry)) continue;
                Path outputPath = LIMA_DIR.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(tis, outputPath, StandardCopyOption.REPLACE_EXISTING);
                    if (entry.getName().contains("/bin/")) {
                        outputPath.toFile().setExecutable(true);
                    }
                }
            }
        }

        Files.write(LIMA_VERSION_FILE, LIMA_VERSION.getBytes());

        log.info("Lima {} installed successfully", LIMA_VERSION);
    }

    /**
     * Get the architecture string for Lima download.
     */
    private String getArch() {
        String osArch = System.getProperty("os.arch");
        switch (osArch) {
            case "amd64":
            case "x86_64":
                return "x86_64";
            case "aarch64":
            case "arm64":
                return "arm64";
            default:
                throw new UnsupportedOperationException("Unsupported architecture: " + osArch);
        }
    }

    @Override
    public void start() throws IOException, InterruptedException {
        log.info("Starting Docker via Lima VM (instance: {})...", instanceId);

        ensureInstalled();
        Files.createDirectories(instanceDir);

        dockerPort = 2375 + Math.abs(instanceId.hashCode() % 1000);

        createAndStartLimaVm();

        if (!waitForDocker()) {
            String logs = getLimaLogs();
            log.error("Lima VM logs:\n{}", logs);
            throw new RuntimeException("Docker daemon in Lima VM failed to start. See logs above.");
        }

        log.info("Docker daemon started in Lima VM (instance: {}, port: {})", instanceId, dockerPort);
    }

    /**
     * Create and start a Lima VM configured for Docker.
     */
    private void createAndStartLimaVm() throws IOException, InterruptedException {
        String limactl = getLimactlPath();

        String vmStatus = runLimaCommand(limactl, "list", "--format", "{{.Name}}:{{.Status}}");
        boolean vmExists = vmStatus.contains(vmName + ":");

        if (vmExists) {
            log.info("Lima VM {} already exists, checking status...", vmName);
            if (vmStatus.contains(vmName + ":Running")) {
                log.info("Lima VM {} is already running", vmName);
                vmStartedByUs = false;
                ensureDockerRunning();
                return;
            } else {
                log.info("Starting existing Lima VM {}...", vmName);
                runLimaCommand(limactl, "start", vmName);
                vmStartedByUs = true;
                ensureDockerRunning();
                return;
            }
        }

        Path configPath = instanceDir.resolve("lima.yaml");
        String limaConfig = createLimaConfig();
        Files.write(configPath, limaConfig.getBytes());

        log.info("Creating Lima VM {} with Docker...", vmName);

        ProcessBuilder pb = new ProcessBuilder(limactl, "start", "--name=" + vmName, configPath.toString());
        pb.redirectErrorStream(true);
        pb.inheritIO();
        Process process = pb.start();

        boolean completed = process.waitFor(10, TimeUnit.MINUTES);
        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("Lima VM creation timed out after 10 minutes");
        }

        if (process.exitValue() != 0) {
            throw new RuntimeException("Failed to create Lima VM. Exit code: " + process.exitValue());
        }

        vmStartedByUs = true;

        ensureDockerRunning();
    }

    /**
     * Create Lima configuration YAML for Docker.
     */
    private String createLimaConfig() {
        return String.format(
                "# Lima VM configuration for docker-java\n" +
                "# Instance: %s\n" +
                "\n" +
                "# Use a minimal Alpine-based image for fast startup\n" +
                "images:\n" +
                "  - location: \"https://cloud-images.ubuntu.com/releases/24.04/release/ubuntu-24.04-server-cloudimg-amd64.img\"\n" +
                "    arch: \"x86_64\"\n" +
                "  - location: \"https://cloud-images.ubuntu.com/releases/24.04/release/ubuntu-24.04-server-cloudimg-arm64.img\"\n" +
                "    arch: \"aarch64\"\n" +
                "\n" +
                "# VM resources\n" +
                "cpus: 2\n" +
                "memory: \"2GiB\"\n" +
                "disk: \"10GiB\"\n" +
                "\n" +
                "# Forward Docker port to host\n" +
                "portForwards:\n" +
                "  - guestPort: 2375\n" +
                "    hostPort: %d\n" +
                "\n" +
                "# Provision script to install and configure Docker\n" +
                "provision:\n" +
                "  - mode: system\n" +
                "    script: |\n" +
                "      #!/bin/bash\n" +
                "      set -eux -o pipefail\n" +
                "      \n" +
                "      # Install Docker if not present\n" +
                "      if ! command -v docker &> /dev/null; then\n" +
                "        curl -fsSL https://get.docker.com | sh\n" +
                "      fi\n" +
                "      \n" +
                "      # Configure Docker to listen on TCP\n" +
                "      mkdir -p /etc/docker\n" +
                "      cat > /etc/docker/daemon.json << 'EOF'\n" +
                "      {\n" +
                "        \"hosts\": [\"unix:///var/run/docker.sock\", \"tcp://0.0.0.0:2375\"]\n" +
                "      }\n" +
                "      EOF\n" +
                "      \n" +
                "      # Override systemd to not use -H flag (conflicts with daemon.json)\n" +
                "      mkdir -p /etc/systemd/system/docker.service.d\n" +
                "      cat > /etc/systemd/system/docker.service.d/override.conf << 'EOF'\n" +
                "      [Service]\n" +
                "      ExecStart=\n" +
                "      ExecStart=/usr/bin/dockerd\n" +
                "      EOF\n" +
                "      \n" +
                "      # Reload and restart Docker\n" +
                "      systemctl daemon-reload\n" +
                "      systemctl enable docker\n" +
                "      systemctl restart docker\n",
                instanceId, dockerPort
        );
    }

    /**
     * Ensure Docker is running inside the Lima VM.
     */
    private void ensureDockerRunning() throws IOException, InterruptedException {
        log.debug("Ensuring Docker is running in Lima VM {}...", vmName);

        String result = runLimaShellCommand("systemctl is-active docker || true");
        if (!"active".equals(result.trim())) {
            log.info("Starting Docker in Lima VM...");
            runLimaShellCommand("sudo systemctl start docker");
        }

        String tcpCheck = runLimaShellCommand("ss -tlnp | grep 2375 || true");
        if (tcpCheck.trim().isEmpty()) {
            log.info("Configuring Docker to listen on TCP...");
            runLimaShellCommand(
                    "sudo mkdir -p /etc/docker && " +
                    "echo '{\"hosts\": [\"unix:///var/run/docker.sock\", \"tcp://0.0.0.0:2375\"]}' | sudo tee /etc/docker/daemon.json && " +
                    "sudo mkdir -p /etc/systemd/system/docker.service.d && " +
                    "echo -e '[Service]\\nExecStart=\\nExecStart=/usr/bin/dockerd' | sudo tee /etc/systemd/system/docker.service.d/override.conf && " +
                    "sudo systemctl daemon-reload && " +
                    "sudo systemctl restart docker"
            );
        }
    }

    /**
     * Run a Lima shell command in the VM.
     */
    private String runLimaShellCommand(String command) throws IOException, InterruptedException {
        String limactl = getLimactlPath();
        ProcessBuilder pb = new ProcessBuilder(limactl, "shell", vmName, "bash", "-c", command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        byte[] output = readAllBytes(process.getInputStream());
        process.waitFor(60, TimeUnit.SECONDS);
        return new String(output).trim();
    }

    /**
     * Run a limactl command.
     */
    private String runLimaCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        byte[] output = readAllBytes(process.getInputStream());
        process.waitFor(30, TimeUnit.SECONDS);
        return new String(output).trim();
    }

    /**
     * Get Lima VM logs for debugging.
     */
    private String getLimaLogs() {
        try {
            return runLimaShellCommand("sudo journalctl -u docker --no-pager -n 50 2>/dev/null || cat /var/log/docker.log 2>/dev/null || echo 'No logs available'");
        } catch (IOException | InterruptedException e) {
            return "Failed to get logs: " + e.getMessage();
        }
    }

    /**
     * Wait for Docker to be accessible.
     */
    private boolean waitForDocker() throws InterruptedException {
        log.debug("Waiting for Docker daemon on localhost:{}...", dockerPort);
        long timeoutMillis = TimeUnit.SECONDS.toMillis(120);
        long startTime = System.currentTimeMillis();
        int attempts = 0;

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            attempts++;

            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress("localhost", dockerPort), 1000);
                socket.close();
                log.debug("Docker daemon is listening on localhost:{} after {} attempts", dockerPort, attempts);

                try {
                    DockerClient testClient = DockerClient.builder()
                            .withHost("tcp://localhost:" + dockerPort)
                            .build();
                    testClient.ping().exec();
                    testClient.close();
                    return true;
                } catch (Exception e) {
                    log.debug("Docker API not ready yet: {}", e.getMessage());
                }
            } catch (IOException e) {
            }

            if (attempts % 20 == 0) {
                log.debug("Still waiting for Docker... ({} seconds elapsed)",
                        (System.currentTimeMillis() - startTime) / 1000);
            }

            Thread.sleep(500);
        }
        
        log.error("Timed out waiting for Docker daemon on port {}", dockerPort);
        return false;
    }

    @Override
    public DockerClient getClient() {
        if (this.dockerClient == null) {
            this.dockerClient = DockerClient.builder()
                    .withHost("tcp://localhost:" + dockerPort)
                    .build();
        }
        return this.dockerClient;
    }

    @Override
    public void stop() {
        log.info("Stopping Docker daemon (instance: {})...", instanceId);
        
        if (dockerClient != null) {
            try {
                dockerClient.close();
            } catch (Exception e) {
                log.debug("Error closing Docker client: {}", e.getMessage());
            }
            dockerClient = null;
        }

        if (vmStartedByUs) {
            try {
                String limactl = getLimactlPath();

                log.info("Stopping Lima VM {}...", vmName);
                ProcessBuilder pb = new ProcessBuilder(limactl, "stop", vmName);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                process.waitFor(30, TimeUnit.SECONDS);

                log.info("Deleting Lima VM {}...", vmName);
                pb = new ProcessBuilder(limactl, "delete", vmName, "--force");
                pb.redirectErrorStream(true);
                process = pb.start();
                process.waitFor(30, TimeUnit.SECONDS);
            } catch (IOException | InterruptedException e) {
                log.warn("Failed to stop Lima VM: {}", e.getMessage());
            }
        }
        
        cleanupInstanceDirectory();
        log.info("Docker daemon stopped (instance: {})", instanceId);
    }

    private void cleanupInstanceDirectory() {
        try {
            if (Files.exists(instanceDir)) {
                try (java.util.stream.Stream<Path> walk = Files.walk(instanceDir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to clean up instance directory: {}", e.getMessage());
        }
    }
}
