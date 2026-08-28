package io.github.intisy.docker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Checks and enables Windows optional features (Hyper-V, Containers, ...)
 * reproducibly: an elevated process enables the feature directly, a
 * non-elevated one opens the Windows elevation dialog, and if that is
 * declined or unavailable the exact one-time command an operator must run
 * in an elevated shell is printed and thrown.
 *
 * <p>Set {@code -Ddocker.auto.setup=false} to suppress the elevation dialog
 * and always fail with the manual command instead.</p>
 *
 * @author Finn Birich
 */
public final class WindowsFeatures {

    private static final Logger log = LoggerFactory.getLogger(WindowsFeatures.class);

    public static final String HYPER_V = "Microsoft-Hyper-V";
    public static final String CONTAINERS = "Containers";

    /** Service present once Hyper-V is enabled; readable without elevation. */
    public static final String HYPER_V_WITNESS_SERVICE = "vmms";

    /** Windows convention for "operation succeeded, reboot required" (ERROR_SUCCESS_REBOOT_REQUIRED). */
    static final int EXIT_REBOOT_REQUIRED = 3010;

    private static final long QUERY_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final long ENABLE_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(15);

    private WindowsFeatures() {
    }

    /**
     * Whether the given Windows optional feature is enabled.
     * {@code Get-WindowsOptionalFeature -Online} itself requires elevation, so in a
     * non-elevated process this returns false even for enabled features; use
     * {@link #isServiceInstalled} with a witness service for a non-elevated probe.
     */
    public static boolean isEnabled(String featureName) {
        try {
            ProcessRunner.Result result = ProcessRunner.run(QUERY_TIMEOUT_MILLIS,
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", queryStateScript(featureName));
            return !result.timedOut && result.exitCode == 0 && parseFeatureState(result.output);
        } catch (IOException e) {
            log.debug("Feature check for {} failed: {}", featureName, e.getMessage());
            return false;
        }
    }

    /**
     * Whether a Windows service exists, elevation not required. Useful as a
     * positive witness that a feature is already enabled (for example
     * {@link #HYPER_V_WITNESS_SERVICE} for {@link #HYPER_V}).
     */
    public static boolean isServiceInstalled(String serviceName) {
        try {
            ProcessRunner.Result result = ProcessRunner.run(TimeUnit.SECONDS.toMillis(10),
                    "sc.exe", "query", serviceName);
            return !result.timedOut && result.exitCode == 0;
        } catch (IOException e) {
            log.debug("Service check for {} failed: {}", serviceName, e.getMessage());
            return false;
        }
    }

    /**
     * Ensures a Windows optional feature is enabled, without a witness service.
     *
     * @see #ensureEnabled(String, String)
     */
    public static void ensureEnabled(String featureName) throws IOException {
        ensureEnabled(featureName, null);
    }

    /**
     * Ensures a Windows optional feature is enabled. Elevated processes enable it
     * directly; non-elevated ones open the Windows elevation dialog. A declined or
     * unavailable dialog fails with the exact command to run in an elevated shell.
     * Throws a RuntimeException with a clear message when the feature was enabled
     * but Windows requires a reboot before it becomes usable.
     *
     * @param featureName    the Windows optional feature name, e.g. {@link #HYPER_V}
     * @param witnessService optional service name proving the feature is already
     *                       enabled without elevation, e.g. {@link #HYPER_V_WITNESS_SERVICE};
     *                       may be null
     */
    public static void ensureEnabled(String featureName, String witnessService) throws IOException {
        if (WindowsElevation.isAdministrator()) {
            if (isEnabled(featureName)) {
                log.debug("Windows feature {} is already enabled", featureName);
                return;
            }
            log.info("Enabling Windows feature {}...", featureName);
            ProcessRunner.Result result = ProcessRunner.run(ENABLE_TIMEOUT_MILLIS,
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", enableScript(featureName));
            handleEnableExit(featureName, result.timedOut ? Integer.MIN_VALUE : result.exitCode);
        } else {
            if (witnessService != null && isServiceInstalled(witnessService)) {
                log.debug("Windows feature {} is already enabled (service {} present)", featureName, witnessService);
                return;
            }
            if ("false".equals(System.getProperty("docker.auto.setup"))) {
                failWithManualInstructions(featureName,
                        "automatic setup is disabled (docker.auto.setup=false)");
            }
            log.info("Windows feature {} requires elevation. Opening the Windows elevation prompt...", featureName);
            handleEnableExit(featureName, WindowsElevation.runElevated(enableScript(featureName)));
        }
    }

    private static void handleEnableExit(String featureName, int exitCode) {
        if (exitCode == 0) {
            log.info("Windows feature {} is enabled", featureName);
            return;
        }
        if (exitCode == EXIT_REBOOT_REQUIRED) {
            log.error("Windows feature {} was enabled but requires a reboot.", featureName);
            throw new RuntimeException("Windows feature '" + featureName
                    + "' was enabled. Reboot, then run the application again.");
        }
        failWithManualInstructions(featureName, "the elevation prompt was declined or the enable command failed");
    }

    private static void failWithManualInstructions(String featureName, String reason) {
        log.error("");
        log.error("The Windows feature '{}' is required but not enabled, and {}.", featureName, reason);
        log.error("Please run this ONE-TIME setup:");
        log.error("  1) Open PowerShell as Administrator");
        log.error("  2) Run: {}", manualEnableCommand(featureName));
        log.error("  3) Reboot if the command reports it is required");
        log.error("  4) Run your application again");
        throw new RuntimeException("Windows feature '" + featureName
                + "' is not enabled. Run in an elevated PowerShell: " + manualEnableCommand(featureName));
    }

    static String enableScript(String featureName) {
        return "$r = Enable-WindowsOptionalFeature -Online -FeatureName " + featureName
                + " -All -NoRestart; if ($r.RestartNeeded) { exit 3010 } else { exit 0 }";
    }

    static String queryStateScript(String featureName) {
        return "(Get-WindowsOptionalFeature -Online -FeatureName " + featureName + ").State";
    }

    static boolean parseFeatureState(String output) {
        return "Enabled".equals(output.trim());
    }

    static String manualEnableCommand(String featureName) {
        return "Enable-WindowsOptionalFeature -Online -FeatureName " + featureName + " -All";
    }
}
