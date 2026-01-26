package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

/**
 * Response from waiting for a container.
 *
 * @author Finn Birich
 */
public class WaitResponse {
    @SerializedName("StatusCode")
    private Integer statusCode;

    @SerializedName("Error")
    private WaitError error;

    public Integer getStatusCode() {
        return statusCode;
    }

    public WaitError getError() {
        return error;
    }

    /**
     * Error detail from wait.
     */
    public static class WaitError {
        @SerializedName("Message")
        private String message;

        public String getMessage() {
            return message;
        }
    }

    @Override
    public String toString() {
        return "WaitResponse{" +
                "statusCode=" + statusCode +
                ", error=" + (error != null ? error.message : "null") +
                '}';
    }
}
