package io.github.intisy.docker.registry;

import java.util.List;

/**
 * What to stamp into the assembled image's config.
 *
 * @implNote a null entrypoint or cmd means "leave the base value alone"; an empty list means
 * "clear it". The difference matters: a base JRE image ships a Cmd that must be cleared or the
 * container runs that instead of ours.
 *
 * @author Finn Birich
 */
public final class ImageConfigOverrides {
    private List<String> entrypoint;
    private List<String> cmd;
    private List<String> env;
    private String workingDir;
    private boolean clearCmd;

    public ImageConfigOverrides withEntrypoint(List<String> value) {
        this.entrypoint = value;
        return this;
    }

    public ImageConfigOverrides withCmd(List<String> value) {
        this.cmd = value;
        this.clearCmd = value == null;
        return this;
    }

    public ImageConfigOverrides withEnv(List<String> value) {
        this.env = value;
        return this;
    }

    public ImageConfigOverrides withWorkingDir(String value) {
        this.workingDir = value;
        return this;
    }

    List<String> entrypoint() {
        return entrypoint;
    }

    List<String> cmd() {
        return cmd;
    }

    boolean clearCmd() {
        return clearCmd;
    }

    List<String> env() {
        return env;
    }

    String workingDir() {
        return workingDir;
    }
}
