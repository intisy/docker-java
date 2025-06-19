package io.github.intisy.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

/**
 * @author Finn Birich
 */
public class LinuxDockerProvider implements DockerProvider {
    private static final String ROOTLESSKIT_VERSION = "v2.1.1";
    private static final String ROOTLESSKIT_DOWNLOAD_URL = "https://github.com/rootless-containers/rootlesskit/releases/download/%s/rootlesskit-%s.tar.gz";
    private static final String DOCKER_ROOTLESS_SCRIPT_URL = "https://raw.githubusercontent.com/moby/moby/master/contrib/dockerd-rootless.sh";
    private static final Path DOCKER_DIR = Path.of(System.getProperty("user.home"), ".docker-java");
    private static final Path DOCKER_PATH = DOCKER_DIR.resolve("docker/dockerd");
    private static final Path ROOTLESSKIT_PATH = DOCKER_DIR.resolve("rootlesskit");
    private static final Path DOCKER_SOCKET_PATH = DOCKER_DIR.resolve("run/docker.sock");
    private static final Path DOCKER_VERSION_FILE = DOCKER_DIR.resolve(".docker-version");

    private static final String SLIRP4NETNS_VERSION = "v1.2.1";
    private static final String SLIRP4NETNS_DOWNLOAD_URL = "https://github.com/rootless-containers/slirp4netns/releases/download/%s/slirp4netns-%s";
    private static final Path SLIRP4NETNS_DIR = DOCKER_DIR.resolve("slirp4netns");
    private static final Path SLIRP4NETNS_PATH = SLIRP4NETNS_DIR.resolve("slirp4netns");

    private DockerClient dockerClient;
    private Process dockerProcess;

    @Override
    public void ensureInstalled() throws IOException, InterruptedException {
        ensureDockerInstalled();
        ensureRootlessScriptInstalled();
        ensureRootlessKitInstalled();
        ensureSlirp4netnsInstalled();
    }

    private void ensureDockerInstalled() throws IOException, InterruptedException {
        boolean autoUpdate = Boolean.parseBoolean(System.getProperty("docker.auto.update", "true"));
        String latestVersion = DockerVersionFetcher.getLatestVersion();
        boolean needsUpdate = true;

        if (Files.exists(DOCKER_PATH)) {
            if (autoUpdate && Files.exists(DOCKER_VERSION_FILE)) {
                String installedVersion = Files.readString(DOCKER_VERSION_FILE).trim();
                if (!installedVersion.equals(latestVersion)) {
                    System.out.println("Newer Docker version available. Updating from " + installedVersion + " to " + latestVersion);
                } else {
                    System.out.println("Docker is up to date. (" + latestVersion + ")");
                    needsUpdate = false;
                }
            } else if (!autoUpdate) {
                needsUpdate = false;
            }
        }

        if (needsUpdate) {
            System.out.println("Docker installation is incomplete or outdated. Re-installing...");

            Path dockerInstallDir = DOCKER_DIR.resolve("docker");
            if (Files.exists(dockerInstallDir)) {
                try (java.util.stream.Stream<Path> walk = Files.walk(dockerInstallDir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(java.io.File::delete);
                }
            }

            String arch = getArch();
            String dockerUrl = String.format("https://download.docker.com/linux/static/stable/%s/docker-%s.tgz", arch, latestVersion);
            downloadAndExtract(dockerUrl, DOCKER_DIR);
            Files.writeString(DOCKER_VERSION_FILE, latestVersion);
        }
    }

    private String getArch() {
        String osArch = System.getProperty("os.arch");
        switch (osArch) {
            case "amd64":
                return "x86_64";
            case "aarch64":
                return "aarch64";
            default:
                throw new UnsupportedOperationException("Unsupported architecture: " + osArch);
        }
    }

    private void ensureRootlessScriptInstalled() throws IOException, InterruptedException {
        Path rootlessScriptPath = DOCKER_PATH.getParent().resolve("dockerd-rootless.sh");
        if (!Files.exists(rootlessScriptPath)) {
            System.out.println("Downloading dockerd-rootless.sh script...");
            downloadFile(DOCKER_ROOTLESS_SCRIPT_URL, rootlessScriptPath);
            rootlessScriptPath.toFile().setExecutable(true);
        }
    }

    private void ensureRootlessKitInstalled() throws IOException, InterruptedException {
        if (Files.exists(ROOTLESSKIT_PATH.resolve("rootlesskit"))) {
            return;
        }
        System.out.println("RootlessKit not found. Downloading...");
        String url = String.format(ROOTLESSKIT_DOWNLOAD_URL, ROOTLESSKIT_VERSION, getArch());
        downloadAndExtract(url, ROOTLESSKIT_PATH);
        System.out.println("RootlessKit installed successfully.");
    }

    private void ensureSlirp4netnsInstalled() throws IOException, InterruptedException {
        if (Files.exists(SLIRP4NETNS_PATH)) {
            return;
        }
        System.out.println("slirp4netns not found. Downloading...");
        SLIRP4NETNS_DIR.toFile().mkdirs();
        String url = String.format(SLIRP4NETNS_DOWNLOAD_URL, SLIRP4NETNS_VERSION, getArch());
        Path downloadedFilePath = SLIRP4NETNS_DIR.resolve("slirp4netns-" + getArch());
        downloadFile(url, downloadedFilePath);
        Files.move(downloadedFilePath, SLIRP4NETNS_PATH);
        SLIRP4NETNS_PATH.toFile().setExecutable(true);
        System.out.println("slirp4netns installed successfully.");
    }

    @Override
    public void start() throws IOException, InterruptedException {
        boolean forceRootless = Boolean.parseBoolean(System.getProperty("docker.force.rootless", "true"));

        ProcessBuilder pb;
        if (isRoot() && !forceRootless) {
            System.out.println("Running as root, starting dockerd with sudo.");
            pb = new ProcessBuilder("sudo", DOCKER_PATH.toString(), "-H", "unix://" + DOCKER_SOCKET_PATH.toString());
            dockerProcess = pb.start();
        } else {
            System.out.println("Attempting to start in rootless mode using dockerd-rootless.sh.");

            Path runDir = DOCKER_DIR.resolve("run");
            Path dataDir = DOCKER_DIR.resolve("data");
            Path configDir = DOCKER_DIR.resolve("config");
            runDir.toFile().mkdirs();
            dataDir.toFile().mkdirs();
            configDir.toFile().mkdirs();

            pb = new ProcessBuilder(DOCKER_PATH.getParent().resolve("dockerd-rootless.sh").toString());

            String path = pb.environment().getOrDefault("PATH", "");
            pb.environment().put("PATH", SLIRP4NETNS_DIR.toString() + File.pathSeparator + ROOTLESSKIT_PATH.toString() + File.pathSeparator + DOCKER_PATH.getParent().toString() + File.pathSeparator + path);
            pb.environment().put("XDG_RUNTIME_DIR", runDir.toString());
            pb.environment().put("XDG_DATA_HOME", dataDir.toString());
            pb.environment().put("XDG_CONFIG_HOME", configDir.toString());

            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            dockerProcess = pb.start();
        }

        if (!waitForSocket()) {
            throw new RuntimeException("Docker daemon failed to create socket in time.");
        }
    }

    @Override
    public DockerClient getClient() {
        if (this.dockerClient == null) {
            String socketPath = DOCKER_SOCKET_PATH.toString();
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost("unix://" + socketPath).build();

            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .build();

            this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
        }
        return this.dockerClient;
    }

    @Override
    public void stop() {
        if (dockerProcess != null) {
            dockerProcess.destroy();
            try {
                dockerProcess.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                dockerProcess.destroyForcibly();
            }
        }
    }

    private boolean isRoot() {
        String username = System.getProperty("user.name");
        return username != null && username.equals("root");
    }

    private void downloadFile(String urlString, Path destinationPath) throws IOException, InterruptedException {
        System.out.println("Downloading " + urlString + " to " + destinationPath);
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlString))
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() >= 400) {
            throw new IOException("Failed to download file: " + response.statusCode());
        }

        try (InputStream in = response.body()) {
            Files.copy(in, destinationPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void downloadAndExtract(String urlString, Path destinationDir, String... toExtract) throws IOException, InterruptedException {
        System.out.println("Downloading and extracting " + urlString + "...");
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlString)).build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to download file from " + urlString + ". Status code: " + response.statusCode());
        }

        try (InputStream is = response.body();
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

    private boolean waitForSocket() throws InterruptedException {
        System.out.println("Waiting for Docker socket to be available at " + DOCKER_SOCKET_PATH + "...");
        long timeoutMillis = TimeUnit.SECONDS.toMillis(30);
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (Files.exists(DOCKER_SOCKET_PATH)) {
                System.out.println("Docker socket found.");
                return true;
            }
            Thread.sleep(500);
        }
        System.err.println("Timed out waiting for Docker socket.");
        return false;
    }
}