package io.github.intisy.docker.registry;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Turns a directory into one gzipped tar layer.
 *
 * @implNote every field that could vary between two builds of the same tree is pinned: entries are
 * sorted, modification times are zero, and uid and gid are zero with no owner names. Without that
 * the digest changes on every build and the node re-pulls a layer whose content is identical.
 *
 * @author Finn Birich
 */
public final class LayerBuilder {
    private static final int EXECUTABLE_MODE = 0755;
    private static final int REGULAR_MODE = 0644;
    private static final int DIRECTORY_MODE = 0755;
    private static final int BUFFER_BYTES = 64 * 1024;

    public static Layer fromDirectory(final Path source, String pathInImage, Path outputFile) throws IOException {
        final List<Path> files = new ArrayList<Path>();
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                files.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(files, new Comparator<Path>() {
            @Override
            public int compare(Path a, Path b) {
                return archiveName(source, a).compareTo(archiveName(source, b));
            }
        });

        String prefix = pathInImage.startsWith("/") ? pathInImage.substring(1) : pathInImage;
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }

        Files.createDirectories(outputFile.getParent());
        OutputStream fileOut = Files.newOutputStream(outputFile);
        TarArchiveOutputStream tar = new TarArchiveOutputStream(new GZIPOutputStream(fileOut));
        try {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (String directory : directoriesFor(prefix, source, files)) {
                TarArchiveEntry entry = new TarArchiveEntry(directory);
                pin(entry, DIRECTORY_MODE, 0);
                tar.putArchiveEntry(entry);
                tar.closeArchiveEntry();
            }
            for (Path file : files) {
                String name = prefix + archiveName(source, file);
                long size = Files.size(file);
                TarArchiveEntry entry = new TarArchiveEntry(name);
                pin(entry, isUnderBin(name) ? EXECUTABLE_MODE : REGULAR_MODE, size);
                tar.putArchiveEntry(entry);
                copy(file, tar);
                tar.closeArchiveEntry();
            }
        } finally {
            tar.close();
        }

        String digest = digestOf(Files.newInputStream(outputFile));
        String diffId = digestOf(new java.util.zip.GZIPInputStream(Files.newInputStream(outputFile)));
        return new Layer(outputFile, diffId, digest, Files.size(outputFile));
    }

    /**
     * @implNote a rule, not an inspection: Windows cannot report the POSIX executable bit, so
     * reading it would make the layer depend on which machine built it, and the launcher script
     * would be non-executable exactly on the machine this project is developed on.
     */
    static boolean isUnderBin(String nameInArchive) {
        return nameInArchive.contains("/bin/") || nameInArchive.startsWith("bin/");
    }

    static List<String> directoriesFor(String prefix, Path source, List<Path> files) {
        List<String> directories = new ArrayList<String>();
        java.util.Set<String> seen = new java.util.TreeSet<String>();
        for (Path file : files) {
            String relative = archiveName(source, file);
            int slash = relative.indexOf('/');
            while (slash >= 0) {
                seen.add(prefix + relative.substring(0, slash) + "/");
                slash = relative.indexOf('/', slash + 1);
            }
        }
        String[] segments = prefix.split("/");
        StringBuilder built = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            built.append(segments[i]).append('/');
            directories.add(built.toString());
        }
        directories.addAll(seen);
        return directories;
    }

    /**
     * @implNote {@code Path} natural ordering is case-insensitive on Windows and case-sensitive on
     * Linux, so sorting by {@code Path} directly would order two names differing only by case
     * differently depending on the building platform, changing the digest with it. This is the
     * string that is actually written to the archive, so sorting by it is platform-independent.
     */
    static String archiveName(Path source, Path file) {
        return source.relativize(file).toString().replace('\\', '/');
    }

    /**
     * @implNote the uid, gid and owner name calls are redundant with commons-compress 1.24.0's own
     * defaults for the {@code TarArchiveEntry(String)} constructor used above: it reads
     * {@code userName} from {@code System.getProperty("user.name")} during construction and resets
     * it to {@code ""} immediately afterward, in that same overload's own body. The calls stay to
     * guard against a future change to which constructor is used, which would otherwise leak the
     * building machine's identity into the layer and silently break cross-machine determinism.
     */
    private static void pin(TarArchiveEntry entry, int mode, long size) {
        entry.setMode((entry.isDirectory() ? TarArchiveEntry.DEFAULT_DIR_MODE : 0) | mode);
        entry.setModTime(0L);
        entry.setUserId(0);
        entry.setGroupId(0);
        entry.setUserName("");
        entry.setGroupName("");
        entry.setSize(size);
    }

    private static void copy(Path file, OutputStream out) throws IOException {
        InputStream in = new FileInputStream(file.toFile());
        try {
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            in.close();
        }
    }

    private static String digestOf(InputStream stream) throws IOException {
        try {
            return Digests.sha256(stream);
        } finally {
            stream.close();
        }
    }

    private LayerBuilder() {}
}
