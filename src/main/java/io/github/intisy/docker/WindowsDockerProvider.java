package io.github.intisy.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PingCmd;
import com.github.dockerjava.core.DockerClientBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * @author Finn Birich
 */
public class WindowsDockerProvider implements DockerProvider {
    private static final Path DOCKER_DESKTOP_PATH = Paths.get("C:", "Program Files", "Docker", "Docker", "Docker Desktop.exe");
    private static final String DOCKER_DESKTOP_INSTALLER_URL = "https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe";
    private static final Path DOCKER_INSTALLER_PATH = Paths.get(System.getProperty("java.io.tmpdir"), "DockerDesktopInstaller.exe");

    @Override
    public void ensureInstalled() throws IOException, InterruptedException {
        if (Files.exists(DOCKER_DESKTOP_PATH)) {
            return;
        }

        System.out.println("Docker Desktop not found. Downloading installer...");
        downloadFile(DOCKER_DESKTOP_INSTALLER_URL, DOCKER_INSTALLER_PATH);

        System.out.println("Starting Docker Desktop installer. This may take a while...");
        ProcessBuilder pb = new ProcessBuilder(DOCKER_INSTALLER_PATH.toString(), "install", "--quiet");
        Process process = pb.start();
        int exitCode = process.waitFor();

        Files.deleteIfExists(DOCKER_INSTALLER_PATH);

        if (exitCode != 0) {
            throw new IOException("Docker Desktop installation failed with exit code: " + exitCode);
        }

        System.out.println("Installation finished. Please start Docker Desktop manually to complete the setup.");
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
            Files.copy(in, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void start() {
        try (PingCmd pingCmd = getClient().pingCmd()) {
            pingCmd.exec();
        } catch (Exception e) {
            System.err.println("Could not connect to Docker Desktop. Please ensure it is running.");
            throw new RuntimeException("Docker Desktop is not running.", e);
        }
    }

    @Override
    public DockerClient getClient() {
        return DockerClientBuilder.getInstance().build();
    }

    @Override
    public void stop() {
    }
}