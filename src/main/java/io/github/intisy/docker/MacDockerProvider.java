package io.github.intisy.docker;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * macOS-specific Docker provider.
 * Always starts its own Docker daemon to avoid conflicts with Docker Desktop.
 * Supports multiple simultaneous instances.
 *
 * @author Finn Birich
 */
@SuppressWarnings({"ResultOfMethodCallIgnored", "BusyWait"})
public class MacDockerProvider extends DockerProvider {
    private static final Logger log = LoggerFactory.getLogger(MacDockerProvider.class);

    private static final String DOCKER_DOWNLOAD_URL = "https://download.docker.com/mac/static/stable/%s/docker-%s.tgz";
    private static final Path DOCKER_PATH = DOCKER_DIR.resolve("docker/dockerd");
    private static final Path DOCKER_VERSION_FILE = DOCKER_DIR.resolve(".docker-version");

    private final String instanceId;
    
    private final Path instanceDir;
    private final Path runDir;
    private final Path dataDir;
    private final Path execDir;
    private final Path dockerSocketPath;

    private DockerClient dockerClient;
    private Process dockerProcess;

    public MacDockerProvider() {
        this.instanceId = UUID.randomUUID().toString().substring(0, 8);
        this.instanceDir = DOCKER_DIR.resolve("instances").resolve(instanceId);
        this.runDir = instanceDir.resolve("run");
        this.dataDir = instanceDir.resolve("data");
        this.execDir = instanceDir.resolve("exec");
        this.dockerSocketPath = runDir.resolve("docker.sock");
        log.debug("Created MacDockerProvider with instance ID: {}", instanceId);
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    public void ensureInstalled() throws IOException {
        boolean autoUpdate = Boolean.parseBoolean(System.getProperty("docker.auto.update", "true"));
        String latestVersion = DockerVersionFetcher.getLatestVersion();
        boolean needsUpdate = true;

        if (Files.exists(DOCKER_PATH)) {
            if (autoUpdate && Files.exists(DOCKER_VERSION_FILE)) {
                String installedVersion = new String(Files.readAllBytes(DOCKER_VERSION_FILE)).trim();
                if (!installedVersion.equals(latestVersion)) {
                    log.info("Newer Docker version available. Updating from {} to {}", installedVersion, latestVersion);
                } else {
                    log.info("Docker is up to date ({})", latestVersion);
                    needsUpdate = false;
                }
            } else if (!autoUpdate) {
                needsUpdate = false;
            }
        }

        if (needsUpdate) {
            log.info("Docker installation is incomplete or outdated. Re-installing...");

            Path dockerInstallDir = DOCKER_DIR.resolve("docker");
            if (Files.exists(dockerInstallDir)) {
                try (java.util.stream.Stream<Path> walk = Files.walk(dockerInstallDir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }

            String arch = getArch();
            String dockerUrl = String.format(DOCKER_DOWNLOAD_URL, arch, latestVersion);
            downloadAndExtract(dockerUrl);
            Files.write(DOCKER_VERSION_FILE, latestVersion.getBytes());
        }
    }

    private String getArch() {
        String osArch = System.getProperty("os.arch");
        switch (osArch) {
            case "amd64":
            case "x86_64":
                return "x86_64";
            case "aarch64":
            case "arm64":
                return "aarch64";
            default:
                throw new UnsupportedOperationException("Unsupported architecture: " + osArch);
        }
    }

    private void createInstanceDirectories() throws IOException {
        Files.createDirectories(runDir);
        Files.createDirectories(dataDir);
        Files.createDirectories(execDir);
    }

    @Override
    public void start() throws IOException, InterruptedException {
        log.info("Starting managed Docker daemon (instance: {})...", instanceId);

        ensureInstalled();
        createInstanceDirectories();

        ProcessBuilder pb = new ProcessBuilder(
                DOCKER_PATH.toString(),
                "-H", "unix://" + dockerSocketPath.toString(),
                "--data-root", dataDir.toString(),
                "--exec-root", execDir.toString(),
                "--pidfile", instanceDir.resolve("docker.pid").toString()
        );
        pb.environment().put("XDG_RUNTIME_DIR", runDir.toString());
        pb.environment().put("XDG_DATA_HOME", dataDir.toString());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        dockerProcess = pb.start();

        if (!waitForSocket()) {
            throw new RuntimeException("Docker daemon failed to create socket in time.");
        }
        
        log.info("Docker daemon started (instance: {})", instanceId);
    }

    @Override
    public DockerClient getClient() {
        if (this.dockerClient == null) {
            this.dockerClient = DockerClient.builder()
                    .withHost("unix://" + dockerSocketPath)
                    .build();
        }
        return this.dockerClient;
    }

    @Override
    public void stop() {
        log.info("Stopping Docker daemon (instance: {})...", instanceId);
        
        if (dockerProcess != null) {
            dockerProcess.destroy();
            try {
                dockerProcess.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                dockerProcess.destroyForcibly();
            }
        }
        
        cleanupInstanceDirectory();
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

    @SuppressWarnings("deprecation")
    private void downloadAndExtract(String urlString) throws IOException {
        log.debug("Downloading and extracting {}...", urlString);
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");

        if (connection.getResponseCode() != 200) {
            throw new IOException("Failed to download file from " + urlString + ". Status code: " + connection.getResponseCode());
        }

        try (InputStream is = connection.getInputStream();
             GzipCompressorInputStream gzis = new GzipCompressorInputStream(is);
             TarArchiveInputStream tis = new TarArchiveInputStream(gzis)) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                if (!tis.canReadEntryData(entry)) continue;
                Path outputPath = MacDockerProvider.DOCKER_DIR.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(tis, outputPath, StandardCopyOption.REPLACE_EXISTING);
                    if (entry.getName().endsWith("dockerd")) {
                        outputPath.toFile().setExecutable(true);
                    }
                }
            }
        }
    }

    private boolean waitForSocket() throws InterruptedException {
        log.debug("Waiting for Docker socket at {}...", dockerSocketPath);
        long timeoutMillis = TimeUnit.SECONDS.toMillis(30);
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (Files.exists(dockerSocketPath)) {
                log.debug("Docker socket found");
                return true;
            }
            Thread.sleep(500);
        }
        log.error("Timed out waiting for Docker socket");
        return false;
    }
}
