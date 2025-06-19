package io.github.intisy.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Finn Birich
 */
public class LinuxDockerProvider implements DockerProvider {
    private static final String APP_BASE_PATH = System.getProperty("user.home") + "/.docker-java";
    private static final Path DOCKER_RUNTIME_PATH = Paths.get(APP_BASE_PATH, "runtime");
    private static final Path DOCKER_BIN_PATH = DOCKER_RUNTIME_PATH.resolve("docker/dockerd");
    private static final Path DOCKER_DATA_ROOT = Paths.get(APP_BASE_PATH, "data", "docker-root");
    private static final Path DOCKER_RUN_PATH = Paths.get(APP_BASE_PATH, "run");
    private static final Path DOCKER_SOCKET_PATH = DOCKER_RUN_PATH.resolve("docker.sock");
    private static final Path DOCKER_PID_PATH = DOCKER_RUN_PATH.resolve("docker.pid");
    private static final Path VERSION_FILE = DOCKER_RUNTIME_PATH.resolve("docker/version.txt");

    private Process dockerDaemonProcess;
    private DockerClient dockerClient;

    @Override
    public void ensureInstalled() throws IOException, InterruptedException {
        String latestVersion = getLatestDockerVersion();
        String currentVersion = getCurrentVersion();
        if (!latestVersion.equals(currentVersion)) {
            downloadAndUnpack(latestVersion);
            setCurrentVersion(latestVersion);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void start() throws IOException, InterruptedException {
        if (!DOCKER_BIN_PATH.toFile().exists()) {
            throw new IllegalStateException("Docker binary not found. Please run ensureInstalled() first.");
        }
        DOCKER_DATA_ROOT.toFile().mkdirs();
        DOCKER_RUN_PATH.toFile().mkdirs();

        ProcessBuilder pb = new ProcessBuilder(
                "sudo", DOCKER_BIN_PATH.toString(),
                "--data-root", DOCKER_DATA_ROOT.toString(),
                "--pidfile", DOCKER_PID_PATH.toString(),
                "-H", "unix://" + DOCKER_SOCKET_PATH
        );
        pb.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.INHERIT);
        this.dockerDaemonProcess = pb.start();

        if (!waitForFile()) {
            throw new RuntimeException("Docker daemon failed to create socket in time.");
        }
    }

    @Override
    public DockerClient getClient() {
        if (this.dockerClient == null) {
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost("unix://" + DOCKER_SOCKET_PATH).build();
            this.dockerClient = DockerClientBuilder.getInstance(config).build();
        }
        return this.dockerClient;
    }

    @Override
    public void stop() {
        if (dockerDaemonProcess != null) {
            dockerDaemonProcess.destroy();
            try {
                dockerDaemonProcess.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                dockerDaemonProcess.destroyForcibly();
            }
        }
    }

    @SuppressWarnings("BusyWait")
    private boolean waitForFile() throws InterruptedException {
        long timeoutMillis = TimeUnit.SECONDS.toMillis(15);
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (Files.exists(DOCKER_SOCKET_PATH)) {
                return true;
            }
            Thread.sleep(250);
        }
        return false;
    }

    private String getLatestDockerVersion() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/moby/moby/releases/latest"))
                .header("Accept", "application/vnd.github.v3+json")
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json = new Gson().fromJson(response.body(), JsonObject.class);
        return json.get("tag_name").getAsString().replace("v", "");
    }

    private void downloadAndUnpack(String version) throws IOException, InterruptedException {
        String arch = System.getProperty("os.arch").equals("amd64") ? "x86_64" : System.getProperty("os.arch");
        String downloadUrl = String.format("https://download.docker.com/linux/static/stable/%s/docker-%s.tgz", arch, version);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to download Docker binaries. Status code: " + response.statusCode() + " from:  " + downloadUrl);
        }

        try (InputStream is = response.body();
             GzipCompressorInputStream gzis = new GzipCompressorInputStream(is);
             TarArchiveInputStream tis = new TarArchiveInputStream(gzis)) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                if (!tis.canReadEntryData(entry)) continue;
                Path outputPath = DOCKER_RUNTIME_PATH.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(tis, outputPath);
                    if (entry.getName().endsWith("docker") || entry.getName().endsWith("dockerd")) {
                        Set<PosixFilePermission> perms = new HashSet<>(Set.of(
                                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                                PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE));
                        Files.setPosixFilePermissions(outputPath, perms);
                    }
                }
            }
        }
    }

    private String getCurrentVersion() {
        try {
            return Files.readString(VERSION_FILE);
        } catch (IOException e) {
            return "none";
        }
    }

    private void setCurrentVersion(String version) throws IOException {
        Files.createDirectories(VERSION_FILE.getParent());
        Files.writeString(VERSION_FILE, version);
    }
}