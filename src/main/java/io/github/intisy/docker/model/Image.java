package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * Represents a Docker image.
 *
 * @author Finn Birich
 */
public class Image {
    @SerializedName("Id")
    private String id;

    @SerializedName("ParentId")
    private String parentId;

    @SerializedName("RepoTags")
    private List<String> repoTags;

    @SerializedName("RepoDigests")
    private List<String> repoDigests;

    @SerializedName("Created")
    private Long created;

    @SerializedName("Size")
    private Long size;

    @SerializedName("VirtualSize")
    private Long virtualSize;

    @SerializedName("SharedSize")
    private Long sharedSize;

    @SerializedName("Labels")
    private Map<String, String> labels;

    @SerializedName("Containers")
    private Integer containers;

    public String getId() {
        return id;
    }

    public String getParentId() {
        return parentId;
    }

    public List<String> getRepoTags() {
        return repoTags;
    }

    public List<String> getRepoDigests() {
        return repoDigests;
    }

    public Long getCreated() {
        return created;
    }

    public Long getSize() {
        return size;
    }

    public Long getVirtualSize() {
        return virtualSize;
    }

    public Long getSharedSize() {
        return sharedSize;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public Integer getContainers() {
        return containers;
    }

    @Override
    public String toString() {
        return "Image{" +
                "id='" + (id != null ? id.substring(0, Math.min(12, id.length())) : "null") + '\'' +
                ", repoTags=" + repoTags +
                ", size=" + size +
                '}';
    }
}
