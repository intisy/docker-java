package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

/**
 * Exec instance inspection result.
 *
 * @author Finn Birich
 */
public class ExecInspect {
    @SerializedName("ID")
    private String id;

    @SerializedName("Running")
    private Boolean running;

    @SerializedName("ExitCode")
    private Integer exitCode;

    @SerializedName("ProcessConfig")
    private ProcessConfig processConfig;

    @SerializedName("OpenStdin")
    private Boolean openStdin;

    @SerializedName("OpenStderr")
    private Boolean openStderr;

    @SerializedName("OpenStdout")
    private Boolean openStdout;

    @SerializedName("ContainerID")
    private String containerId;

    @SerializedName("Pid")
    private Integer pid;

    public String getId() {
        return id;
    }

    public Boolean getRunning() {
        return running;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public ProcessConfig getProcessConfig() {
        return processConfig;
    }

    public Boolean getOpenStdin() {
        return openStdin;
    }

    public Boolean getOpenStderr() {
        return openStderr;
    }

    public Boolean getOpenStdout() {
        return openStdout;
    }

    public String getContainerId() {
        return containerId;
    }

    public Integer getPid() {
        return pid;
    }

    /**
     * Process configuration.
     */
    public static class ProcessConfig {
        @SerializedName("privileged")
        private Boolean privileged;

        @SerializedName("user")
        private String user;

        @SerializedName("tty")
        private Boolean tty;

        @SerializedName("entrypoint")
        private String entrypoint;

        @SerializedName("arguments")
        private java.util.List<String> arguments;

        public Boolean getPrivileged() {
            return privileged;
        }

        public String getUser() {
            return user;
        }

        public Boolean getTty() {
            return tty;
        }

        public String getEntrypoint() {
            return entrypoint;
        }

        public java.util.List<String> getArguments() {
            return arguments;
        }
    }

    @Override
    public String toString() {
        return "ExecInspect{" +
                "id='" + id + '\'' +
                ", running=" + running +
                ", exitCode=" + exitCode +
                '}';
    }
}
