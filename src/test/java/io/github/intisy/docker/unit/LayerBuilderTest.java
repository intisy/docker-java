package io.github.intisy.docker.unit;

import io.github.intisy.docker.registry.Digests;
import io.github.intisy.docker.registry.Layer;
import io.github.intisy.docker.registry.LayerBuilder;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
public class LayerBuilderTest {

    private static Path launcherLikeTree(Path root) throws IOException {
        Path dist = root.resolve("dist");
        Files.createDirectories(dist.resolve("bin"));
        Files.createDirectories(dist.resolve("lib"));
        Files.write(dist.resolve("bin/launcher"), "#!/bin/sh\nexec java -cp ...\n".getBytes(StandardCharsets.UTF_8));
        Files.write(dist.resolve("bin/launcher.bat"), "@echo off\n".getBytes(StandardCharsets.UTF_8));
        Files.write(dist.resolve("lib/launcher.jar"), new byte[] {0x50, 0x4b, 0x03, 0x04});
        return dist;
    }

    @Test
    public void entriesAreRootedAtThePathInImage(@TempDir Path tmp) throws IOException {
        Layer layer = LayerBuilder.fromDirectory(
                launcherLikeTree(tmp), "/opt/app/launcher", tmp.resolve("layer.tar.gz"));

        List<String> names = entryNames(layer.file());
        assertTrue(names.contains("opt/app/launcher/bin/launcher"), names.toString());
        assertTrue(names.contains("opt/app/launcher/lib/launcher.jar"), names.toString());
    }

    @Test
    public void filesUnderBinAreExecutableAndOthersAreNot(@TempDir Path tmp) throws IOException {
        Layer layer = LayerBuilder.fromDirectory(
                launcherLikeTree(tmp), "/opt/app/launcher", tmp.resolve("layer.tar.gz"));

        assertEquals(0755, modeOf(layer.file(), "opt/app/launcher/bin/launcher"));
        assertEquals(0644, modeOf(layer.file(), "opt/app/launcher/lib/launcher.jar"));
    }

    @Test
    public void twoBuildsOfTheSameTreeProduceTheSameDigest(@TempDir Path tmp) throws IOException {
        Path source = launcherLikeTree(tmp);
        Layer first = LayerBuilder.fromDirectory(source, "/opt/app/launcher", tmp.resolve("a.tar.gz"));
        Layer second = LayerBuilder.fromDirectory(source, "/opt/app/launcher", tmp.resolve("b.tar.gz"));

        assertEquals(first.diffId(), second.diffId());
        assertEquals(first.digest(), second.digest());
    }

    @Test
    public void changingAFileChangesTheDigest(@TempDir Path tmp) throws IOException {
        Path source = launcherLikeTree(tmp);
        Layer before = LayerBuilder.fromDirectory(source, "/opt/app/launcher", tmp.resolve("a.tar.gz"));
        Files.write(source.resolve("lib/launcher.jar"), new byte[] {0x50, 0x4b, 0x03, 0x05});
        Layer after = LayerBuilder.fromDirectory(source, "/opt/app/launcher", tmp.resolve("b.tar.gz"));

        assertNotEquals(before.diffId(), after.diffId());
    }

    /**
     * The diff id is the digest of the uncompressed tar and the digest is the digest of the gzip.
     * Conflating them produces a config whose rootfs entry no layer can satisfy, and the registry
     * accepts the push and the node then fails to unpack.
     */
    @Test
    public void diffIdIsTheUncompressedDigestAndDigestIsTheCompressedOne(@TempDir Path tmp) throws IOException {
        Layer layer = LayerBuilder.fromDirectory(
                launcherLikeTree(tmp), "/opt/app/launcher", tmp.resolve("layer.tar.gz"));

        assertNotEquals(layer.diffId(), layer.digest());
        assertEquals(layer.digest(), sha256Closing(new FileInputStream(layer.file().toFile())));
        assertEquals(layer.diffId(),
                sha256Closing(new GZIPInputStream(new FileInputStream(layer.file().toFile()))));
        assertEquals(Files.size(layer.file()), layer.size());
    }

    /**
     * Determinism comes from pinning every field that could otherwise vary between two builds of
     * the same tree. Asserting the resulting digests match is not enough on its own: tar stores
     * mtime at one-second granularity, so two builds in the same second agree even when nothing
     * is pinned at all.
     */
    @Test
    public void everyEntryHasItsVaryingMetadataPinned(@TempDir Path tmp) throws IOException {
        Layer layer = LayerBuilder.fromDirectory(
                launcherLikeTree(tmp), "/opt/app/launcher", tmp.resolve("layer.tar.gz"));

        TarArchiveInputStream tar = openTar(layer.file());
        try {
            int entries = 0;
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                entries++;
                assertEquals(0L, entry.getModTime().getTime(), entry.getName() + " mtime");
                assertEquals(0, entry.getLongUserId(), entry.getName() + " uid");
                assertEquals(0, entry.getLongGroupId(), entry.getName() + " gid");
                assertEquals("", entry.getUserName(), entry.getName() + " user name");
                assertEquals("", entry.getGroupName(), entry.getName() + " group name");
            }
            assertTrue(entries > 0, "expected entries in the layer");
        } finally {
            tar.close();
        }
    }

    /**
     * {@code java.nio.file.Path} natural ordering is case-insensitive on Windows and
     * case-sensitive on Linux, so sorting {@code Path} objects directly would order these two
     * files differently depending on which platform built the layer, changing the digest with it.
     * The archive must order entries by the normalized name string instead, which is what actually
     * gets written and is platform-independent.
     */
    @Test
    public void entriesAreOrderedByTheArchiveNameNotThePlatformPathOrdering(@TempDir Path tmp) throws IOException {
        Path dist = tmp.resolve("dist");
        Files.createDirectories(dist);
        Files.write(dist.resolve("Zeta.txt"), new byte[] {1});
        Files.write(dist.resolve("apple.txt"), new byte[] {1});

        Layer layer = LayerBuilder.fromDirectory(dist, "/opt/app/launcher", tmp.resolve("layer.tar.gz"));

        List<String> names = entryNames(layer.file());
        int zeta = names.indexOf("opt/app/launcher/Zeta.txt");
        int apple = names.indexOf("opt/app/launcher/apple.txt");
        assertTrue(zeta >= 0 && apple >= 0, names.toString());
        assertEquals("Zeta.txt".compareTo("apple.txt") < 0, zeta < apple, names.toString());
    }

    /**
     * @implNote {@link Digests#sha256(InputStream)} does not close its argument; on Windows a
     * lingering open handle on the layer file blocks {@code @TempDir} cleanup after the test.
     */
    private static String sha256Closing(InputStream stream) throws IOException {
        try {
            return Digests.sha256(stream);
        } finally {
            stream.close();
        }
    }

    private static List<String> entryNames(Path archive) throws IOException {
        List<String> names = new ArrayList<String>();
        TarArchiveInputStream tar = openTar(archive);
        try {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                names.add(entry.getName());
            }
        } finally {
            tar.close();
        }
        return names;
    }

    private static int modeOf(Path archive, String name) throws IOException {
        TarArchiveInputStream tar = openTar(archive);
        try {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                if (name.equals(entry.getName())) {
                    return entry.getMode() & 0777;
                }
            }
        } finally {
            tar.close();
        }
        throw new IOException("no entry " + name + " in " + archive);
    }

    private static TarArchiveInputStream openTar(Path archive) throws IOException {
        InputStream raw = new FileInputStream(archive.toFile());
        return new TarArchiveInputStream(new GZIPInputStream(raw));
    }
}
