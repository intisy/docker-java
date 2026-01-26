package io.github.intisy.docker.command.volume;

import io.github.intisy.docker.exception.ConflictException;
import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Command to remove a volume.
 *
 * @author Finn Birich
 */
public class RemoveVolumeCmd {
    private final DockerHttpClient client;
    private final String volumeName;
    private boolean force = false;

    public RemoveVolumeCmd(DockerHttpClient client, String volumeName) {
        this.client = client;
        this.volumeName = volumeName;
    }

    /**
     * Force removal of the volume.
     */
    public RemoveVolumeCmd withForce(boolean force) {
        this.force = force;
        return this;
    }

    /**
     * Execute the command.
     */
    public void exec() {
        try {
            Map<String, String> queryParams = new HashMap<>();
            if (force) {
                queryParams.put("force", "true");
            }

            DockerResponse response = client.delete("/volumes/" + volumeName, queryParams);
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Volume not found: " + volumeName);
            }
            if (response.getStatusCode() == 409) {
                throw new ConflictException("Volume is in use: " + volumeName);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to remove volume: " + response.getBody(), response.getStatusCode());
            }
        } catch (IOException e) {
            throw new DockerException("Failed to remove volume", e);
        }
    }
}
