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
 * Command to start an exec instance.
 *
 * @author Finn Birich
 */
public class ExecStartCmd {
    private final DockerHttpClient client;
    private final String execId;
    private boolean detach = false;
    private boolean tty = false;

    public ExecStartCmd(DockerHttpClient client, String execId) {
        this.client = client;
        this.execId = execId;
    }

    /**
     * Detach from the exec command.
     */
    public ExecStartCmd withDetach(boolean detach) {
        this.detach = detach;
        return this;
    }

    /**
     * Allocate a pseudo-TTY.
     */
    public ExecStartCmd withTty(boolean tty) {
        this.tty = tty;
        return this;
    }

    /**
     * Execute the command (detached mode).
     */
    public void exec() {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("Detach", true);
            body.put("Tty", tty);

            DockerResponse response = client.post("/exec/" + execId + "/start", body);
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Exec instance not found: " + execId);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to start exec: " + response.getBody(), response.getStatusCode());
            }
        } catch (IOException e) {
            throw new DockerException("Failed to start exec", e);
        }
    }

    /**
     * Execute the command and stream output.
     */
    public void exec(StreamCallback<String> callback) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("Detach", false);
            body.put("Tty", tty);

            DockerResponse response = client.post("/exec/" + execId + "/start", body);
            
            if (response.getStatusCode() == 404) {
                callback.onError(new NotFoundException("Exec instance not found: " + execId));
                return;
            }
            if (!response.isSuccessful()) {
                callback.onError(new DockerException("Failed to start exec: " + response.getBody(), response.getStatusCode()));
                return;
            }

            String output = response.getBody();
            if (output != null && !output.isEmpty()) {
                for (String line : output.split("\n")) {
                    callback.onNext(line);
                }
            }
            callback.onComplete();
        } catch (IOException e) {
            callback.onError(new DockerException("Failed to start exec", e));
        }
    }
}
