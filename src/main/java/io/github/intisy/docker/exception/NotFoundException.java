package io.github.intisy.docker.exception;

/**
 * Exception thrown when a Docker resource (container, image, volume, etc.) is not found.
 *
 * @author Finn Birich
 */
public class NotFoundException extends DockerException {

    public NotFoundException(String message) {
        super(message, 404);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, 404, cause);
    }
}
