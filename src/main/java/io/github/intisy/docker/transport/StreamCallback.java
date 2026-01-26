package io.github.intisy.docker.transport;

/**
 * Callback interface for streaming Docker responses (logs, pull progress, etc.).
 *
 * @param <T> The type of object being streamed
 * @author Finn Birich
 */
public interface StreamCallback<T> {
    
    /**
     * Called when a new item is received from the stream.
     *
     * @param item The received item
     */
    void onNext(T item);

    /**
     * Called when an error occurs during streaming.
     *
     * @param throwable The error that occurred
     */
    default void onError(Throwable throwable) {
        throwable.printStackTrace();
    }

    /**
     * Called when the stream completes successfully.
     */
    default void onComplete() {}

    /**
     * Called to check if streaming should be cancelled.
     *
     * @return true if streaming should stop, false otherwise
     */
    default boolean isCancelled() {
        return false;
    }
}
