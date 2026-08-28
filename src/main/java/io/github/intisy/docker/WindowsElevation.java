package io.github.intisy.docker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Detects whether the current JVM runs with administrator privileges and
 * runs PowerShell scripts through the Windows elevation (UAC) dialog.
 * Shared by docker-java itself and by consumers such as kubernetes-java,
 * so elevation behavior stays identical across libraries and machines.
 *
 * @author Finn Birich
 */
public final class WindowsElevation {

    private static final Logger log = LoggerFactory.getLogger(WindowsElevation.class);

    /** Returned by {@link #runElevated} when the elevated process could not be launched at all. */
    public static final int EXIT_LAUNCH_FAILED = Integer.MIN_VALUE;

    private static final long ADMIN_CHECK_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(10);
    private static final long ELEVATED_RUN_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(15);

    private WindowsElevation() {
    }

    /**
     * Whether the current process runs with administrator privileges.
     * Probes with {@code net session}, which succeeds only in an elevated context.
     */
    public static boolean isAdministrator() {
        try {
            ProcessRunner.Result result = ProcessRunner.run(ADMIN_CHECK_TIMEOUT_MILLIS, "net", "session");
            log.debug("Admin check (net session) exit code: {}", result.exitCode);
            return !result.timedOut && result.exitCode == 0;
        } catch (IOException e) {
            log.debug("Admin check failed with exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Runs a PowerShell script in an elevated process, opening the Windows
     * elevation (UAC) dialog when the current process is not elevated.
     * Blocks until the elevated process exits and returns its exit code.
     *
     * The script travels as {@code -EncodedCommand} (Base64 UTF-16LE), so no quoting
     * or escaping of the script content is ever needed.
     *
     * @param powershellScript script to execute elevated; its {@code exit} code is propagated
     * @return the elevated script's exit code; a declined UAC dialog yields a non-zero
     *         code from the launcher, {@link #EXIT_LAUNCH_FAILED} if nothing could be started
     */
    public static int runElevated(String powershellScript) {
        String launcher = launcherCommand(toEncodedCommand(powershellScript));
        try {
            ProcessRunner.Result result = ProcessRunner.run(ELEVATED_RUN_TIMEOUT_MILLIS,
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", launcher);
            if (!result.output.trim().isEmpty()) {
                log.debug("Elevated command output: {}", result.output.trim());
            }
            if (result.timedOut) {
                log.warn("Elevated command timed out");
                return EXIT_LAUNCH_FAILED;
            }
            return result.exitCode;
        } catch (IOException e) {
            log.warn("Failed to run elevated command: {}", e.getMessage());
            return EXIT_LAUNCH_FAILED;
        }
    }

    static String toEncodedCommand(String script) {
        return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    }

    static String launcherCommand(String encodedCommand) {
        return "$p = Start-Process powershell.exe -ArgumentList "
                + "'-NoProfile','-NonInteractive','-EncodedCommand','" + encodedCommand + "'"
                + " -Verb RunAs -Wait -PassThru; exit $p.ExitCode";
    }
}
