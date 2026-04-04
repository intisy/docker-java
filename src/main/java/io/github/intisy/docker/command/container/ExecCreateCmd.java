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

    public ExecCreateCmd withCmd(String... cmd) {
        config.setCmd(cmd);
        return this;
    }

    public ExecCreateCmd withCmd(List<String> cmd) {
        config.setCmd(cmd);
        return this;
    }

    public ExecCreateCmd withAttachStdin(boolean attachStdin) {
        config.setAttachStdin(attachStdin);
        return this;
    }

    public ExecCreateCmd withAttachStdout(boolean attachStdout) {
        config.setAttachStdout(attachStdout);
        return this;
    }

    public ExecCreateCmd withAttachStderr(boolean attachStderr) {
        config.setAttachStderr(attachStderr);
        return this;
    }

    public ExecCreateCmd withTty(boolean tty) {
        config.setTty(tty);
        return this;
    }

    public ExecCreateCmd withEnv(List<String> env) {
        config.setEnv(env);
        return this;
    }

    public ExecCreateCmd withEnv(String key, String value) {
        config.addEnv(key, value);
        return this;
    }

    public ExecCreateCmd withPrivileged(boolean privileged) {
        config.setPrivileged(privileged);
        return this;
    }

    public ExecCreateCmd withUser(String user) {
        config.setUser(user);
        return this;
    }

    public ExecCreateCmd withWorkingDir(String workingDir) {
        config.setWorkingDir(workingDir);
        return this;
    }

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
