package io.github.intisy.docker.command.container;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.model.ExecConfig;
import io.github.intisy.docker.model.ExecCreateResponse;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command to create an exec instance.
 *
 * @author Finn Birich
 */
public class ExecCreateCmd {
    private final DockerHttpClient client;
    private final String containerId;
    private final ExecConfig config;

    public ExecCreateCmd(DockerHttpClient client, String containerId) {
        this.client = client;
        this.containerId = containerId;
        this.config = new ExecConfig();
        this.config.setAttachStdout(true);
        this.config.setAttachStderr(true);
    }

    /**
     * Set the command to execute.
     */
    public ExecCreateCmd withCmd(String... cmd) {
        config.setCmd(cmd);
        return this;
    }

    /**
     * Set the command to execute.
     */
    public ExecCreateCmd withCmd(List<String> cmd) {
        config.setCmd(cmd);
        return this;
    }

    /**
     * Attach stdin.
     */
    public ExecCreateCmd withAttachStdin(boolean attachStdin) {
        config.setAttachStdin(attachStdin);
        return this;
    }

    /**
     * Attach stdout.
     */
    public ExecCreateCmd withAttachStdout(boolean attachStdout) {
        config.setAttachStdout(attachStdout);
        return this;
    }

    /**
     * Attach stderr.
     */
    public ExecCreateCmd withAttachStderr(boolean attachStderr) {
        config.setAttachStderr(attachStderr);
        return this;
    }

    /**
     * Allocate a pseudo-TTY.
     */
    public ExecCreateCmd withTty(boolean tty) {
        config.setTty(tty);
        return this;
    }

    /**
     * Set environment variables.
     */
    public ExecCreateCmd withEnv(List<String> env) {
        config.setEnv(env);
        return this;
    }

    /**
     * Add an environment variable.
     */
    public ExecCreateCmd withEnv(String key, String value) {
        config.addEnv(key, value);
        return this;
    }

    /**
     * Run as privileged.
     */
    public ExecCreateCmd withPrivileged(boolean privileged) {
        config.setPrivileged(privileged);
        return this;
    }

    /**
     * Set the user.
     */
    public ExecCreateCmd withUser(String user) {
        config.setUser(user);
        return this;
    }

    /**
     * Set the working directory.
     */
    public ExecCreateCmd withWorkingDir(String workingDir) {
        config.setWorkingDir(workingDir);
        return this;
    }

    /**
     * Execute the command.
     */
    public ExecCreateResponse exec() {
        try {
            DockerResponse response = client.post("/containers/" + containerId + "/exec", config);
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Container not found: " + containerId);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to create exec: " + response.getBody(), response.getStatusCode());
            }

            return client.getGson().fromJson(response.getBody(), ExecCreateResponse.class);
        } catch (IOException e) {
            throw new DockerException("Failed to create exec", e);
        }
    }
}
