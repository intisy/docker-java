package io.github.intisy.docker.command.image;

import com.google.gson.reflect.TypeToken;
import io.github.intisy.docker.exception.ConflictException;
import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command to remove an image.
 *
 * @author Finn Birich
 */
public class RemoveImageCmd {
    private final DockerHttpClient client;
    private final String imageId;
    private boolean force = false;
    private boolean noPrune = false;

    public RemoveImageCmd(DockerHttpClient client, String imageId) {
        this.client = client;
        this.imageId = imageId;
    }

    /**
     * Force removal of the image.
     */
    public RemoveImageCmd withForce(boolean force) {
        this.force = force;
        return this;
    }

    /**
     * Do not delete untagged parent images.
     */
    public RemoveImageCmd withNoPrune(boolean noPrune) {
        this.noPrune = noPrune;
        return this;
    }

    /**
     * Execute the command.
     */
    public List<DeletedLayer> exec() {
        try {
            Map<String, String> queryParams = new HashMap<>();
            if (force) {
                queryParams.put("force", "true");
            }
            if (noPrune) {
                queryParams.put("noprune", "true");
            }

            DockerResponse response = client.delete("/images/" + imageId, queryParams);
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Image not found: " + imageId);
            }
            if (response.getStatusCode() == 409) {
                throw new ConflictException("Image is in use: " + imageId);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to remove image: " + response.getBody(), response.getStatusCode());
            }

            Type listType = new TypeToken<List<DeletedLayer>>() {}.getType();
            return client.getGson().fromJson(response.getBody(), listType);
        } catch (IOException e) {
            throw new DockerException("Failed to remove image", e);
        }
    }

    /**
     * Represents a deleted image layer.
     */
    public static class DeletedLayer {
        private String Untagged;
        private String Deleted;

        public String getUntagged() {
            return Untagged;
        }

        public String getDeleted() {
            return Deleted;
        }

        @Override
        public String toString() {
            if (Untagged != null) {
                return "Untagged: " + Untagged;
            }
            if (Deleted != null) {
                return "Deleted: " + Deleted;
            }
            return "DeletedLayer{}";
        }
    }
}
