package io.github.intisy.docker.command.volume;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.model.Volume;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;

/**
 * Command to inspect a volume.
 *
 * @author Finn Birich
 */
public class InspectVolumeCmd {
    private final DockerHttpClient client;
    private final String volumeName;

    public InspectVolumeCmd(DockerHttpClient client, String volumeName) {
        this.client = client;
        this.volumeName = volumeName;
    }

    /**
     * Execute the command.
     */
    public Volume exec() {
        try {
            DockerResponse response = client.get("/volumes/" + volumeName);
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Volume not found: " + volumeName);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to inspect volume: " + response.getBody(), response.getStatusCode());
            }

            return client.getGson().fromJson(response.getBody(), Volume.class);
        } catch (IOException e) {
            throw new DockerException("Failed to inspect volume", e);
        }
    }
}
