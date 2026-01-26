package io.github.intisy.docker.command.image;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.model.PullResponse;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.StreamCallback;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Command to pull an image.
 *
 * @author Finn Birich
 */
public class PullImageCmd {
    private final DockerHttpClient client;
    private final String image;
    private String tag = "latest";
    private String platform;
    private String authHeader;

    public PullImageCmd(DockerHttpClient client, String image) {
        this.client = client;
        if (image.contains(":")) {
            int colonIndex = image.lastIndexOf(":");
            if (colonIndex > image.lastIndexOf("/")) {
                this.image = image.substring(0, colonIndex);
                this.tag = image.substring(colonIndex + 1);
            } else {
                this.image = image;
            }
        } else {
            this.image = image;
        }
    }

    /**
     * Set the tag to pull.
     */
    public PullImageCmd withTag(String tag) {
        this.tag = tag;
        return this;
    }

    /**
     * Set the platform (e.g., linux/amd64).
     */
    public PullImageCmd withPlatform(String platform) {
        this.platform = platform;
        return this;
    }

    /**
     * Set the authentication header (base64 encoded JSON).
     */
    public PullImageCmd withAuthHeader(String authHeader) {
        this.authHeader = authHeader;
        return this;
    }

    /**
     * Execute the command and wait for completion.
     */
    public void exec() {
        exec(new StreamCallback<PullResponse>() {
            @Override
            public void onNext(PullResponse item) {
                System.out.println(item);
            }
        });
    }

    /**
     * Execute the command with timeout.
     */
    public boolean exec(long timeout, TimeUnit unit) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        exec(new StreamCallback<PullResponse>() {
            @Override
            public void onNext(PullResponse item) {
                if (item.isError()) {
                    error.set(new DockerException(item.getError()));
                }
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(timeout, unit);
            if (error.get() != null) {
                throw new DockerException("Pull failed: " + error.get().getMessage(), error.get());
            }
            return completed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Execute the command with a callback for progress updates.
     */
    public void exec(StreamCallback<PullResponse> callback) {
        try {
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("fromImage", image);
            queryParams.put("tag", tag);
            if (platform != null) {
                queryParams.put("platform", platform);
            }

            client.postStream("/images/create", queryParams, new StreamCallback<String>() {
                @Override
                public void onNext(String item) {
                    try {
                        PullResponse response = client.getGson().fromJson(item, PullResponse.class);
                        if (response != null) {
                            callback.onNext(response);
                            if (response.isError()) {
                                callback.onError(new DockerException(response.getError()));
                            }
                        }
                    } catch (Exception e) {}
                }

                @Override
                public void onError(Throwable throwable) {
                    callback.onError(throwable);
                }

                @Override
                public void onComplete() {
                    callback.onComplete();
                }

                @Override
                public boolean isCancelled() {
                    return callback.isCancelled();
                }
            });
        } catch (IOException e) {
            callback.onError(new DockerException("Failed to pull image", e));
        }
    }
}
