package io.github.intisy.docker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.github.intisy.docker.IOUtils.readAllBytes;

/**
 * Windows-specific Docker provider.
 * Supports both native Windows containers (requires admin) and WSL2-based Docker (no admin required).
 * Supports multiple simultaneous instances.
 *
 * @author Finn Birich
 */
public class WindowsDockerProvider extends DockerProvider {
    private static final Logger log = LoggerFactory.getLogger(WindowsDockerProvider.class);

    private static final String DOCKER_DOWNLOAD_URL = "https://download.docker.com/win/static/stable/%s/%s.zip";
    private static final Path DOCKER_PATH = DOCKER_DIR.resolve("docker/dockerd.exe");
    private static final Path DOCKER_VERSION_FILE = DOCKER_DIR.resolve(".docker-version");

    private final String instanceId;
    
    private final Path instanceDir;
    private final String pipeName;
    private final Path dockerPipePath;
    private final Path dataDir;
    private final Path execDir;

    private DockerClient dockerClient;
    private Process dockerProcess;
    private boolean usingWsl2 = false;
    private String wslSocketPath;

    public WindowsDockerProvider() {
        this.instanceId = UUID.randomUUID().toString().substring(0, 8);
        this.instanceDir = DOCKER_DIR.resolve("instances").resolve(instanceId);
        this.pipeName = "docker_java_" + instanceId;
        this.dockerPipePath = Paths.get("\\\\.\\pipe\\" + pipeName);
        this.dataDir = instanceDir.resolve("data");
        this.execDir = instanceDir.resolve("exec");
        log.debug("Created WindowsDockerProvider with instance ID: {}", instanceId);
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void ensureInstalled() throws IOException {
        if (usingWsl2) {
            ensureWsl2DockerInstalled();
            return;
        }
        
        boolean autoUpdate = Boolean.parseBoolean(System.getProperty("docker.auto.update", "true"));
        String latestVersion = DockerVersionFetcher.getLatestVersion();
        boolean needsUpdate = true;

        if (Files.exists(DOCKER_PATH)) {
            if (autoUpdate && Files.exists(DOCKER_VERSION_FILE)) {
                String installedVersion = new String(Files.readAllBytes(DOCKER_VERSION_FILE)).trim();
                if (!installedVersion.equals(latestVersion)) {
                    log.info("Newer Docker version available. Updating from {} to {}", installedVersion, latestVersion);
                } else {
                    log.info("Docker is up to date ({})", latestVersion);
                    needsUpdate = false;
                }
            } else if (!autoUpdate) {
                needsUpdate = false;
            }
        }

        if (needsUpdate) {
            log.info("Docker installation is incomplete or outdated. Re-installing...");

            Path dockerInstallDir = DOCKER_DIR.resolve("docker");
            if (Files.exists(dockerInstallDir)) {
                try (java.util.stream.Stream<Path> walk = Files.walk(dockerInstallDir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }

            String arch = getArch();
            String dockerUrl = String.format(DOCKER_DOWNLOAD_URL, arch, latestVersion);
            downloadAndExtract(dockerUrl);
            Files.write(DOCKER_VERSION_FILE, latestVersion.getBytes());
        }
    }

    private void ensureWsl2DockerInstalled() throws IOException {
        log.info("Checking Docker installation in WSL2 (distro: {})...", wslDistro);
        
        try {
            // Check if dockerd exists
            ProcessBuilder checkPb = new ProcessBuilder("wsl", "-d", wslDistro, "-e", "bash", "-c", "command -v dockerd");
            checkPb.redirectErrorStream(true);
            Process checkProcess = checkPb.start();
            byte[] output = readAllBytes(checkProcess.getInputStream());
            int exitCode = checkProcess.waitFor();
            
            log.debug("dockerd check exit code: {}, output: {}", exitCode, new String(output).trim());
            
            if (exitCode != 0 || new String(output).trim().isEmpty()) {
                log.info("Docker not found in WSL2. Installing...");
                installDockerInWsl2();
            } else {
                log.info("Docker is already installed in WSL2 at: {}", new String(output).trim());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while checking WSL2 Docker installation", e);
        }
    }

    private void installDockerInWsl2() throws IOException {
        log.info("Docker is not installed in WSL2 (distro: {})", wslDistro);
        log.info("");
        log.info("Please install Docker manually in WSL by running these commands:");
        log.info("  wsl -d {}", wslDistro);
        log.info("  curl -fsSL https://get.docker.com | sh");
        log.info("  sudo usermod -aG docker $USER");
        log.info("  exit");
        log.info("");
        log.info("Then restart WSL:");
        log.info("  wsl --shutdown");
        log.info("  wsl -d {}", wslDistro);
        log.info("");
        
        throw new RuntimeException("Docker is not installed in WSL2. Please install it manually (see instructions above).");
    }

    private String getArch() {
        String osArch = System.getProperty("os.arch");
        if ("amd64".equals(osArch)) {
            return "x86_64";
        }
        throw new UnsupportedOperationException("Unsupported architecture: " + osArch);
    }

    private void downloadAndExtract(String urlString) throws IOException {
        log.debug("Downloading and extracting {}...", urlString);
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");

        if (connection.getResponseCode() != 200) {
            throw new IOException("Failed to download file from " + urlString + ". Status code: " + connection.getResponseCode());
        }

        try (InputStream is = connection.getInputStream();
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    Path outputPath = DOCKER_DIR.resolve(entry.getName());
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(zis, outputPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private void createInstanceDirectories() throws IOException {
        Files.createDirectories(instanceDir);
        Files.createDirectories(dataDir);
        Files.createDirectories(execDir);
    }

    @Override
    public void start() throws IOException, InterruptedException {
        log.info("Starting Docker (instance: {})...", instanceId);
        
        boolean isAdmin = isAdministrator();
        log.debug("Administrator check result: {}", isAdmin);
        
        if (isAdmin) {
            log.info("Running with administrator privileges. Using native Windows Docker");
            ensureContainersFeatureEnabled();
            startNativeDocker();
        } else {
            log.info("Not running as administrator. Checking for WSL2...");
            boolean wslAvailable = isWsl2Available();
            log.debug("WSL2 availability check result: {}", wslAvailable);
            
            if (wslAvailable) {
                log.info("WSL2 is available. Using Docker in WSL2 with distro: {}", wslDistro);
                usingWsl2 = true;
                startWsl2Docker();
            } else {
                log.error("Docker requires either administrator privileges or a usable WSL2 Linux distribution");
                log.error("You have WSL but only Docker Desktop's internal distros are installed.");
                log.error("To fix this, install a Linux distribution:");
                log.error("  1) Open PowerShell as Administrator (one-time setup)");
                log.error("  2) Run: wsl --install -d Ubuntu");
                log.error("  3) Complete the Ubuntu setup (create username/password)");
                log.error("  4) Try running your application again");
                throw new RuntimeException("Cannot start Docker: no admin privileges and no usable WSL2 Linux distro. " +
                        "Install Ubuntu with 'wsl --install -d Ubuntu' (requires admin once).");
            }
        }
    }

    private void startNativeDocker() throws IOException, InterruptedException {
        ensureInstalled();
        createInstanceDirectories();

        ProcessBuilder pb = new ProcessBuilder(
                DOCKER_PATH.toString(),
                "-H", "npipe://" + dockerPipePath.toString().replace("\\", "/"),
                "--data-root", dataDir.toString(),
                "--exec-root", execDir.toString(),
                "--pidfile", instanceDir.resolve("docker.pid").toString()
        );
        
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        dockerProcess = pb.start();

        if (!waitForPipe()) {
            throw new RuntimeException("Docker daemon failed to create named pipe in time.");
        }
        
        log.info("Docker daemon started (instance: {}, pipe: {})", instanceId, pipeName);
    }

    // Port for this instance's Docker daemon (base port + hash of instance ID)
    private int dockerPort;
    
    private void startWsl2Docker() throws IOException, InterruptedException {
        ensureInstalled();
        
        // Use TCP instead of Unix socket so Windows Java can connect
        // Generate a unique port based on instance ID to avoid conflicts
        dockerPort = 2375 + Math.abs(instanceId.hashCode() % 1000);
        wslSocketPath = "tcp://0.0.0.0:" + dockerPort;  // Listen on all interfaces inside WSL
        String wslLogFile = "/tmp/docker-java-" + instanceId + ".log";
        
        // Get the actual home directory path in WSL
        String wslHome = runWslCommand("echo $HOME", false, 5);
        if (wslHome.isEmpty()) {
            wslHome = "/home/" + runWslCommand("whoami", false, 5);
        }
        log.debug("WSL home directory: {}", wslHome);
        
        String wslDataDir = wslHome + "/.docker-java/instances/" + instanceId + "/data";
        String wslExecDir = wslHome + "/.docker-java/instances/" + instanceId + "/exec";
        String wslPidFile = wslHome + "/.docker-java/instances/" + instanceId + "/docker.pid";
        
        // Create directories in WSL2
        log.debug("Creating directories in WSL2 (distro: {})...", wslDistro);
        String mkdirResult = runWslCommand("mkdir -p " + wslDataDir + " " + wslExecDir + " && echo ok", false, 10);
        log.debug("mkdir result: {}", mkdirResult);
        
        // Remove any existing socket
        runWslCommand("rm -f " + wslSocketPath, false, 5);
        
        // Check if Docker Desktop daemon is running and might conflict
        String existingDocker = runWslCommand("pgrep -f 'dockerd' 2>/dev/null", false, 5);
        if (!existingDocker.isEmpty()) {
            log.warn("Another dockerd process may be running (PIDs: {}). This might cause conflicts.", existingDocker.replace("\n", ", "));
        }
        
        log.info("Starting Docker daemon in WSL2 (instance: {}, distro: {})...", instanceId, wslDistro);
        
        // dockerd always needs root to start (docker group only helps with client access)
        // Check if passwordless sudo is configured
        if (!checkPasswordlessSudo()) {
            log.error("Starting dockerd requires root privileges.");
            log.error("Please run this ONE-TIME setup in WSL ({}):", wslDistro);
            log.error("");
            log.error("  # Allow passwordless sudo for dockerd");
            log.error("  echo \"$USER ALL=(ALL) NOPASSWD: /usr/bin/dockerd\" | sudo tee /etc/sudoers.d/dockerd");
            log.error("  sudo chmod 440 /etc/sudoers.d/dockerd");
            log.error("");
            throw new RuntimeException("Cannot start Docker in WSL2: dockerd requires root. " +
                    "Run the setup commands above in WSL (wsl -d " + wslDistro + ").");
        }
        
        log.debug("Passwordless sudo for dockerd is available");
        
        // Check if another dockerd is running (like Docker Desktop)
        String existingDockerds = runWslCommand("pgrep -x dockerd 2>/dev/null | wc -l", false, 5).trim();
        boolean otherDockerdRunning = !"0".equals(existingDockerds) && !existingDockerds.isEmpty();
        
        // Determine isolation flags to avoid conflicts with other Docker daemons
        String isolationFlags = "";
        if (otherDockerdRunning) {
            log.info("Another Docker daemon detected, using isolation flags to avoid conflicts");
            // Use --iptables=false to prevent conflicts with existing Docker's iptables rules
            // Use --bridge=none to disable default bridge (avoids need to create custom bridge device)
            isolationFlags = " --iptables=false --bridge=none";
        }
        
        log.debug("Starting dockerd directly...");
        
        // Build the dockerd command - use TCP so Windows Java can connect
        String dockerdCmd = String.format(
                "sudo dockerd -H %s --data-root %s --exec-root %s --pidfile %s%s",
                wslSocketPath, wslDataDir, wslExecDir, wslPidFile, isolationFlags);
        
        log.debug("Docker command: {}", dockerdCmd);
        
        // First, test if the command works by running dockerd --version
        String versionCheck = runWslCommand("sudo dockerd --version 2>&1", false, 10);
        log.debug("dockerd version check: {}", versionCheck);
        
        // Clear any old log file
        runWslCommand("rm -f " + wslLogFile, false, 5);
        
        // Start dockerd directly, capturing output
        log.debug("Executing dockerd command...");
        ProcessBuilder pb = new ProcessBuilder(
                "wsl", "-d", wslDistro, "--", "bash", "-c",
                "echo 'Starting dockerd...' >> " + wslLogFile + " && " +
                dockerdCmd + " >> " + wslLogFile + " 2>&1"
        );
        pb.redirectErrorStream(true);
        
        // Start the process
        dockerProcess = pb.start();
        
        // Give it time to initialize (Docker 29+ has a deliberate 1s+ delay for security warnings)
        Thread.sleep(8000);
        
        // Check if process is still running
        if (!dockerProcess.isAlive()) {
            byte[] output = readAllBytes(dockerProcess.getInputStream());
            String outputStr = new String(output).trim();
            log.error("WSL process died. Output: {}", outputStr.isEmpty() ? "(empty)" : outputStr);
            
            // Check log file
            String logContent = runWslCommand("cat " + wslLogFile + " 2>/dev/null || echo '(no log)'", false, 5);
            log.error("Log file contents:\n{}", logContent);
            
            throw new RuntimeException("dockerd failed to start. Check logs above.");
        }
        
        // Check if dockerd is actually running inside WSL
        String dockerdCheck = runWslCommand("pgrep -x dockerd && echo 'running' || echo 'not running'", false, 5);
        log.debug("dockerd process check: {}", dockerdCheck.trim());
        
        // Check log file for any startup messages
        String earlyLog = runWslCommand("cat " + wslLogFile + " 2>/dev/null | head -20", false, 5);
        if (!earlyLog.isEmpty()) {
            log.debug("Early log output:\n{}", earlyLog);
        }
        
        // Check if our specific dockerd is running (by port)
        String ourDockerd = runWslCommand("pgrep -af 'dockerd.*" + dockerPort + "' 2>/dev/null || echo 'not found'", false, 5);
        log.debug("Our dockerd process (by port {}): {}", dockerPort, ourDockerd.trim());
        
        log.debug("WSL process is alive, waiting for port {}...", dockerPort);

        if (!waitForWslSocket()) {
            // Additional diagnostics when connection fails
            String portCheck = runWslCommand("ss -tlnp 2>/dev/null | grep " + dockerPort + " || echo 'port not found'", false, 5);
            log.error("Port {} status after wait: {}", dockerPort, portCheck.trim());
            // Try to get error log
            String logContent = runWslCommand("cat " + wslLogFile + " 2>/dev/null", false, 5);
            if (!logContent.isEmpty()) {
                log.error("Docker daemon log:\n{}", logContent);
            } else {
                log.error("Docker daemon log is empty.");
            }
            
            // Check if port is listening INSIDE WSL
            String netstatCheck = runWslCommand("ss -tlnp 2>/dev/null | grep " + dockerPort + " || echo 'port not listening in WSL'", false, 5);
            log.error("Port {} status inside WSL: {}", dockerPort, netstatCheck.trim());
            
            // Check if dockerd is running inside WSL
            String dockerdPs = runWslCommand("ps aux | grep dockerd | grep -v grep || echo 'no dockerd process'", false, 5);
            log.error("dockerd processes in WSL:\n{}", dockerdPs);
            
            // Check if our dockerd process exists (look for our port)
            String psOutput = runWslCommand("pgrep -af 'dockerd.*" + dockerPort + "' 2>/dev/null || echo '(none with our port)'", false, 5);
            log.error("Our dockerd process: {}", psOutput);
            
            // Check if Docker Desktop's dockerd is blocking
            String allDockerds = runWslCommand("pgrep -af dockerd 2>/dev/null || echo '(none)'", false, 5);
            if (allDockerds.contains("-H fd://")) {
                log.error("");
                log.error("Docker Desktop's dockerd is running in WSL2.");
                log.error("Stop it with: wsl -d {} -- sudo systemctl stop docker", wslDistro);
            }
            
            // Check if our WSL process is still running
            if (dockerProcess != null && dockerProcess.isAlive()) {
                log.error("WSL process is still running but port not accessible from Windows");
                log.error("This might be a WSL2 networking issue. Try: wsl --shutdown");
                dockerProcess.destroyForcibly();
            }
            
            throw new RuntimeException("Docker daemon in WSL2 failed to start or port not accessible. See logs above for details.");
        }
        
        log.info("Docker daemon started in WSL2 (instance: {}, port: {})", instanceId, dockerPort);
    }
    
    /**
     * Check if passwordless sudo is available for dockerd.
     */
    private boolean checkPasswordlessSudo() {
        try {
            // Try sudo -n (non-interactive) to check if dockerd can be run without password
            // We check with 'sudo -n dockerd --version' to verify dockerd specifically
            ProcessBuilder pb = new ProcessBuilder("wsl", "-d", wslDistro, "-e", "bash", "-c", 
                    "sudo -n dockerd --version >/dev/null 2>&1 && echo yes || echo no");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            byte[] output = readAllBytes(process.getInputStream());
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            
            if (!completed) {
                log.debug("sudo check timed out");
                return false;
            }
            
            String result = new String(output).trim();
            boolean hasPasswordlessSudo = "yes".equals(result);
            log.debug("Passwordless sudo for dockerd: {}", hasPasswordlessSudo);
            return hasPasswordlessSudo;
        } catch (IOException | InterruptedException e) {
            log.debug("Failed to check passwordless sudo: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Run a command in WSL.
     */
    private String runWslCommand(String command, boolean useSudo, int timeoutSeconds) {
        try {
            String fullCommand = useSudo ? "sudo " + command : command;
            ProcessBuilder pb = new ProcessBuilder("wsl", "-d", wslDistro, "-e", "bash", "-c", fullCommand);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            byte[] output = readAllBytes(process.getInputStream());
            process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            return new String(output).trim();
        } catch (IOException | InterruptedException e) {
            log.debug("WSL command failed: {}", e.getMessage());
            return "";
        }
    }

    private void ensureContainersFeatureEnabled() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", "Get-WindowsOptionalFeature -Online -FeatureName Containers");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        process.waitFor();
        String outputStr = output.toString();

        if (!outputStr.contains("State            : Enabled")) {
            log.info("Enabling Windows Containers feature (requires reboot)...");
            ProcessBuilder enablePb = new ProcessBuilder("powershell.exe", "-Command", "Enable-WindowsOptionalFeature -Online -FeatureName Containers -All");
            enablePb.inheritIO();
            Process enableProcess = enablePb.start();
            enableProcess.waitFor();
            throw new RuntimeException("Windows Containers feature enabled. Reboot required.");
        }
    }

    private boolean isAdministrator() {
        try {
            ProcessBuilder pb = new ProcessBuilder("net", "session");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            byte[] output = readAllBytes(process.getInputStream());
            int exitCode = process.waitFor();
            log.debug("Admin check (net session) exit code: {}", exitCode);
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            log.debug("Admin check failed with exception: {}", e.getMessage());
            return false;
        }
    }

    private String wslDistro = null;
    
    private boolean isWsl2Available() {
        try {
            // First check: try wsl -l -v which lists distros with their WSL version
            ProcessBuilder pb = new ProcessBuilder("wsl", "-l", "-v");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Read as bytes and convert, handling potential UTF-16 encoding
            byte[] outputBytes = readAllBytes(process.getInputStream());
            String output = new String(outputBytes, java.nio.charset.StandardCharsets.UTF_16LE);
            
            int exitCode = process.waitFor();
            log.debug("WSL -l -v exit code: {}, output: {}", exitCode, output.trim());
            
            if (exitCode != 0) {
                log.debug("WSL not installed or not working");
                return false;
            }
            
            // Parse the output to find a usable distro (not docker-desktop or docker-desktop-data)
            String[] lines = output.split("\n");
            String defaultDistro = null;
            String usableDistro = null;
            
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.contains("NAME") || line.contains("---")) {
                    continue;
                }
                
                // Check for WSL2 distro (has "2" in version column)
                if (!line.contains("2")) {
                    continue;
                }
                
                boolean isDefault = line.startsWith("*");
                String distroName = line.replace("*", "").trim().split("\\s+")[0];
                
                // Skip Docker Desktop's internal distros
                if (distroName.toLowerCase().startsWith("docker-desktop")) {
                    log.debug("Skipping Docker Desktop distro: {}", distroName);
                    if (isDefault) {
                        defaultDistro = distroName;
                    }
                    continue;
                }
                
                // Found a usable distro
                if (usableDistro == null || isDefault) {
                    usableDistro = distroName;
                    log.debug("Found usable WSL2 distro: {} (default: {})", distroName, isDefault);
                }
            }
            
            if (usableDistro != null) {
                wslDistro = usableDistro;
                log.info("Using WSL2 distro: {}", wslDistro);
                
                // Verify the distro has bash
                ProcessBuilder testPb = new ProcessBuilder("wsl", "-d", wslDistro, "-e", "bash", "-c", "echo test");
                testPb.redirectErrorStream(true);
                Process testProcess = testPb.start();
                readAllBytes(testProcess.getInputStream());
                int testExitCode = testProcess.waitFor();
                
                if (testExitCode == 0) {
                    return true;
                } else {
                    log.warn("WSL2 distro {} does not have bash available", wslDistro);
                    wslDistro = null;
                }
            }
            
            // No usable distro found
            if (defaultDistro != null && defaultDistro.toLowerCase().startsWith("docker-desktop")) {
                log.warn("Only Docker Desktop WSL distros found. These cannot be used to run dockerd.");
                log.warn("Install a Linux distro with: wsl --install -d Ubuntu");
            }
            
            return false;
        } catch (IOException | InterruptedException e) {
            log.debug("WSL check failed with exception: {}", e.getMessage());
            return false;
        }
    }

    private String wslIpAddress;
    
    @Override
    public DockerClient getClient() {
        if (this.dockerClient == null) {
            if (usingWsl2) {
                // Connect via TCP to WSL2
                String host = wslIpAddress != null ? wslIpAddress : "localhost";
                this.dockerClient = DockerClient.builder()
                        .withHost("tcp://" + host + ":" + dockerPort)
                        .build();
            } else {
                this.dockerClient = DockerClient.builder()
                        .withHost("npipe://" + dockerPipePath.toString().replace("\\", "/"))
                        .build();
            }
        }
        return this.dockerClient;
    }

    @Override
    public void stop() {
        log.info("Stopping Docker daemon (instance: {})...", instanceId);
        
        if (dockerProcess != null) {
            if (usingWsl2 && wslDistro != null) {
                try {
                    // Kill dockerd process by port
                    ProcessBuilder pb = new ProcessBuilder("wsl", "-d", wslDistro, "-e", "bash", "-c",
                            "sudo -n pkill -f 'dockerd.*" + dockerPort + "' 2>/dev/null ; " +
                            "rm -rf ~/.docker-java/instances/" + instanceId);
                    pb.start().waitFor(5, TimeUnit.SECONDS);
                } catch (IOException | InterruptedException e) {
                    log.warn("Failed to stop WSL2 Docker daemon: {}", e.getMessage());
                }
            }
            
            dockerProcess.destroy();
            try {
                dockerProcess.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                dockerProcess.destroyForcibly();
            }
        }
        
        cleanupInstanceDirectory();
    }

    private void cleanupInstanceDirectory() {
        try {
            if (Files.exists(instanceDir)) {
                try (java.util.stream.Stream<Path> walk = Files.walk(instanceDir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to clean up instance directory: {}", e.getMessage());
        }
    }

    @SuppressWarnings("BusyWait")
    private boolean waitForPipe() throws InterruptedException {
        log.debug("Waiting for Docker pipe at {}...", dockerPipePath);
        long timeoutMillis = TimeUnit.SECONDS.toMillis(30);
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (Files.exists(dockerPipePath)) {
                log.debug("Docker pipe found");
                return true;
            }
            Thread.sleep(500);
        }
        log.error("Timed out waiting for Docker pipe");
        return false;
    }

    @SuppressWarnings("BusyWait")
    private boolean waitForWslSocket() throws InterruptedException {
        // Get WSL2's IP address
        wslIpAddress = runWslCommand("hostname -I | awk '{print $1}'", false, 5).trim();
        if (wslIpAddress.isEmpty()) {
            wslIpAddress = "localhost";
        }
        log.debug("WSL2 IP address: {}", wslIpAddress);
        
        log.debug("Waiting for Docker daemon on {}:{}...", wslIpAddress, dockerPort);
        long timeoutMillis = TimeUnit.SECONDS.toMillis(60);
        long startTime = System.currentTimeMillis();
        int attempts = 0;
        
        // Try both localhost and WSL IP
        String[] hosts = wslIpAddress.equals("localhost") ? 
                new String[]{"localhost"} : 
                new String[]{"localhost", wslIpAddress};
        
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            attempts++;
            
            for (String host : hosts) {
                try {
                    java.net.Socket socket = new java.net.Socket();
                    socket.connect(new java.net.InetSocketAddress(host, dockerPort), 1000);
                    socket.close();
                    wslIpAddress = host; // Remember which one worked
                    log.debug("Docker daemon is listening on {}:{} after {} attempts", host, dockerPort, attempts);
                    
                    // Verify our dockerd is actually running
                    String verify = runWslCommand("ss -tlnp 2>/dev/null | grep " + dockerPort, false, 5);
                    log.debug("Port {} listener info: {}", dockerPort, verify.trim());
                    
                    return true;
                } catch (IOException e) {
                    // Not ready yet on this host
                }
            }
            
            // Log progress every 10 seconds
            if (attempts % 20 == 0) {
                log.debug("Still waiting for Docker daemon... ({} seconds elapsed)", (System.currentTimeMillis() - startTime) / 1000);
            }
            
            Thread.sleep(500);
        }
        log.error("Timed out waiting for Docker daemon on port {} after {} seconds", dockerPort, (System.currentTimeMillis() - startTime) / 1000);
        return false;
    }
}
