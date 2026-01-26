package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response from creating a container.
 *
 * @author Finn Birich
 */
public class CreateContainerResponse {
    @SerializedName("Id")
    private String id;

    @SerializedName("Warnings")
    private List<String> warnings;

    public String getId() {
        return id;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    @Override
    public String toString() {
        return "CreateContainerResponse{" +
                "id='" + id + '\'' +
                ", warnings=" + warnings +
                '}';
    }
}
