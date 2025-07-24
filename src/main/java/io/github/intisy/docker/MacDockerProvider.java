//package io.github.intisy.docker; TODO add this
//
//import com.github.dockerjava.api.DockerClient;
//import com.github.dockerjava.core.DefaultDockerClientConfig;
//import com.github.dockerjava.core.DockerClientImpl;
//import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
//import com.github.dockerjava.transport.DockerHttpClient;
//import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
//import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
//import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
//
//import java.io.File;
//import java.io.IOException;
//import java.io.InputStream;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.nio.file.StandardCopyOption;
//import java.util.concurrent.TimeUnit;
//
///**
// * @author Finn Birich
// */
//@SuppressWarnings({"ResultOfMethodCallIgnored", "BusyWait"})
//public class MacDockerProvider implements DockerProvider {
//    private static final String DOCKER_DOWNLOAD_URL = "https://download.docker.com/mac/static/stable/%s/docker-%s.tgz";
//    private static final Path DOCKER_PATH = DOCKER_DIR.resolve("docker/dockerd");
//    private static final Path DOCKER_SOCKET_PATH = DOCKER_DIR.resolve("run/docker.sock");
//    private static final Path DOCKER_VERSION_FILE = DOCKER_DIR.resolve(".docker-version");
//
//    private DockerClient dockerClient;
//    private Process dockerProcess;
//
//    public void ensureInstalled() throws IOException {
//        boolean autoUpdate = Boolean.parseBoolean(System.getProperty("docker.auto.update", "true"));
//        String latestVersion = DockerVersionFetcher.getLatestVersion();
//        boolean needsUpdate = true;
//
//        if (Files.exists(DOCKER_PATH)) {
//            if (autoUpdate && Files.exists(DOCKER_VERSION_FILE)) {
//                String installedVersion = new String(Files.readAllBytes(DOCKER_VERSION_FILE)).trim();
//                if (!installedVersion.equals(latestVersion)) {
//                    System.out.println("Newer Docker version available. Updating from " + installedVersion + " to " + latestVersion);
//                } else {
//                    System.out.println("Docker is up to date. (" + latestVersion + ")");
//                    needsUpdate = false;
//                }
//            } else if (!autoUpdate) {
//                needsUpdate = false;
//            }
//        }
//
//        if (needsUpdate) {
//            System.out.println("Docker installation is incomplete or outdated. Re-installing...");
//
//            Path dockerInstallDir = DOCKER_DIR.resolve("docker");
//            if (Files.exists(dockerInstallDir)) {
//                try (java.util.stream.Stream<Path> walk = Files.walk(dockerInstallDir)) {
//                    walk.sorted(java.util.Comparator.reverseOrder())
//                            .map(Path::toFile)
//                            .forEach(File::delete);
//                }
//            }
//
//            String arch = getArch();
//            String dockerUrl = String.format(DOCKER_DOWNLOAD_URL, arch, latestVersion);
//            downloadAndExtract(dockerUrl);
//            Files.write(DOCKER_VERSION_FILE, latestVersion.getBytes());
//        }
//    }
//
//    private String getArch() {
//        String osArch = System.getProperty("os.arch");
//        switch (osArch) {
//            case "amd64":
//                return "x86_64";
//            case "aarch64":
//                return "aarch64";
//            default:
//                throw new UnsupportedOperationException("Unsupported architecture: " + osArch);
//        }
//    }
//
//    @Override
//    public void start() throws IOException, InterruptedException {
//        this.dockerClient = tryConnectToExistingDocker();
//        if (this.dockerClient != null) {
//            return;
//        }
//        System.out.println("No running Docker daemon found or connection failed. Starting a managed Docker daemon.");
//
//        ensureInstalled();
//
//        Path runDir = DOCKER_DIR.resolve("run");
//        Path dataDir = DOCKER_DIR.resolve("data");
//        runDir.toFile().mkdirs();
//        dataDir.toFile().mkdirs();
//
//        ProcessBuilder pb = new ProcessBuilder(DOCKER_PATH.toString(), "--config-file", DOCKER_DIR.resolve("config.json").toString());
//        pb.environment().put("XDG_RUNTIME_DIR", runDir.toString());
//        pb.environment().put("XDG_DATA_HOME", dataDir.toString());
//        pb.redirectErrorStream(true);
//        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
//        dockerProcess = pb.start();
//
//        if (!waitForSocket()) {
//            throw new RuntimeException("Docker daemon failed to create socket in time.");
//        }
//    }
//
//    private DockerClient tryConnectToExistingDocker() {
//        String systemSocketPath = "/var/run/docker.sock";
//        File systemSocket = new File(systemSocketPath);
//
//        if (systemSocket.exists()) {
//            System.out.println("Found existing Docker socket at " + systemSocketPath + ". Attempting to connect.");
//            try {
//                DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
//                        .withDockerHost("unix://" + systemSocketPath).build();
//
//                DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
//                        .dockerHost(config.getDockerHost())
//                        .sslConfig(config.getSSLConfig())
//                        .build();
//
//                DockerClient client = DockerClientImpl.getInstance(config, httpClient);
//                client.pingCmd().exec(); // Verify connection
//                System.out.println("Successfully connected to existing Docker daemon.");
//                return client;
//            } catch (Exception e) {
//                System.err.println("Failed to connect to existing Docker daemon at " + systemSocketPath + ": " + e.getMessage());
//            }
//        }
//        return null;
//    }
//
//    @Override
//    public DockerClient getClient() {
//        if (this.dockerClient == null) {
//            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
//                    .withDockerHost("unix://" + DOCKER_SOCKET_PATH).build();
//
//            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
//                    .dockerHost(config.getDockerHost())
//                    .sslConfig(config.getSSLConfig())
//                    .build();
//
//            this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
//        }
//        return this.dockerClient;
//    }
//
//    @Override
//    public void stop() {
//        if (dockerProcess != null) {
//            dockerProcess.destroy();
//            try {
//                dockerProcess.waitFor(10, TimeUnit.SECONDS);
//            } catch (InterruptedException e) {
//                dockerProcess.destroyForcibly();
//            }
//        }
//    }
//
//    @SuppressWarnings("deprecation")
//    private void downloadAndExtract(String urlString) throws IOException {
//        System.out.println("Downloading and extracting " + urlString + "...");
//        URL url = new URL(urlString);
//        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
//        connection.setInstanceFollowRedirects(true);
//        connection.setRequestMethod("GET");
//
//        if (connection.getResponseCode() != 200) {
//            throw new IOException("Failed to download file from " + urlString + ". Status code: " + connection.getResponseCode());
//        }
//
//        try (InputStream is = connection.getInputStream();
//             GzipCompressorInputStream gzis = new GzipCompressorInputStream(is);
//             TarArchiveInputStream tis = new TarArchiveInputStream(gzis)) {
//            TarArchiveEntry entry;
//            while ((entry = tis.getNextTarEntry()) != null) {
//                if (!tis.canReadEntryData(entry)) continue;
//                Path outputPath = MacDockerProvider.DOCKER_DIR.resolve(entry.getName());
//                if (entry.isDirectory()) {
//                    Files.createDirectories(outputPath);
//                } else {
//                    Files.createDirectories(outputPath.getParent());
//                    Files.copy(tis, outputPath, StandardCopyOption.REPLACE_EXISTING);
//                    if (entry.getName().endsWith("dockerd")) {
//                        outputPath.toFile().setExecutable(true);
//                    }
//                }
//            }
//        }
//    }
//
//    private boolean waitForSocket() throws InterruptedException {
//        System.out.println("Waiting for Docker socket to be available at " + DOCKER_SOCKET_PATH + "...");
//        long timeoutMillis = TimeUnit.SECONDS.toMillis(30);
//        long startTime = System.currentTimeMillis();
//        while (System.currentTimeMillis() - startTime < timeoutMillis) {
//            if (Files.exists(DOCKER_SOCKET_PATH)) {
//                System.out.println("Docker socket found.");
//                return true;
//            }
//            Thread.sleep(500);
//        }
//        System.err.println("Timed out waiting for Docker socket.");
//        return false;
//    }
//}
