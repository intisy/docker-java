package io.github.intisy.docker.command.system;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.model.Version;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;

/**
 * Command to get Docker version information.
 *
 * @author Finn Birich
 */
public class VersionCmd {
    private final DockerHttpClient client;

    public VersionCmd(DockerHttpClient client) {
        this.client = client;
    }

    /**
     * Execute the command.
     */
    public Version exec() {
        try {
            DockerResponse response = client.get("/version");
            
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to get version info: " + response.getBody(), response.getStatusCode());
            }

            return client.getGson().fromJson(response.getBody(), Version.class);
        } catch (IOException e) {
            throw new DockerException("Failed to get version info", e);
        }
    }
}
