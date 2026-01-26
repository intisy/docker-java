package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

/**
 * Response from creating an exec instance.
 *
 * @author Finn Birich
 */
public class ExecCreateResponse {
    @SerializedName("Id")
    private String id;

    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "ExecCreateResponse{" +
                "id='" + id + '\'' +
                '}';
    }
}
