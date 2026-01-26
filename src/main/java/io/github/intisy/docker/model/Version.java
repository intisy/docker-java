package io.github.intisy.docker.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Docker version information.
 *
 * @author Finn Birich
 */
public class Version {
    @SerializedName("Platform")
    private Platform platform;

    @SerializedName("Components")
    private List<Component> components;

    @SerializedName("Version")
    private String version;

    @SerializedName("ApiVersion")
    private String apiVersion;

    @SerializedName("MinAPIVersion")
    private String minAPIVersion;

    @SerializedName("GitCommit")
    private String gitCommit;

    @SerializedName("GoVersion")
    private String goVersion;

    @SerializedName("Os")
    private String os;

    @SerializedName("Arch")
    private String arch;

    @SerializedName("KernelVersion")
    private String kernelVersion;

    @SerializedName("Experimental")
    private Boolean experimental;

    @SerializedName("BuildTime")
    private String buildTime;

    public Platform getPlatform() {
        return platform;
    }

    public List<Component> getComponents() {
        return components;
    }

    public String getVersion() {
        return version;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public String getMinAPIVersion() {
        return minAPIVersion;
    }

    public String getGitCommit() {
        return gitCommit;
    }

    public String getGoVersion() {
        return goVersion;
    }

    public String getOs() {
        return os;
    }

    public String getArch() {
        return arch;
    }

    public String getKernelVersion() {
        return kernelVersion;
    }

    public Boolean getExperimental() {
        return experimental;
    }

    public String getBuildTime() {
        return buildTime;
    }

    /**
     * Platform info.
     */
    public static class Platform {
        @SerializedName("Name")
        private String name;

        public String getName() {
            return name;
        }
    }

    /**
     * Component info.
     */
    public static class Component {
        @SerializedName("Name")
        private String name;

        @SerializedName("Version")
        private String version;

        @SerializedName("Details")
        private Object details;

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }

        public Object getDetails() {
            return details;
        }
    }

    @Override
    public String toString() {
        return "Version{" +
                "version='" + version + '\'' +
                ", apiVersion='" + apiVersion + '\'' +
                ", os='" + os + '\'' +
                ", arch='" + arch + '\'' +
                ", goVersion='" + goVersion + '\'' +
                '}';
    }
}
