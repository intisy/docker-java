package io.github.intisy.docker;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static io.github.intisy.docker.IOUtils.readAllBytes;

/**
 * Diagnostic test for WSL2 detection on Windows.
 * Run this test to see what's happening with WSL detection.
 *
 * @author Finn Birich
 */
public class WslDiagnosticTest {
    private static final Logger log = LoggerFactory.getLogger(WslDiagnosticTest.class);

    @Test
    @DisplayName("Diagnose WSL2 availability")
    void diagnoseWsl2() throws Exception {
        log.info("=== WSL2 Diagnostic Test ===");
        log.info("OS: {}", System.getProperty("os.name"));
        
        log.info("\n--- Test 1: WSL command availability ---");
        try {
            ProcessBuilder pb = new ProcessBuilder("wsl", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            byte[] output = readAllBytes(process.getInputStream());
            int exitCode = process.waitFor();
            
            String utf8 = new String(output, StandardCharsets.UTF_8);
            String utf16le = new String(output, StandardCharsets.UTF_16LE);
            
            log.info("wsl --version exit code: {}", exitCode);
            log.info("Output (UTF-8): {}", utf8.trim());
            log.info("Output (UTF-16LE): {}", utf16le.trim());
        } catch (Exception e) {
            log.error("wsl --version failed: {}", e.getMessage());
        }

        log.info("\n--- Test 2: WSL distributions ---");
        try {
            ProcessBuilder pb = new ProcessBuilder("wsl", "-l", "-v");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            byte[] output = readAllBytes(process.getInputStream());
            int exitCode = process.waitFor();
            
            String utf16le = new String(output, StandardCharsets.UTF_16LE);
            String utf8 = new String(output, StandardCharsets.UTF_8);
            
            log.info("wsl -l -v exit code: {}", exitCode);
            log.info("Output (UTF-16LE):\n{}", utf16le);
            log.info("Output (UTF-8):\n{}", utf8);
            
            boolean hasVersion2 = utf16le.contains("2") || utf8.contains("2");
            log.info("Contains '2': {}", hasVersion2);
        } catch (Exception e) {
            log.error("wsl -l -v failed: {}", e.getMessage());
        }

        log.info("\n--- Test 3: Execute command in WSL ---");
        try {
            ProcessBuilder pb = new ProcessBuilder("wsl", "-e", "echo", "hello from wsl");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            byte[] output = readAllBytes(process.getInputStream());
            int exitCode = process.waitFor();
            
            log.info("wsl -e echo exit code: {}", exitCode);
            log.info("Output: {}", new String(output, StandardCharsets.UTF_8).trim());
        } catch (Exception e) {
            log.error("wsl -e echo failed: {}", e.getMessage());
        }

        log.info("\n--- Test 4: Check for dockerd in WSL ---");
        try {
            ProcessBuilder pb = new ProcessBuilder("wsl", "-d", "Ubuntu", "-e", "bash", "-c", "command -v dockerd || echo 'not found'");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            byte[] output = readAllBytes(process.getInputStream());
            int exitCode = process.waitFor();
            
            log.info("dockerd check exit code: {}", exitCode);
            log.info("Output: {}", new String(output, StandardCharsets.UTF_8).trim());
        } catch (Exception e) {
            log.error("dockerd check failed: {}", e.getMessage());
        }
        
        log.info("\n--- Test 4b: Check docker group membership ---");
        try {
            ProcessBuilder pb = new ProcessBuilder("wsl", "-d", "Ubuntu", "-e", "bash", "-c", "groups && id");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            byte[] output = readAllBytes(process.getInputStream());
            int exitCode = process.waitFor();
            
            log.info("groups check exit code: {}", exitCode);
            log.info("Output: {}", new String(output, StandardCharsets.UTF_8).trim());
        } catch (Exception e) {
            log.error("groups check failed: {}", e.getMessage());
        }
        
        log.info("\n--- Test 4c: Try starting dockerd (will show errors) ---");
        try {
            ProcessBuilder pb = new ProcessBuilder("wsl", "-d", "Ubuntu", "--", "bash", "-c", 
                    "dockerd -H unix:///tmp/test-docker.sock 2>&1 & sleep 3; cat /tmp/test-docker.log 2>/dev/null; pkill -f 'dockerd.*test-docker'");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            byte[] output = readAllBytes(process.getInputStream());
            int exitCode = process.waitFor();
            
            log.info("dockerd test exit code: {}", exitCode);
            log.info("Output:\n{}", new String(output, StandardCharsets.UTF_8).trim());
        } catch (Exception e) {
            log.error("dockerd test failed: {}", e.getMessage());
        }

        log.info("\n--- Test 5: Default WSL distribution ---");
        try {
            ProcessBuilder pb = new ProcessBuilder("wsl", "-e", "cat", "/etc/os-release");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            byte[] output = readAllBytes(process.getInputStream());
            int exitCode = process.waitFor();
            
            log.info("os-release exit code: {}", exitCode);
            log.info("Output:\n{}", new String(output, StandardCharsets.UTF_8).trim());
        } catch (Exception e) {
            log.error("os-release check failed: {}", e.getMessage());
        }

        log.info("\n=== Diagnostic Complete ===");
    }

    @Test
    @DisplayName("Test WindowsDockerProvider WSL detection")
    void testProviderDetection() {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            log.info("Skipping Windows-specific test on {}", os);
            return;
        }

        log.info("Creating WindowsDockerProvider...");
        WindowsDockerProvider provider = new WindowsDockerProvider();
        log.info("Instance ID: {}", provider.getInstanceId());
        
        log.info("Note: Check debug logs above for WSL2 detection details");
    }
}
