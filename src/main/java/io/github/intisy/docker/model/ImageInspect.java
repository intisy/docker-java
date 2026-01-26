package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * Detailed information about an image from docker inspect.
 *
 * @author Finn Birich
 */
public class ImageInspect {
    @SerializedName("Id")
    private String id;

    @SerializedName("RepoTags")
    private List<String> repoTags;

    @SerializedName("RepoDigests")
    private List<String> repoDigests;

    @SerializedName("Parent")
    private String parent;

    @SerializedName("Comment")
    private String comment;

    @SerializedName("Created")
    private String created;

    @SerializedName("Container")
    private String container;

    @SerializedName("DockerVersion")
    private String dockerVersion;

    @SerializedName("Author")
    private String author;

    @SerializedName("Config")
    private ImageConfig config;

    @SerializedName("Architecture")
    private String architecture;

    @SerializedName("Os")
    private String os;

    @SerializedName("OsVersion")
    private String osVersion;

    @SerializedName("Size")
    private Long size;

    @SerializedName("VirtualSize")
    private Long virtualSize;

    @SerializedName("RootFS")
    private RootFS rootFS;

    @SerializedName("Metadata")
    private Metadata metadata;

    public String getId() {
        return id;
    }

    public List<String> getRepoTags() {
        return repoTags;
    }

    public List<String> getRepoDigests() {
        return repoDigests;
    }

    public String getParent() {
        return parent;
    }

    public String getComment() {
        return comment;
    }

    public String getCreated() {
        return created;
    }

    public String getContainer() {
        return container;
    }

    public String getDockerVersion() {
        return dockerVersion;
    }

    public String getAuthor() {
        return author;
    }

    public ImageConfig getConfig() {
        return config;
    }

    public String getArchitecture() {
        return architecture;
    }

    public String getOs() {
        return os;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public Long getSize() {
        return size;
    }

    public Long getVirtualSize() {
        return virtualSize;
    }

    public RootFS getRootFS() {
        return rootFS;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    /**
     * Image configuration.
     */
    public static class ImageConfig {
        @SerializedName("Hostname")
        private String hostname;

        @SerializedName("Domainname")
        private String domainname;

        @SerializedName("User")
        private String user;

        @SerializedName("AttachStdin")
        private Boolean attachStdin;

        @SerializedName("AttachStdout")
        private Boolean attachStdout;

        @SerializedName("AttachStderr")
        private Boolean attachStderr;

        @SerializedName("ExposedPorts")
        private Map<String, Object> exposedPorts;

        @SerializedName("Tty")
        private Boolean tty;

        @SerializedName("OpenStdin")
        private Boolean openStdin;

        @SerializedName("StdinOnce")
        private Boolean stdinOnce;

        @SerializedName("Env")
        private List<String> env;

        @SerializedName("Cmd")
        private List<String> cmd;

        @SerializedName("Entrypoint")
        private List<String> entrypoint;

        @SerializedName("Image")
        private String image;

        @SerializedName("Volumes")
        private Map<String, Object> volumes;

        @SerializedName("WorkingDir")
        private String workingDir;

        @SerializedName("Labels")
        private Map<String, String> labels;

        @SerializedName("StopSignal")
        private String stopSignal;

        public String getHostname() {
            return hostname;
        }

        public String getUser() {
            return user;
        }

        public Map<String, Object> getExposedPorts() {
            return exposedPorts;
        }

        public List<String> getEnv() {
            return env;
        }

        public List<String> getCmd() {
            return cmd;
        }

        public List<String> getEntrypoint() {
            return entrypoint;
        }

        public String getImage() {
            return image;
        }

        public Map<String, Object> getVolumes() {
            return volumes;
        }

        public String getWorkingDir() {
            return workingDir;
        }

        public Map<String, String> getLabels() {
            return labels;
        }

        public String getStopSignal() {
            return stopSignal;
        }
    }

    /**
     * Root filesystem info.
     */
    public static class RootFS {
        @SerializedName("Type")
        private String type;

        @SerializedName("Layers")
        private List<String> layers;

        public String getType() {
            return type;
        }

        public List<String> getLayers() {
            return layers;
        }
    }

    /**
     * Image metadata.
     */
    public static class Metadata {
        @SerializedName("LastTagTime")
        private String lastTagTime;

        public String getLastTagTime() {
            return lastTagTime;
        }
    }

    @Override
    public String toString() {
        return "ImageInspect{" +
                "id='" + (id != null ? id.substring(0, Math.min(12, id.length())) : "null") + '\'' +
                ", repoTags=" + repoTags +
                ", architecture='" + architecture + '\'' +
                ", os='" + os + '\'' +
                ", size=" + size +
                '}';
    }
}
