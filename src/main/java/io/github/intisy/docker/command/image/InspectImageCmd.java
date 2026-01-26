package io.github.intisy.docker.command.image;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.model.ImageInspect;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;

/**
 * Command to inspect an image.
 *
 * @author Finn Birich
 */
public class InspectImageCmd {
    private final DockerHttpClient client;
    private final String imageId;

    public InspectImageCmd(DockerHttpClient client, String imageId) {
        this.client = client;
        this.imageId = imageId;
    }

    /**
     * Execute the command.
     */
    public ImageInspect exec() {
        try {
            DockerResponse response = client.get("/images/" + imageId + "/json");
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Image not found: " + imageId);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to inspect image: " + response.getBody(), response.getStatusCode());
            }

            return client.getGson().fromJson(response.getBody(), ImageInspect.class);
        } catch (IOException e) {
            throw new DockerException("Failed to inspect image", e);
        }
    }
}
