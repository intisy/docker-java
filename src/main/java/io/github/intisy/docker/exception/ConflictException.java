package io.github.intisy.docker.exception;

/**
 * Exception thrown when there's a conflict (e.g., container name already in use).
 *
 * @author Finn Birich
 */
public class ConflictException extends DockerException {

    public ConflictException(String message) {
        super(message, 409);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, 409, cause);
    }
}
