package io.github.intisy.docker.registry;

import java.util.List;

/**
 * What to stamp into the assembled image's config.
 * {@code cmd} is three-state: never calling {@code withCmd} leaves the base Cmd alone; {@code withCmd(null)}
 * removes it; {@code withCmd(list)}, empty list included, replaces it. This matters because a base JRE image
 * ships a Cmd that must be removed, via {@code withCmd(null)}, or the container runs that instead of ours.
 * {@code entrypoint} is only two-state: never calling {@code withEntrypoint} leaves the base value alone, any
 * list (including empty) replaces it; there is no way to clear it.
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
