package io.github.intisy.docker.command.image;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Command to tag an image.
 *
 * @author Finn Birich
 */
public class TagImageCmd {
    private final DockerHttpClient client;
    private final String imageId;
    private String repo;
    private String tag;

    public TagImageCmd(DockerHttpClient client, String imageId) {
        this.client = client;
        this.imageId = imageId;
    }

    /**
     * Set the repository name.
     */
    public TagImageCmd withRepo(String repo) {
        this.repo = repo;
        return this;
    }

    /**
     * Set the tag name.
     */
    public TagImageCmd withTag(String tag) {
        this.tag = tag;
        return this;
    }

    /**
     * Set repository and tag from a combined name:tag format.
     */
    public TagImageCmd withImageNameTag(String imageNameTag) {
        if (imageNameTag.contains(":")) {
            int colonIndex = imageNameTag.lastIndexOf(":");
            this.repo = imageNameTag.substring(0, colonIndex);
            this.tag = imageNameTag.substring(colonIndex + 1);
        } else {
            this.repo = imageNameTag;
            this.tag = "latest";
        }
        return this;
    }

    /**
     * Execute the command.
     */
    public void exec() {
        try {
            Map<String, String> queryParams = new HashMap<>();
            if (repo != null) {
                queryParams.put("repo", repo);
            }
            if (tag != null) {
                queryParams.put("tag", tag);
            }

            DockerResponse response = client.post("/images/" + imageId + "/tag", queryParams, null);
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Image not found: " + imageId);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to tag image: " + response.getBody(), response.getStatusCode());
            }
        } catch (IOException e) {
            throw new DockerException("Failed to tag image", e);
        }
    }
}
