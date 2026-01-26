package io.github.intisy.docker.exception;

/**
 * Base exception for Docker-related errors.
 *
 * @author Finn Birich
 */
public class DockerException extends RuntimeException {
    private final int httpStatus;

    public DockerException(String message) {
        this(message, 0);
    }

    public DockerException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public DockerException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = 0;
    }

    public DockerException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
