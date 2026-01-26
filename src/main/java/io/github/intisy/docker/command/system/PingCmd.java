package io.github.intisy.docker.command.system;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;

/**
 * Command to ping the Docker daemon.
 *
 * @author Finn Birich
 */
public class PingCmd {
    private final DockerHttpClient client;

    public PingCmd(DockerHttpClient client) {
        this.client = client;
    }

    /**
     * Execute the command.
     * @return true if the daemon is reachable
     */
    public boolean exec() {
        try {
            DockerResponse response = client.get("/_ping");
            return response.isSuccessful();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Execute the command and throw an exception if the daemon is not reachable.
     */
    public void execOrThrow() {
        try {
            DockerResponse response = client.get("/_ping");
            if (!response.isSuccessful()) {
                throw new DockerException("Docker daemon ping failed: " + response.getBody(), response.getStatusCode());
            }
        } catch (IOException e) {
            throw new DockerException("Docker daemon is not reachable", e);
        }
    }
}
