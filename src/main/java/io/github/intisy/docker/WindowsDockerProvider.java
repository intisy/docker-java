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

    private static final String DOCKER_DOWNLOAD_URL = "https://download.docker.com/win/static/stable/%s/docker-%s.zip";
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
        Path baseDir = getBaseDirectory();
        this.instanceDir = baseDir.resolve("instances").resolve(instanceId);
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

    /**
     * Check if NVIDIA Container Toolkit is installed in WSL2.
     * @return true if installed, false otherwise
     */
    public boolean isNvidiaContainerToolkitInstalled() {
        if (!usingWsl2 || wslDistro == null) {
            return false;
        }
        
        String result = runWslCommand("command -v nvidia-container-toolkit && echo installed || echo missing", false, 5);
        return result.contains("installed");
    }

    /**
     * Check if NVIDIA GPU is available in WSL2.
     * @return true if NVIDIA GPU is detected, false otherwise
     */
    public boolean isNvidiaGpuAvailable() {
        if (!usingWsl2 || wslDistro == null) {
            return false;
        }
        
        String result = runWslCommand("nvidia-smi --query-gpu=name --format=csv,noheader 2>/dev/null | head -1", false, 10);
        boolean available = !result.isEmpty() && !result.contains("not found") && !result.contains("error");
        log.debug("NVIDIA GPU check result: {} (available: {})", result.trim(), available);
        return available;
    }

    /**
     * Install NVIDIA Container Toolkit in WSL2.
     * This enables GPU passthrough to Docker containers.
     * @throws IOException if installation fails
     */
    public void installNvidiaContainerToolkit() throws IOException {
        if (!usingWsl2 || wslDistro == null) {
            throw new IOException("NVIDIA Container Toolkit can only be installed in WSL2 mode");
        }
        
        log.info("Installing NVIDIA Container Toolkit in WSL2 (distro: {})...", wslDistro);
        log.info("This may take a few minutes...");
        
        if (!checkPasswordlessSudoForApt()) {
            log.error("NVIDIA Container Toolkit installation requires passwordless sudo.");
            log.error("Please run this ONE-TIME setup in WSL ({}):", wslDistro);
            log.error("");
            log.error("  wsl -d {}", wslDistro);
            log.error("  sudo bash -c 'echo \"$USER ALL=(ALL) NOPASSWD: ALL\" > /etc/sudoers.d/nopasswd-$USER'");
            log.error("  sudo chmod 440 /etc/sudoers.d/nopasswd-$USER");
            log.error("  exit");
            log.error("");
            log.error("Or install manually:");
            printManualInstallInstructions();
            throw new IOException("Passwordless sudo is required. Run the setup commands above in WSL.");
        }
        
        try {
            log.info("Adding NVIDIA GPG key...");
            String gpgKeyCmd = "curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey | " +
                    "sudo gpg --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg --yes 2>&1";
            String gpgResult = runWslCommandWithTimeout(gpgKeyCmd, false, 60);
            log.debug("GPG key result: {}", gpgResult);
            
            log.info("Adding NVIDIA repository...");
            String repoCmd = "curl -s -L https://nvidia.github.io/libnvidia-container/stable/deb/nvidia-container-toolkit.list | " +
                    "sed 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg] https://#g' | " +
                    "sudo tee /etc/apt/sources.list.d/nvidia-container-toolkit.list > /dev/null 2>&1 && echo ok";
            String repoResult = runWslCommandWithTimeout(repoCmd, false, 60);
            log.debug("Repo add result: {}", repoResult);
            
            log.info("Updating package lists...");
            String updateResult = runWslCommandWithTimeout("sudo apt-get update 2>&1", false, 180);
            log.debug("apt update result: {}", updateResult);
            
            log.info("Installing nvidia-container-toolkit package...");
            String installResult = runWslCommandWithTimeout(
                    "sudo apt-get install -y nvidia-container-toolkit 2>&1", false, 600);
            log.debug("Install result: {}", installResult);
            
            log.info("Configuring Docker to use NVIDIA runtime...");
            String configResult = runWslCommandWithTimeout("sudo nvidia-ctk runtime configure --runtime=docker 2>&1", false, 60);
            log.debug("Config result: {}", configResult);
            
            if (isNvidiaContainerToolkitInstalled()) {
                log.info("NVIDIA Container Toolkit installed successfully!");
            } else {
                throw new IOException("Installation completed but toolkit not found. Check output above for errors.");
            }
            
        } catch (Exception e) {
            log.error("Failed to install NVIDIA Container Toolkit: {}", e.getMessage());
            log.error("");
            printManualInstallInstructions();
            throw new IOException("Failed to install NVIDIA Container Toolkit: " + e.getMessage(), e);
        }
    }

    /**
     * Check if passwordless sudo is available (tests with 'sudo -n true').
     */
    private boolean checkPasswordlessSudoForApt() {
        try {
            ProcessBuilder pb = new ProcessBuilder("wsl", "-d", wslDistro, "-e", "bash", "-c",
                    "sudo -n true 2>/dev/null && echo yes || echo no");
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
            log.debug("Passwordless sudo available: {}", hasPasswordlessSudo);
            return hasPasswordlessSudo;
        } catch (IOException | InterruptedException e) {
            log.debug("Failed to check passwordless sudo: {}", e.getMessage());
            return false;
        }
    }

    private void printManualInstallInstructions() {
        log.error("You can install it manually by running these commands in WSL ({}):", wslDistro);
        log.error("  curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey | sudo gpg --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg --yes");
        log.error("  curl -s -L https://nvidia.github.io/libnvidia-container/stable/deb/nvidia-container-toolkit.list | \\");
        log.error("    sed 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg] https://#g' | \\");
        log.error("    sudo tee /etc/apt/sources.list.d/nvidia-container-toolkit.list");
        log.error("  sudo apt-get update");
        log.error("  sudo apt-get install -y nvidia-container-toolkit");
        log.error("  sudo nvidia-ctk runtime configure --runtime=docker");
    }

    /**
     * Ensure NVIDIA Container Toolkit is set up for GPU support.
     * Automatically installs if NVIDIA GPU is detected but toolkit is not installed.
     * @throws IOException if setup fails
     */
    public void ensureNvidiaContainerToolkit() throws IOException {
        if (!usingWsl2 || wslDistro == null) {
            log.debug("Not using WSL2, skipping NVIDIA toolkit check");
            return;
        }
        
        if (!isNvidiaGpuAvailable()) {
            log.debug("No NVIDIA GPU detected, skipping toolkit installation");
            return;
        }
        
        if (isNvidiaContainerToolkitInstalled()) {
            log.debug("NVIDIA Container Toolkit is already installed");
            return;
        }
        
        log.info("NVIDIA GPU detected but Container Toolkit not installed. Installing automatically...");
        installNvidiaContainerToolkit();
    }
    
    /**
     * Run a WSL command with extended timeout for long-running operations.
     */
    private String runWslCommandWithTimeout(String command, boolean useSudo, int timeoutSeconds) {
        try {
            String fullCommand = useSudo ? "sudo " + command : command;
            ProcessBuilder pb = new ProcessBuilder("wsl", "-d", wslDistro, "-e", "bash", "-c", fullCommand);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    log.debug("Error reading process output: {}", e.getMessage());
                }
            });
            reader.start();
            
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            reader.join(1000);
            
            if (!completed) {
                process.destroyForcibly();
                log.warn("WSL command timed out after {} seconds", timeoutSeconds);
            }
            
            return output.toString().trim();
        } catch (IOException | InterruptedException e) {
            log.debug("WSL command failed: {}", e.getMessage());
            return "";
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

    private int dockerPort;
    private boolean usingExistingDaemon = false;
    private static final Object EXISTING_DAEMON_LOCK = new Object();
    private static volatile boolean existingDaemonSetup = false;
    private static volatile int sharedDaemonPort = 0;
    private static volatile String sharedWslIp = null;
    
    private void startWsl2Docker() throws IOException, InterruptedException {
        ensureInstalled();
        
        if (tryConnectToExistingDaemon()) {
            log.info("Connected to existing Docker daemon in WSL2");
            usingExistingDaemon = true;
            return;
        }
        
        dockerPort = 2375 + Math.abs(instanceId.hashCode() % 1000);
        wslSocketPath = "tcp://0.0.0.0:" + dockerPort;
        String wslLogFile = "/tmp/docker-java-" + instanceId + ".log";
        
        String wslHome = runWslCommand("echo $HOME", false, 5);
        if (wslHome.isEmpty()) {
            wslHome = "/home/" + runWslCommand("whoami", false, 5);
        }
        log.debug("WSL home directory: {}", wslHome);
        
        String wslBase = getWslBaseDirectory();
        String wslBaseDir = wslHome + "/" + wslBase + "/instances/" + instanceId;
        String wslDataDir = wslBaseDir + "/data";
        String wslExecDir = wslBaseDir + "/exec";
        String wslPidFile = wslBaseDir + "/docker.pid";
        
        log.debug("Creating directories in WSL2 (distro: {})...", wslDistro);
        String mkdirResult = runWslCommand("mkdir -p " + wslDataDir + " " + wslExecDir + " && echo ok", false, 10);
        log.debug("mkdir result: {}", mkdirResult);
        
        runWslCommand("rm -f " + wslSocketPath, false, 5);
        
        String existingDocker = runWslCommand("pgrep -f 'dockerd' 2>/dev/null", false, 5);
        if (!existingDocker.isEmpty()) {
            log.warn("Another dockerd process may be running (PIDs: {}). This might cause conflicts.", existingDocker.replace("\n", ", "));
        }
        
        log.info("Starting Docker daemon in WSL2 (instance: {}, distro: {})...", instanceId, wslDistro);
        
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
        
        String existingDockerds = runWslCommand("pgrep -x dockerd 2>/dev/null | wc -l", false, 5).trim();
        boolean otherDockerdRunning = !"0".equals(existingDockerds) && !existingDockerds.isEmpty();
        
        String isolationFlags = "";
        if (otherDockerdRunning) {
            log.info("Another Docker daemon detected, using isolation flags to avoid conflicts");
            isolationFlags = " --iptables=false";
        }
        
        log.debug("Starting dockerd directly...");
        
        String dockerdCmd = String.format(
                "sudo dockerd -H %s --data-root %s --exec-root %s --pidfile %s%s",
                wslSocketPath, wslDataDir, wslExecDir, wslPidFile, isolationFlags);
        
        log.debug("Docker command: {}", dockerdCmd);
        
        String versionCheck = runWslCommand("sudo dockerd --version 2>&1", false, 10);
        log.debug("dockerd version check: {}", versionCheck);
        
        runWslCommand("rm -f " + wslLogFile, false, 5);
        
        log.debug("Executing dockerd command...");
        ProcessBuilder pb = new ProcessBuilder(
                "wsl", "-d", wslDistro, "--", "bash", "-c",
                "echo 'Starting dockerd...' >> " + wslLogFile + " && " +
                dockerdCmd + " >> " + wslLogFile + " 2>&1"
        );
        pb.redirectErrorStream(true);
        
        dockerProcess = pb.start();
        
        Thread.sleep(8000);
        
        if (!dockerProcess.isAlive()) {
            byte[] output = readAllBytes(dockerProcess.getInputStream());
            String outputStr = new String(output).trim();
            log.error("WSL process died. Output: {}", outputStr.isEmpty() ? "(empty)" : outputStr);
            
            String logContent = runWslCommand("cat " + wslLogFile + " 2>/dev/null || echo '(no log)'", false, 5);
            log.error("Log file contents:\n{}", logContent);
            
            throw new RuntimeException("dockerd failed to start. Check logs above.");
        }
        
        String dockerdCheck = runWslCommand("pgrep -x dockerd && echo 'running' || echo 'not running'", false, 5);
        log.debug("dockerd process check: {}", dockerdCheck.trim());
        
        String earlyLog = runWslCommand("cat " + wslLogFile + " 2>/dev/null | head -20", false, 5);
        if (!earlyLog.isEmpty()) {
            log.debug("Early log output:\n{}", earlyLog);
        }
        
        String ourDockerd = runWslCommand("pgrep -af 'dockerd.*" + dockerPort + "' 2>/dev/null || echo 'not found'", false, 5);
        log.debug("Our dockerd process (by port {}): {}", dockerPort, ourDockerd.trim());
        
        log.debug("WSL process is alive, waiting for port {}...", dockerPort);

        if (!waitForWslSocket()) {
            String portCheck = runWslCommand("ss -tlnp 2>/dev/null | grep " + dockerPort + " || echo 'port not found'", false, 5);
            log.error("Port {} status after wait: {}", dockerPort, portCheck.trim());
            String logContent = runWslCommand("cat " + wslLogFile + " 2>/dev/null", false, 5);
            if (!logContent.isEmpty()) {
                log.error("Docker daemon log:\n{}", logContent);
            } else {
                log.error("Docker daemon log is empty.");
            }
            
            String netstatCheck = runWslCommand("ss -tlnp 2>/dev/null | grep " + dockerPort + " || echo 'port not listening in WSL'", false, 5);
            log.error("Port {} status inside WSL: {}", dockerPort, netstatCheck.trim());
            
            String dockerdPs = runWslCommand("ps aux | grep dockerd | grep -v grep || echo 'no dockerd process'", false, 5);
            log.error("dockerd processes in WSL:\n{}", dockerdPs);
            
            String psOutput = runWslCommand("pgrep -af 'dockerd.*" + dockerPort + "' 2>/dev/null || echo '(none with our port)'", false, 5);
            log.error("Our dockerd process: {}", psOutput);
            
            String allDockerds = runWslCommand("pgrep -af dockerd 2>/dev/null || echo '(none)'", false, 5);
            if (allDockerds.contains("-H fd://")) {
                log.error("");
                log.error("Docker Desktop's dockerd is running in WSL2.");
                log.error("Stop it with: wsl -d {} -- sudo systemctl stop docker", wslDistro);
            }
            
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
     * Try to connect to an existing Docker daemon running in WSL2.
     * This checks if Docker is running, starts it if needed, and exposes it on TCP.
     * Uses synchronization to prevent race conditions when multiple threads call this.
     */
    private boolean tryConnectToExistingDaemon() {
        synchronized (EXISTING_DAEMON_LOCK) {
            if (existingDaemonSetup && sharedDaemonPort > 0 && sharedWslIp != null) {
                dockerPort = sharedDaemonPort;
                wslIpAddress = sharedWslIp;
                log.info("Using existing Docker daemon connection on port {}", dockerPort);
                return true;
            }
            
            try {
                String socketCheck = runWslCommand("test -S /var/run/docker.sock && echo yes || echo no", false, 5);
                if (!"yes".equals(socketCheck.trim())) {
                    log.debug("Docker socket /var/run/docker.sock does not exist");
                    
                    log.info("Docker daemon not running, attempting to start it...");
                    String startResult = runWslCommand("sudo service docker start 2>&1", false, 30);
                    log.debug("Docker service start result: {}", startResult);
                    
                    Thread.sleep(3000);
                    
                    socketCheck = runWslCommand("test -S /var/run/docker.sock && echo yes || echo no", false, 5);
                    if (!"yes".equals(socketCheck.trim())) {
                        log.debug("Docker socket still doesn't exist after service start");
                        return false;
                    }
                }
                
                log.info("Found Docker daemon in WSL2, setting up TCP forwarding...");
                
                String wslIp = runWslCommand("hostname -I | awk '{print $1}'", false, 5).trim();
                if (wslIp.isEmpty()) {
                    log.info("Could not get WSL2 IP address, will use isolated daemon");
                    return false;
                }
                wslIpAddress = wslIp;
                log.info("WSL2 IP: {}", wslIp);
                
                String socatPath = runWslCommand("command -v socat 2>/dev/null || echo ''", false, 5).trim();
                if (socatPath.isEmpty()) {
                    log.info("Installing socat for TCP forwarding...");
                    runWslCommand("sudo apt-get update -qq && sudo apt-get install -y -qq socat 2>&1", false, 120);
                    socatPath = runWslCommand("command -v socat 2>/dev/null || echo ''", false, 5).trim();
                    if (socatPath.isEmpty()) {
                        log.info("Failed to install socat, will use isolated daemon");
                        return false;
                    }
                }
                
                dockerPort = 2375;
                
                runWslCommand("sudo pkill -9 -f 'socat.*:" + dockerPort + "' 2>/dev/null; sleep 1", false, 10);
                
                String socketPerms = runWslCommand("ls -la /var/run/docker.sock 2>&1", false, 5);
                log.info("Docker socket: {}", socketPerms.trim());
                
                String socatCmd = String.format(
                    "sudo nohup socat TCP-LISTEN:%d,bind=0.0.0.0,reuseaddr,fork UNIX-CONNECT:/var/run/docker.sock </dev/null >/tmp/socat-%d.log 2>&1 &",
                    dockerPort, dockerPort);
                runWslCommand(socatCmd, false, 5);
                
                Thread.sleep(2000);
                
                String socatPid = runWslCommand("pgrep -f 'socat.*:" + dockerPort + "' 2>/dev/null | head -1 || echo ''", false, 5).trim();
                if (socatPid.isEmpty()) {
                    String socatLog = runWslCommand("cat /tmp/socat-" + dockerPort + ".log 2>/dev/null | head -20 || echo '(no log)'", false, 5);
                    log.info("socat failed to start. Log: {}", socatLog);
                    return false;
                }
                log.info("socat running with PID: {}", socatPid);
                
                String listenCheck = runWslCommand("ss -tlnp 2>/dev/null | grep ':" + dockerPort + " ' || echo 'not listening'", false, 5);
                log.info("Port {} status: {}", dockerPort, listenCheck.trim());
                
                boolean connected = testDockerConnection(wslIp, dockerPort);
                if (!connected) {
                    connected = testDockerConnection("localhost", dockerPort);
                    if (connected) {
                        wslIpAddress = "localhost";
                    }
                }
                
                if (connected) {
                    sharedDaemonPort = dockerPort;
                    sharedWslIp = wslIpAddress;
                    existingDaemonSetup = true;
                    log.info("Connected to Docker daemon via TCP at {}:{}", wslIpAddress, dockerPort);
                    return true;
                }
                
                String socatLog = runWslCommand("cat /tmp/socat-" + dockerPort + ".log 2>/dev/null | tail -10 || echo '(no log)'", false, 5);
                log.info("Connection failed, socat log: {}", socatLog);
                return false;
            } catch (Exception e) {
                log.info("Failed to connect to existing daemon: {}", e.getMessage());
                return false;
            }
        }
    }
    
    /**
     * Test Docker connection from Windows by attempting a TCP socket connection and HTTP request.
     */
    private boolean testDockerConnection(String host, int port) {
        try {
            log.info("Testing Docker connection to {}:{}...", host, port);
            
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), 3000);
            socket.close();
            log.info("TCP socket connection successful to {}:{}", host, port);
            
            java.net.URL url = new java.net.URL("http://" + host + ":" + port + "/version");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            
            if (responseCode == 200) {
                log.info("Docker API responding at {}:{}", host, port);
                return true;
            }
            log.info("Docker API returned HTTP {} at {}:{}", responseCode, host, port);
            return false;
        } catch (java.net.ConnectException e) {
            log.info("Connection refused to {}:{} - socat may not be forwarding correctly", host, port);
            return false;
        } catch (java.net.SocketTimeoutException e) {
            log.info("Connection timeout to {}:{}", host, port);
            return false;
        } catch (IOException e) {
            log.info("Connection test failed for {}:{} - {}: {}", host, port, e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if passwordless sudo is available for dockerd.
     */
    private boolean checkPasswordlessSudo() {
        try {
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
            ProcessBuilder pb = new ProcessBuilder("wsl", "-l", "-v");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            byte[] outputBytes = readAllBytes(process.getInputStream());
            String output = new String(outputBytes, java.nio.charset.StandardCharsets.UTF_16LE);
            
            int exitCode = process.waitFor();
            log.debug("WSL -l -v exit code: {}, output: {}", exitCode, output.trim());
            
            if (exitCode != 0) {
                log.debug("WSL not installed or not working");
                return false;
            }
            
            String[] lines = output.split("\n");
            String defaultDistro = null;
            String usableDistro = null;
            
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.contains("NAME") || line.contains("---")) {
                    continue;
                }
                
                if (!line.contains("2")) {
                    continue;
                }
                
                boolean isDefault = line.startsWith("*");
                String distroName = line.replace("*", "").trim().split("\\s+")[0];
                
                if (distroName.toLowerCase().startsWith("docker-desktop")) {
                    log.debug("Skipping Docker Desktop distro: {}", distroName);
                    if (isDefault) {
                        defaultDistro = distroName;
                    }
                    continue;
                }
                
                if (usableDistro == null || isDefault) {
                    usableDistro = distroName;
                    log.debug("Found usable WSL2 distro: {} (default: {})", distroName, isDefault);
                }
            }
            
            if (usableDistro != null) {
                wslDistro = usableDistro;
                log.info("Using WSL2 distro: {}", wslDistro);
                
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
        
        if (usingExistingDaemon && usingWsl2 && wslDistro != null) {
            try {
                ProcessBuilder pb = new ProcessBuilder("wsl", "-d", wslDistro, "-e", "bash", "-c",
                        "sudo -n pkill -f 'socat.*:" + dockerPort + "' 2>/dev/null || true");
                pb.start().waitFor(5, TimeUnit.SECONDS);
                log.info("Stopped socat TCP forwarder");
            } catch (IOException | InterruptedException e) {
                log.debug("Failed to stop socat: {}", e.getMessage());
            }
            return;
        }
        
        if (dockerProcess != null) {
            if (usingWsl2 && wslDistro != null) {
                try {
                    String wslBase = getWslBaseDirectory();
                    ProcessBuilder pb = new ProcessBuilder("wsl", "-d", wslDistro, "-e", "bash", "-c",
                            "sudo -n pkill -f 'dockerd.*" + dockerPort + "' 2>/dev/null ; " +
                            "rm -rf ~/" + wslBase + "/instances/" + instanceId);
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
        wslIpAddress = runWslCommand("hostname -I | awk '{print $1}'", false, 5).trim();
        if (wslIpAddress.isEmpty()) {
            wslIpAddress = "localhost";
        }
        log.debug("WSL2 IP address: {}", wslIpAddress);
        
        log.debug("Waiting for Docker daemon on {}:{}...", wslIpAddress, dockerPort);
        long timeoutMillis = TimeUnit.SECONDS.toMillis(60);
        long startTime = System.currentTimeMillis();
        int attempts = 0;
        
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
                    wslIpAddress = host;
                    log.debug("Docker daemon is listening on {}:{} after {} attempts", host, dockerPort, attempts);
                    
                    String verify = runWslCommand("ss -tlnp 2>/dev/null | grep " + dockerPort, false, 5);
                    log.debug("Port {} listener info: {}", dockerPort, verify.trim());
                    
                    return true;
                } catch (IOException e) {
                }
            }
            
            if (attempts % 20 == 0) {
                log.debug("Still waiting for Docker daemon... ({} seconds elapsed)", (System.currentTimeMillis() - startTime) / 1000);
            }
            
            Thread.sleep(500);
        }
        log.error("Timed out waiting for Docker daemon on port {} after {} seconds", dockerPort, (System.currentTimeMillis() - startTime) / 1000);
        return false;
    }
}
