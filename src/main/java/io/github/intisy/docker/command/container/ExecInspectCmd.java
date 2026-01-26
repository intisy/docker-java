package io.github.intisy.docker.command.container;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.model.ExecInspect;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;

/**
 * Command to inspect an exec instance.
 *
 * @author Finn Birich
 */
public class ExecInspectCmd {
    private final DockerHttpClient client;
    private final String execId;

    public ExecInspectCmd(DockerHttpClient client, String execId) {
        this.client = client;
        this.execId = execId;
    }

    /**
     * Execute the command.
     */
    public ExecInspect exec() {
        try {
            DockerResponse response = client.get("/exec/" + execId + "/json");
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Exec instance not found: " + execId);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to inspect exec: " + response.getBody(), response.getStatusCode());
            }

            return client.getGson().fromJson(response.getBody(), ExecInspect.class);
        } catch (IOException e) {
            throw new DockerException("Failed to inspect exec", e);
        }
    }
}
