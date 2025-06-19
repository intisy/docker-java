package io.github.intisy.docker;

import com.github.dockerjava.api.DockerClient;
import java.io.IOException;

/**
 * @author Finn Birich
 */
public interface DockerProvider {
    void ensureInstalled() throws IOException, InterruptedException;
    void start() throws IOException, InterruptedException;
    DockerClient getClient();
    void stop();
}