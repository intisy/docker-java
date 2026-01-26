package io.github.intisy.docker.exception;

/**
 * Exception thrown when a resource was not modified (e.g., container already stopped).
 *
 * @author Finn Birich
 */
public class NotModifiedException extends DockerException {

    public NotModifiedException(String message) {
        super(message, 304);
    }

    public NotModifiedException(String message, Throwable cause) {
        super(message, 304, cause);
    }
}
