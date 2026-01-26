package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for creating an exec instance.
 *
 * @author Finn Birich
 */
public class ExecConfig {
    @SerializedName("AttachStdin")
    private Boolean attachStdin;

    @SerializedName("AttachStdout")
    private Boolean attachStdout;

    @SerializedName("AttachStderr")
    private Boolean attachStderr;

    @SerializedName("DetachKeys")
    private String detachKeys;

    @SerializedName("Tty")
    private Boolean tty;

    @SerializedName("Env")
    private List<String> env;

    @SerializedName("Cmd")
    private List<String> cmd;

    @SerializedName("Privileged")
    private Boolean privileged;

    @SerializedName("User")
    private String user;

    @SerializedName("WorkingDir")
    private String workingDir;

    public ExecConfig() {
    }

    public Boolean getAttachStdin() {
        return attachStdin;
    }

    public ExecConfig setAttachStdin(Boolean attachStdin) {
        this.attachStdin = attachStdin;
        return this;
    }

    public Boolean getAttachStdout() {
        return attachStdout;
    }

    public ExecConfig setAttachStdout(Boolean attachStdout) {
        this.attachStdout = attachStdout;
        return this;
    }

    public Boolean getAttachStderr() {
        return attachStderr;
    }

    public ExecConfig setAttachStderr(Boolean attachStderr) {
        this.attachStderr = attachStderr;
        return this;
    }

    public String getDetachKeys() {
        return detachKeys;
    }

    public ExecConfig setDetachKeys(String detachKeys) {
        this.detachKeys = detachKeys;
        return this;
    }

    public Boolean getTty() {
        return tty;
    }

    public ExecConfig setTty(Boolean tty) {
        this.tty = tty;
        return this;
    }

    public List<String> getEnv() {
        return env;
    }

    public ExecConfig setEnv(List<String> env) {
        this.env = env;
        return this;
    }

    public ExecConfig addEnv(String key, String value) {
        if (this.env == null) {
            this.env = new ArrayList<>();
        }
        this.env.add(key + "=" + value);
        return this;
    }

    public List<String> getCmd() {
        return cmd;
    }

    public ExecConfig setCmd(List<String> cmd) {
        this.cmd = cmd;
        return this;
    }

    public ExecConfig setCmd(String... cmd) {
        this.cmd = new ArrayList<>();
        for (String c : cmd) {
            this.cmd.add(c);
        }
        return this;
    }

    public Boolean getPrivileged() {
        return privileged;
    }

    public ExecConfig setPrivileged(Boolean privileged) {
        this.privileged = privileged;
        return this;
    }

    public String getUser() {
        return user;
    }

    public ExecConfig setUser(String user) {
        this.user = user;
        return this;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    public ExecConfig setWorkingDir(String workingDir) {
        this.workingDir = workingDir;
        return this;
    }
}
