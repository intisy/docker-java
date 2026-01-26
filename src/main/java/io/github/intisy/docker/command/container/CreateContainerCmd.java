package io.github.intisy.docker.command.container;

import io.github.intisy.docker.exception.ConflictException;
import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.exception.NotFoundException;
import io.github.intisy.docker.model.*;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;
import java.util.*;

/**
 * Command to create a container.
 *
 * @author Finn Birich
 */
public class CreateContainerCmd {
    private final DockerHttpClient client;
    private final ContainerConfig config;
    private String name;

    public CreateContainerCmd(DockerHttpClient client, String image) {
        this.client = client;
        this.config = new ContainerConfig();
        this.config.setImage(image);
    }

    /**
     * Set the container name.
     */
    public CreateContainerCmd withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Set the hostname.
     */
    public CreateContainerCmd withHostname(String hostname) {
        config.setHostname(hostname);
        return this;
    }

    /**
     * Set the user.
     */
    public CreateContainerCmd withUser(String user) {
        config.setUser(user);
        return this;
    }

    /**
     * Set environment variables.
     */
    public CreateContainerCmd withEnv(List<String> env) {
        config.setEnv(env);
        return this;
    }

    /**
     * Add an environment variable.
     */
    public CreateContainerCmd withEnv(String key, String value) {
        config.addEnv(key, value);
        return this;
    }

    /**
     * Set the command to run.
     */
    public CreateContainerCmd withCmd(String... cmd) {
        config.setCmd(cmd);
        return this;
    }

    /**
     * Set the command to run.
     */
    public CreateContainerCmd withCmd(List<String> cmd) {
        config.setCmd(cmd);
        return this;
    }

    /**
     * Set the entrypoint.
     */
    public CreateContainerCmd withEntrypoint(String... entrypoint) {
        config.setEntrypoint(entrypoint);
        return this;
    }

    /**
     * Set the working directory.
     */
    public CreateContainerCmd withWorkingDir(String workingDir) {
        config.setWorkingDir(workingDir);
        return this;
    }

    /**
     * Add a label.
     */
    public CreateContainerCmd withLabel(String key, String value) {
        config.addLabel(key, value);
        return this;
    }

    /**
     * Set labels.
     */
    public CreateContainerCmd withLabels(Map<String, String> labels) {
        config.setLabels(labels);
        return this;
    }

    /**
     * Add an exposed port.
     */
    public CreateContainerCmd withExposedPort(ExposedPort port) {
        config.addExposedPort(port);
        return this;
    }

    /**
     * Set TTY mode.
     */
    public CreateContainerCmd withTty(boolean tty) {
        config.setTty(tty);
        return this;
    }

    /**
     * Set stdin open.
     */
    public CreateContainerCmd withStdinOpen(boolean stdinOpen) {
        config.setOpenStdin(stdinOpen);
        return this;
    }

    /**
     * Set attach stdout.
     */
    public CreateContainerCmd withAttachStdout(boolean attach) {
        config.setAttachStdout(attach);
        return this;
    }

    /**
     * Set attach stderr.
     */
    public CreateContainerCmd withAttachStderr(boolean attach) {
        config.setAttachStderr(attach);
        return this;
    }

    /**
     * Set the host configuration.
     */
    public CreateContainerCmd withHostConfig(HostConfig hostConfig) {
        config.setHostConfig(hostConfig);
        return this;
    }

    /**
     * Add a port binding.
     */
    public CreateContainerCmd withPortBinding(ExposedPort exposedPort, PortBinding binding) {
        if (config.getHostConfig() == null) {
            config.setHostConfig(new HostConfig());
        }
        config.getHostConfig().addPortBinding(exposedPort, binding);
        config.addExposedPort(exposedPort);
        return this;
    }

    /**
     * Publish all exposed ports.
     */
    public CreateContainerCmd withPublishAllPorts(boolean publishAll) {
        if (config.getHostConfig() == null) {
            config.setHostConfig(new HostConfig());
        }
        config.getHostConfig().setPublishAllPorts(publishAll);
        return this;
    }

    /**
     * Add a bind mount.
     */
    public CreateContainerCmd withBind(String hostPath, String containerPath) {
        if (config.getHostConfig() == null) {
            config.setHostConfig(new HostConfig());
        }
        config.getHostConfig().addBind(hostPath + ":" + containerPath);
        return this;
    }

    /**
     * Add a bind mount with read-only option.
     */
    public CreateContainerCmd withBind(String hostPath, String containerPath, boolean readOnly) {
        if (config.getHostConfig() == null) {
            config.setHostConfig(new HostConfig());
        }
        String bind = hostPath + ":" + containerPath;
        if (readOnly) {
            bind += ":ro";
        }
        config.getHostConfig().addBind(bind);
        return this;
    }

    /**
     * Add a mount.
     */
    public CreateContainerCmd withMount(Mount mount) {
        if (config.getHostConfig() == null) {
            config.setHostConfig(new HostConfig());
        }
        config.getHostConfig().addMount(mount);
        return this;
    }

    /**
     * Set the network mode.
     */
    public CreateContainerCmd withNetworkMode(String networkMode) {
        if (config.getHostConfig() == null) {
            config.setHostConfig(new HostConfig());
        }
        config.getHostConfig().setNetworkMode(networkMode);
        return this;
    }

    /**
     * Set privileged mode.
     */
    public CreateContainerCmd withPrivileged(boolean privileged) {
        if (config.getHostConfig() == null) {
            config.setHostConfig(new HostConfig());
        }
        config.getHostConfig().setPrivileged(privileged);
        return this;
    }

    /**
     * Set auto-remove.
     */
    public CreateContainerCmd withAutoRemove(boolean autoRemove) {
        if (config.getHostConfig() == null) {
            config.setHostConfig(new HostConfig());
        }
        config.getHostConfig().setAutoRemove(autoRemove);
        return this;
    }

    /**
     * Set restart policy.
     */
    public CreateContainerCmd withRestartPolicy(HostConfig.RestartPolicy restartPolicy) {
        if (config.getHostConfig() == null) {
            config.setHostConfig(new HostConfig());
        }
        config.getHostConfig().setRestartPolicy(restartPolicy);
        return this;
    }

    /**
     * Set memory limit in bytes.
     */
    public CreateContainerCmd withMemory(long memory) {
        if (config.getHostConfig() == null) {
            config.setHostConfig(new HostConfig());
        }
        config.getHostConfig().setMemory(memory);
        return this;
    }

    /**
     * Add DNS server.
     */
    public CreateContainerCmd withDns(String dns) {
        if (config.getHostConfig() == null) {
            config.setHostConfig(new HostConfig());
        }
        List<String> dnsList = config.getHostConfig().getDns();
        if (dnsList == null) {
            dnsList = new ArrayList<>();
            config.getHostConfig().setDns(dnsList);
        }
        dnsList.add(dns);
        return this;
    }

    /**
     * Add extra host.
     */
    public CreateContainerCmd withExtraHost(String hostname, String ip) {
        if (config.getHostConfig() == null) {
            config.setHostConfig(new HostConfig());
        }
        config.getHostConfig().addExtraHost(hostname + ":" + ip);
        return this;
    }

    /**
     * Execute the command.
     */
    public CreateContainerResponse exec() {
        try {
            Map<String, String> queryParams = new HashMap<>();
            if (name != null) {
                queryParams.put("name", name);
            }

            DockerResponse response = client.post("/containers/create", queryParams, config);
            
            if (response.getStatusCode() == 404) {
                throw new NotFoundException("Image not found: " + config.getImage());
            }
            if (response.getStatusCode() == 409) {
                throw new ConflictException("Container name conflict: " + name);
            }
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to create container: " + response.getBody(), response.getStatusCode());
            }

            return client.getGson().fromJson(response.getBody(), CreateContainerResponse.class);
        } catch (IOException e) {
            throw new DockerException("Failed to create container", e);
        }
    }
}
