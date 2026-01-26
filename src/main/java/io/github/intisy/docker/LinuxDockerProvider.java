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
 * Linux-specific Docker provider with rootless support.
 * Always starts its own Docker daemon to avoid conflicts with system Docker.
 * Supports multiple simultaneous instances.
 *
 * @author Finn Birich
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class LinuxDockerProvider extends DockerProvider {
    private static final Logger log = LoggerFactory.getLogger(LinuxDockerProvider.class);

    private static final String ROOTLESSKIT_VERSION = "v2.3.5";
    private static final String ROOTLESSKIT_DOWNLOAD_URL = "https://github.com/rootless-containers/rootlesskit/releases/download/%s/rootlesskit-%s.tar.gz";
    private static final String DOCKER_ROOTLESS_SCRIPT_URL = "https://raw.githubusercontent.com/moby/moby/master/contrib/dockerd-rootless.sh";
    private static final String DOCKER_DOWNLOAD_URL = "https://download.docker.com/linux/static/stable/%s/docker-%s.tgz";

    private static final String SLIRP4NETNS_VERSION = "v1.2.1";
    private static final String SLIRP4NETNS_DOWNLOAD_URL = "https://github.com/rootless-containers/slirp4netns/releases/download/%s/slirp4netns-%s";
    private static final Path SLIRP4NETNS_DIR = DOCKER_DIR.resolve("slirp4netns");
    private static final Path SLIRP4NETNS_PATH = SLIRP4NETNS_DIR.resolve("slirp4netns");

    private final String instanceId;
    
    private final Path instanceDir;
    private Path dockerPath;
    private Path rootlessKitPath;
    private Path dockerSocketPath;
    private Path dockerVersionFile;
    private Path dataDir;
    private Path runDir;
    private Path execDir;
    private Path configDir;

    private DockerClient dockerClient;
    private Process dockerProcess;
    
    public LinuxDockerProvider() {
        this.instanceId = UUID.randomUUID().toString().substring(0, 8);
        this.instanceDir = DOCKER_DIR.resolve("instances").resolve(instanceId);
        setPath(DOCKER_DIR);
        log.debug("Created LinuxDockerProvider with instance ID: {}", instanceId);
    }
    
    public void setPath(Path basePath) {
        dockerPath = basePath.resolve("docker/dockerd");
        rootlessKitPath = basePath.resolve("rootlesskit");
        dockerVersionFile = basePath.resolve(".docker-version");
        
        runDir = instanceDir.resolve("run");
        dataDir = instanceDir.resolve("data");
        execDir = instanceDir.resolve("exec");
        configDir = instanceDir.resolve("config");
        dockerSocketPath = runDir.resolve("docker.sock");
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void ensureInstalled() throws IOException {
        boolean autoUpdate = Boolean.parseBoolean(System.getProperty("docker.auto.update", "true"));
        String latestVersion = DockerVersionFetcher.getLatestVersion();
        boolean needsUpdate = true;

        if (Files.exists(dockerPath)) {
            if (autoUpdate && Files.exists(dockerVersionFile)) {
                String installedVersion = new String(Files.readAllBytes(dockerVersionFile)).trim();
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
            downloadAndExtract(dockerUrl, DOCKER_DIR);
            Files.write(dockerVersionFile, latestVersion.getBytes());
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
            case "arm":
            case "armv7l":
                return "armv7l";
            case "ppc64le":
                return "ppc64le";
            case "riscv64":
                return "riscv64";
            case "s390x":
                return "s390x";
            default:
                throw new UnsupportedOperationException("Unsupported architecture: " + osArch);
        }
    }

    private void ensureRootlessScriptInstalled() throws IOException {
        Path rootlessScriptPath = dockerPath.getParent().resolve("dockerd-rootless.sh");
        if (!Files.exists(rootlessScriptPath)) {
            log.info("Downloading dockerd-rootless.sh script...");
            downloadFile(DOCKER_ROOTLESS_SCRIPT_URL, rootlessScriptPath);
            rootlessScriptPath.toFile().setExecutable(true);
        }
    }

    private void ensureRootlessKitInstalled() throws IOException {
        if (Files.exists(rootlessKitPath.resolve("rootlesskit"))) {
            return;
        }
        log.info("RootlessKit not found. Downloading...");
        String url = String.format(ROOTLESSKIT_DOWNLOAD_URL, ROOTLESSKIT_VERSION, getArch());
        downloadAndExtract(url, rootlessKitPath);
        log.info("RootlessKit installed successfully");
    }

    private void ensureSlirp4netnsInstalled() throws IOException {
        if (Files.exists(SLIRP4NETNS_PATH)) {
            return;
        }
        log.info("slirp4netns not found. Downloading...");
        SLIRP4NETNS_DIR.toFile().mkdirs();
        String url = String.format(SLIRP4NETNS_DOWNLOAD_URL, SLIRP4NETNS_VERSION, getArch());
        Path downloadedFilePath = SLIRP4NETNS_DIR.resolve("slirp4netns-" + getArch());
        downloadFile(url, downloadedFilePath);
        Files.move(downloadedFilePath, SLIRP4NETNS_PATH);
        SLIRP4NETNS_PATH.toFile().setExecutable(true);
        log.info("slirp4netns installed successfully");
    }

    private void createInstanceDirectories() throws IOException {
        Files.createDirectories(runDir);
        Files.createDirectories(dataDir);
        Files.createDirectories(execDir);
        Files.createDirectories(configDir);
    }

    @Override
    public void start() throws IOException, InterruptedException {
        log.info("Starting managed Docker daemon (instance: {})...", instanceId);

        boolean forceRootless = Boolean.parseBoolean(System.getProperty("docker.force.rootless", "true"));

        ensureInstalled();
        createInstanceDirectories();
        
        ProcessBuilder pb;
        if (isRoot() && !forceRootless) {
            log.info("Running as root, starting dockerd");
            pb = new ProcessBuilder(
                    dockerPath.toString(),
                    "-H", "unix://" + dockerSocketPath.toString(),
                    "--data-root", dataDir.toString(),
                    "--exec-root", execDir.toString(),
                    "--pidfile", instanceDir.resolve("docker.pid").toString()
            );
        } else {
            log.info("Starting in rootless mode using dockerd-rootless.sh");

            ensureRootlessScriptInstalled();
            ensureRootlessKitInstalled();
            ensureSlirp4netnsInstalled();

            pb = new ProcessBuilder(dockerPath.getParent().resolve("dockerd-rootless.sh").toString());

            String path = pb.environment().getOrDefault("PATH", "");
            pb.environment().put("PATH", SLIRP4NETNS_DIR + File.pathSeparator + rootlessKitPath + File.pathSeparator + dockerPath.getParent().toString() + File.pathSeparator + path);
            pb.environment().put("XDG_RUNTIME_DIR", runDir.toString());
            pb.environment().put("XDG_DATA_HOME", dataDir.toString());
            pb.environment().put("XDG_CONFIG_HOME", configDir.toString());
        }
        
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
            String socketPath = dockerSocketPath.toString();
            this.dockerClient = DockerClient.builder()
                    .withHost("unix://" + socketPath)
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

    private boolean isRoot() {
        String username = System.getProperty("user.name");
        return username != null && username.equals("root");
    }

    private void downloadFile(String urlString, Path destinationPath) throws IOException {
        log.debug("Downloading {} to {}", urlString, destinationPath);
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");
        connection.connect();

        int responseCode = connection.getResponseCode();
        if (responseCode >= 400) {
            throw new IOException("Failed to download file: " + responseCode);
        }

        try (InputStream in = connection.getInputStream()) {
            Files.copy(in, destinationPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @SuppressWarnings("deprecation")
    private void downloadAndExtract(String urlString, Path destinationDir) throws IOException {
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
                Path outputPath = destinationDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(tis, outputPath, StandardCopyOption.REPLACE_EXISTING);
                    if (entry.getName().endsWith("dockerd") || entry.getName().contains("rootlesskit")) {
                        outputPath.toFile().setExecutable(true);
                    }
                }
            }
        }
    }

    @SuppressWarnings("BusyWait")
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
