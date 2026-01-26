package io.github.intisy.docker.command.container;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;
import io.github.intisy.docker.transport.StreamCallback;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Command to get container logs.
 *
 * @author Finn Birich
 */
public class LogsContainerCmd {
    private final DockerHttpClient client;
    private final String containerId;
    private boolean follow = false;
    private boolean stdout = true;
    private boolean stderr = true;
    private String since;
    private String until;
    private boolean timestamps = false;
    private Integer tail;

    public LogsContainerCmd(DockerHttpClient client, String containerId) {
        this.client = client;
        this.containerId = containerId;
    }

    /**
     * Follow log output (streaming).
     */
    public LogsContainerCmd withFollow(boolean follow) {
        this.follow = follow;
        return this;
    }

    /**
     * Include stdout.
     */
    public LogsContainerCmd withStdout(boolean stdout) {
        this.stdout = stdout;
        return this;
    }

    /**
     * Include stderr.
     */
    public LogsContainerCmd withStderr(boolean stderr) {
        this.stderr = stderr;
        return this;
    }

    /**
     * Show logs since timestamp (Unix timestamp or RFC3339).
     */
    public LogsContainerCmd withSince(String since) {
        this.since = since;
        return this;
    }

    /**
     * Show logs until timestamp (Unix timestamp or RFC3339).
     */
    public LogsContainerCmd withUntil(String until) {
        this.until = until;
        return this;
    }

    /**
     * Add timestamps to output.
     */
    public LogsContainerCmd withTimestamps(boolean timestamps) {
        this.timestamps = timestamps;
        return this;
    }

    /**
     * Only return the last N lines.
     */
    public LogsContainerCmd withTail(int tail) {
        this.tail = tail;
        return this;
    }

    /**
     * Execute the command and return logs as a string.
     */
    public String exec() {
        try {
            Map<String, String> queryParams = buildQueryParams();
            queryParams.put("follow", "false");

            DockerResponse response = client.get("/containers/" + containerId + "/logs", queryParams);
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Container not found: " + containerId);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to get container logs: " + response.getBody(), response.getStatusCode());
            }

            return parseLogOutput(response.getBody());
        } catch (IOException e) {
            throw new DockerException("Failed to get container logs", e);
        }
    }

    /**
     * Execute the command with streaming callback.
     */
    public void exec(StreamCallback<String> callback) {
        try {
            Map<String, String> queryParams = buildQueryParams();
            if (follow) {
                queryParams.put("follow", "true");
            }

            client.getStream("/containers/" + containerId + "/logs", queryParams, new StreamCallback<String>() {
                @Override
                public void onNext(String item) {
                    callback.onNext(parseLogLine(item));
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
            callback.onError(new DockerException("Failed to stream container logs", e));
        }
    }

    private Map<String, String> buildQueryParams() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("stdout", String.valueOf(stdout));
        queryParams.put("stderr", String.valueOf(stderr));
        if (since != null) {
            queryParams.put("since", since);
        }
        if (until != null) {
            queryParams.put("until", until);
        }
        queryParams.put("timestamps", String.valueOf(timestamps));
        if (tail != null) {
            queryParams.put("tail", String.valueOf(tail));
        }
        return queryParams;
    }

    private String parseLogOutput(String rawOutput) {
        StringBuilder result = new StringBuilder();
        String[] lines = rawOutput.split("\n");
        for (String line : lines) {
            result.append(parseLogLine(line)).append("\n");
        }
        return result.toString().trim();
    }

    private String parseLogLine(String line) {
        if (line.length() > 8) {
            char firstChar = line.charAt(0);
            if (firstChar == 1 || firstChar == 2) {
                return line.substring(8);
            }
        }
        return line;
    }
}
