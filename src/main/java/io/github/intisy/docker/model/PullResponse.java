package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

/**
 * Response from pulling an image.
 *
 * @author Finn Birich
 */
public class PullResponse {
    @SerializedName("status")
    private String status;

    @SerializedName("progressDetail")
    private ProgressDetail progressDetail;

    @SerializedName("progress")
    private String progress;

    @SerializedName("id")
    private String id;

    @SerializedName("error")
    private String error;

    @SerializedName("errorDetail")
    private ErrorDetail errorDetail;

    public String getStatus() {
        return status;
    }

    public ProgressDetail getProgressDetail() {
        return progressDetail;
    }

    public String getProgress() {
        return progress;
    }

    public String getId() {
        return id;
    }

    public String getError() {
        return error;
    }

    public ErrorDetail getErrorDetail() {
        return errorDetail;
    }

    public boolean isError() {
        return error != null || errorDetail != null;
    }

    /**
     * Progress detail.
     */
    public static class ProgressDetail {
        @SerializedName("current")
        private Long current;

        @SerializedName("total")
        private Long total;

        public Long getCurrent() {
            return current;
        }

        public Long getTotal() {
            return total;
        }

        public int getPercentage() {
            if (total == null || total == 0 || current == null) {
                return 0;
            }
            return (int) ((current * 100) / total);
        }
    }

    /**
     * Error detail.
     */
    public static class ErrorDetail {
        @SerializedName("message")
        private String message;

        public String getMessage() {
            return message;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (id != null) {
            sb.append(id).append(": ");
        }
        if (status != null) {
            sb.append(status);
        }
        if (progress != null) {
            sb.append(" ").append(progress);
        }
        if (error != null) {
            sb.append(" ERROR: ").append(error);
        }
        return sb.toString();
    }
}
