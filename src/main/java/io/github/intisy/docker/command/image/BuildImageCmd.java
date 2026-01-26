package io.github.intisy.docker.command.image;

import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.StreamCallback;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Command to build an image from a Dockerfile.
 * Note: This is a simplified implementation. Full build support would require
 * sending a tar archive containing the build context.
 *
 * @author Finn Birich
 */
public class BuildImageCmd {
    private final DockerHttpClient client;
    private String dockerfile = "Dockerfile";
    private Set<String> tags = new HashSet<>();
    private Map<String, String> buildArgs = new HashMap<>();
    private boolean noCache = false;
    private boolean pull = false;
    private boolean rm = true;
    private boolean forceRm = false;
    private Long memory;
    private Long memswap;
    private Integer cpuShares;
    private String cpuSetCpus;
    private String platform;
    private String target;

    public BuildImageCmd(DockerHttpClient client) {
        this.client = client;
    }

    /**
     * Set the Dockerfile path within the build context.
     */
    public BuildImageCmd withDockerfile(String dockerfile) {
        this.dockerfile = dockerfile;
        return this;
    }

    /**
     * Add a tag for the image.
     */
    public BuildImageCmd withTag(String tag) {
        this.tags.add(tag);
        return this;
    }

    /**
     * Set tags for the image.
     */
    public BuildImageCmd withTags(Set<String> tags) {
        this.tags = tags;
        return this;
    }

    /**
     * Add a build argument.
     */
    public BuildImageCmd withBuildArg(String key, String value) {
        this.buildArgs.put(key, value);
        return this;
    }

    /**
     * Set build arguments.
     */
    public BuildImageCmd withBuildArgs(Map<String, String> buildArgs) {
        this.buildArgs = buildArgs;
        return this;
    }

    /**
     * Do not use cache when building.
     */
    public BuildImageCmd withNoCache(boolean noCache) {
        this.noCache = noCache;
        return this;
    }

    /**
     * Always attempt to pull a newer version of the base images.
     */
    public BuildImageCmd withPull(boolean pull) {
        this.pull = pull;
        return this;
    }

    /**
     * Remove intermediate containers after a successful build.
     */
    public BuildImageCmd withRm(boolean rm) {
        this.rm = rm;
        return this;
    }

    /**
     * Always remove intermediate containers.
     */
    public BuildImageCmd withForceRm(boolean forceRm) {
        this.forceRm = forceRm;
        return this;
    }

    /**
     * Set memory limit.
     */
    public BuildImageCmd withMemory(long memory) {
        this.memory = memory;
        return this;
    }

    /**
     * Set total memory (memory + swap).
     */
    public BuildImageCmd withMemswap(long memswap) {
        this.memswap = memswap;
        return this;
    }

    /**
     * Set CPU shares.
     */
    public BuildImageCmd withCpuShares(int cpuShares) {
        this.cpuShares = cpuShares;
        return this;
    }

    /**
     * Set CPUs to use.
     */
    public BuildImageCmd withCpuSetCpus(String cpuSetCpus) {
        this.cpuSetCpus = cpuSetCpus;
        return this;
    }

    /**
     * Set the platform (e.g., linux/amd64).
     */
    public BuildImageCmd withPlatform(String platform) {
        this.platform = platform;
        return this;
    }

    /**
     * Set the target build stage.
     */
    public BuildImageCmd withTarget(String target) {
        this.target = target;
        return this;
    }

    /**
     * Build query parameters for the API call.
     */
    private Map<String, String> buildQueryParams() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("dockerfile", dockerfile);
        
        for (String tag : tags) {
            queryParams.put("t", tag);
        }
        
        if (!buildArgs.isEmpty()) {
            queryParams.put("buildargs", client.getGson().toJson(buildArgs));
        }
        
        if (noCache) {
            queryParams.put("nocache", "true");
        }
        if (pull) {
            queryParams.put("pull", "true");
        }
        if (!rm) {
            queryParams.put("rm", "false");
        }
        if (forceRm) {
            queryParams.put("forcerm", "true");
        }
        if (memory != null) {
            queryParams.put("memory", String.valueOf(memory));
        }
        if (memswap != null) {
            queryParams.put("memswap", String.valueOf(memswap));
        }
        if (cpuShares != null) {
            queryParams.put("cpushares", String.valueOf(cpuShares));
        }
        if (cpuSetCpus != null) {
            queryParams.put("cpusetcpus", cpuSetCpus);
        }
        if (platform != null) {
            queryParams.put("platform", platform);
        }
        if (target != null) {
            queryParams.put("target", target);
        }
        
        return queryParams;
    }

    /**
     * Execute the build command with a callback for streaming output.
     * Note: This requires sending a tar archive as the request body.
     * This simplified version just sets up the parameters.
     */
    public void exec(StreamCallback<BuildResponse> callback) {
        try {
            Map<String, String> queryParams = buildQueryParams();
            
            client.postStream("/build", queryParams, new StreamCallback<String>() {
                @Override
                public void onNext(String item) {
                    try {
                        BuildResponse response = client.getGson().fromJson(item, BuildResponse.class);
                        if (response != null) {
                            callback.onNext(response);
                            if (response.getError() != null) {
                                callback.onError(new DockerException(response.getError()));
                            }
                        }
                    } catch (Exception e) {}
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
            callback.onError(new DockerException("Failed to build image", e));
        }
    }

    /**
     * Build response from the Docker daemon.
     */
    public static class BuildResponse {
        private String stream;
        private String error;
        private ErrorDetail errorDetail;
        private String status;
        private String id;
        private AuxInfo aux;

        public String getStream() {
            return stream;
        }

        public String getError() {
            return error;
        }

        public ErrorDetail getErrorDetail() {
            return errorDetail;
        }

        public String getStatus() {
            return status;
        }

        public String getId() {
            return id;
        }

        public AuxInfo getAux() {
            return aux;
        }

        public boolean isError() {
            return error != null || errorDetail != null;
        }

        public static class ErrorDetail {
            private String message;

            public String getMessage() {
                return message;
            }
        }

        public static class AuxInfo {
            private String ID;

            public String getId() {
                return ID;
            }
        }

        @Override
        public String toString() {
            if (stream != null) {
                return stream.trim();
            }
            if (status != null) {
                return status + (id != null ? " " + id : "");
            }
            if (error != null) {
                return "ERROR: " + error;
            }
            return "";
        }
    }
}
