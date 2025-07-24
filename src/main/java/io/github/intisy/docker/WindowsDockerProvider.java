package io.github.intisy.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Finn Birich
 */
public class WindowsDockerProvider extends DockerProvider {
    private static final String DOCKER_DOWNLOAD_URL = "https://download.docker.com/win/static/stable/%s/docker-%s.zip";
    private static final Path DOCKER_PATH = DOCKER_DIR.resolve("docker/dockerd.exe");
    private static final Path DOCKER_PIPE_PATH = Paths.get("\\\\.\\pipe\\docker_engine");
    private static final Path DOCKER_VERSION_FILE = DOCKER_DIR.resolve(".docker-version");

    private DockerClient dockerClient;
    private Process dockerProcess;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void ensureInstalled() throws IOException {
        boolean autoUpdate = Boolean.parseBoolean(System.getProperty("docker.auto.update", "true"));
        String latestVersion = DockerVersionFetcher.getLatestVersion();
        boolean needsUpdate = true;

        if (Files.exists(DOCKER_PATH)) {
            if (autoUpdate && Files.exists(DOCKER_VERSION_FILE)) {
                String installedVersion = new String(Files.readAllBytes(DOCKER_VERSION_FILE)).trim();
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
        if ("amd64".equals(osArch)) {
            return "x86_64";
        }
        throw new UnsupportedOperationException("Unsupported architecture: " + osArch);
    }

    private void downloadAndExtract(String urlString) throws IOException {
        System.out.println("Downloading and extracting " + urlString + "...");
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");

        if (connection.getResponseCode() != 200) {
            throw new IOException("Failed to download file from " + urlString + ". Status code: " + connection.getResponseCode());
        }

        try (InputStream is = connection.getInputStream();
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    Path outputPath = DOCKER_DIR.resolve(entry.getName());
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(zis, outputPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    @Override
    public void start() throws IOException, InterruptedException {
        this.dockerClient = tryConnectToExistingDocker();
        if (this.dockerClient != null) {
            return;
        }
        System.out.println("No running Docker daemon found or connection failed. Starting a managed Docker daemon.");

        ensureInstalled();

        ProcessBuilder pb = new ProcessBuilder(DOCKER_PATH.toString(), "-H", "npipe://" + DOCKER_PIPE_PATH.toString().replace("\\", "/"));
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        dockerProcess = pb.start();

        if (!waitForPipe()) {
            throw new RuntimeException("Docker daemon failed to create named pipe in time.");
        }
    }

    private DockerClient tryConnectToExistingDocker() {
        String systemPipePath = "//./pipe/docker_engine";
        File systemPipe = new File(systemPipePath);

        if (systemPipe.exists()) {
            System.out.println("Found existing Docker pipe at " + systemPipePath + ". Attempting to connect.");
            try {
                DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                        .withDockerHost("npipe://" + systemPipePath).build();

                DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                        .dockerHost(config.getDockerHost())
                        .sslConfig(config.getSSLConfig())
                        .build();

                DockerClient client = DockerClientImpl.getInstance(config, httpClient);
                client.pingCmd().exec(); // Verify connection
                System.out.println("Successfully connected to existing Docker daemon.");
                return client;
            } catch (Exception e) {
                System.err.println("Failed to connect to existing Docker daemon at " + systemPipePath + ": " + e.getMessage());
            }
        }
        return null;
    }

    @Override
    public DockerClient getClient() {
        if (this.dockerClient == null) {
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost("npipe://" + DOCKER_PIPE_PATH.toString().replace("\\", "/")).build();

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

    @SuppressWarnings("BusyWait")
    private boolean waitForPipe() throws InterruptedException {
        System.out.println("Waiting for Docker pipe to be available at " + DOCKER_PIPE_PATH + "...");
        long timeoutMillis = TimeUnit.SECONDS.toMillis(30);
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (Files.exists(DOCKER_PIPE_PATH)) {
                System.out.println("Docker pipe found.");
                return true;
            }
            Thread.sleep(500);
        }
        System.err.println("Timed out waiting for Docker pipe.");
        return false;
    }
}