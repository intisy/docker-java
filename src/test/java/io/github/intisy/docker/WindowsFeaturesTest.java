package io.github.intisy.docker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the pure command-building and parsing logic behind the
 * Windows optional feature and elevation helpers (no PowerShell required).
 *
 * @author Finn Birich
 */
@Tag("unit")
public class WindowsFeaturesTest {

    @Test
    @DisplayName("enableScript targets the feature with -All -NoRestart and maps RestartNeeded to exit 3010")
    void testEnableScript() {
        String script = WindowsFeatures.enableScript(WindowsFeatures.HYPER_V);
        assertTrue(script.contains("Enable-WindowsOptionalFeature"));
        assertTrue(script.contains("-FeatureName Microsoft-Hyper-V"));
        assertTrue(script.contains("-All"));
        assertTrue(script.contains("-NoRestart"));
        assertTrue(script.contains("exit 3010"));
        assertTrue(script.contains("exit 0"));
    }

    @Test
    @DisplayName("queryStateScript reads the feature state property")
    void testQueryStateScript() {
        String script = WindowsFeatures.queryStateScript(WindowsFeatures.CONTAINERS);
        assertTrue(script.contains("Get-WindowsOptionalFeature"));
        assertTrue(script.contains("-FeatureName Containers"));
        assertTrue(script.contains(".State"));
    }

    @Test
    @DisplayName("parseFeatureState accepts only an Enabled state, ignoring surrounding whitespace")
    void testParseFeatureState() {
        assertTrue(WindowsFeatures.parseFeatureState("Enabled"));
        assertTrue(WindowsFeatures.parseFeatureState("Enabled\r\n"));
        assertTrue(WindowsFeatures.parseFeatureState("  Enabled  "));
        assertFalse(WindowsFeatures.parseFeatureState("Disabled"));
        assertFalse(WindowsFeatures.parseFeatureState("EnabledPending"));
        assertFalse(WindowsFeatures.parseFeatureState(""));
        assertFalse(WindowsFeatures.parseFeatureState("Get-WindowsOptionalFeature : The requested operation requires elevation."));
    }

    @Test
    @DisplayName("manualEnableCommand is the exact command an operator runs in an elevated shell")
    void testManualEnableCommand() {
        assertEquals("Enable-WindowsOptionalFeature -Online -FeatureName Microsoft-Hyper-V -All",
                WindowsFeatures.manualEnableCommand(WindowsFeatures.HYPER_V));
    }

    @Test
    @DisplayName("feature name constants match the Windows optional feature names")
    void testFeatureConstants() {
        assertEquals("Microsoft-Hyper-V", WindowsFeatures.HYPER_V);
        assertEquals("Containers", WindowsFeatures.CONTAINERS);
        assertEquals(3010, WindowsFeatures.EXIT_REBOOT_REQUIRED);
    }

    @Test
    @DisplayName("toEncodedCommand produces the Base64 UTF-16LE form PowerShell -EncodedCommand expects")
    void testEncodedCommand() {
        String script = "Enable-WindowsOptionalFeature -Online -FeatureName 'Microsoft-Hyper-V' -All";
        String encoded = WindowsElevation.toEncodedCommand(script);
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_16LE);
        assertEquals(script, decoded);
    }

    @Test
    @DisplayName("launcherCommand elevates a nested powershell and propagates its exit code")
    void testLauncherCommand() {
        String launcher = WindowsElevation.launcherCommand("QQBCAEMA");
        assertTrue(launcher.contains("Start-Process"));
        assertTrue(launcher.contains("-Verb RunAs"));
        assertTrue(launcher.contains("-Wait"));
        assertTrue(launcher.contains("-PassThru"));
        assertTrue(launcher.contains("'QQBCAEMA'"));
        assertTrue(launcher.contains("exit $p.ExitCode"));
    }
}
