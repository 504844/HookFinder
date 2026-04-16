package com.hookfinder;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Locates RuneLite's cached gamepack JAR files on disk.
 *
 * RuneLite stores gamepacks in:
 *   Windows: %USERPROFILE%\.runelite\cache\
 *   macOS:   ~/.runelite/cache/
 *   Linux:   ~/.runelite/cache/
 *
 * The files are typically named like "gamepack_###.jar" or just files
 * inside the jagex cache folder. RuneLite also uses:
 *   %USERPROFILE%\.runelite\repository2\cache\
 *
 * We search all known locations and return what we find.
 */
public class RuneLiteLocator {

    private static final String[] SEARCH_DIRS = {
            // Standard RuneLite cache
            ".runelite",
            // Flatpak RuneLite on Linux
            ".var/app/net.runelite.RuneLite/data/.runelite",
    };

    private static final String[] CACHE_SUBDIRS = {
            "cache",
            "repository2/cache",
            "repository2",
            "",  // root .runelite folder itself
    };

    /**
     * Find all gamepack JARs across known RuneLite locations.
     * Returns them sorted by last modified (newest first).
     */
    public static List<File> findGamepacks() {
        List<File> found = new ArrayList<>();
        String userHome = System.getProperty("user.home");

        for (String searchDir : SEARCH_DIRS) {
            Path runeliteRoot = Paths.get(userHome, searchDir);
            if (!Files.isDirectory(runeliteRoot)) continue;

            for (String sub : CACHE_SUBDIRS) {
                Path cacheDir = sub.isEmpty() ? runeliteRoot : runeliteRoot.resolve(sub);
                if (!Files.isDirectory(cacheDir)) continue;
                scanForGamepacks(cacheDir, found, 0);
            }
        }

        // Also check Jagex launcher cache location (Windows)
        Path jagexCache = Paths.get(userHome, "jagexcache", "oldschool", "LIVE");
        if (Files.isDirectory(jagexCache)) {
            scanForGamepacks(jagexCache, found, 0);
        }

        // Sort by last modified, newest first
        found.sort(Comparator.comparingLong(File::lastModified).reversed());
        return found;
    }

    /**
     * Get the single most recent gamepack, or null if none found.
     */
    public static File findLatestGamepack() {
        List<File> all = findGamepacks();
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Get the RuneLite root directory, or null if not found.
     */
    public static File findRuneLiteDir() {
        String userHome = System.getProperty("user.home");
        for (String searchDir : SEARCH_DIRS) {
            File dir = new File(userHome, searchDir);
            if (dir.isDirectory()) return dir;
        }
        return null;
    }

    private static void scanForGamepacks(Path dir, List<File> results, int depth) {
        if (depth > 3) return; // Don't recurse too deep

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                File file = entry.toFile();

                if (file.isFile() && isGamepackJar(file)) {
                    results.add(file);
                } else if (file.isDirectory() && depth < 3) {
                    scanForGamepacks(entry, results, depth + 1);
                }
            }
        } catch (Exception ignored) {
            // Permission denied, etc.
        }
    }

    private static boolean isGamepackJar(File file) {
        String name = file.getName().toLowerCase();
        if (!name.endsWith(".jar")) return false;

        // Known gamepack file names
        if (name.startsWith("gamepack")) return true;
        if (name.startsWith("injected-client-")) return true;
        if (name.equals("client.jar")) return true;
        if (name.startsWith("osrs-") && name.endsWith(".jar")) return true;

        // Check file size — gamepacks are typically 8-50MB
        long sizeMB = file.length() / (1024 * 1024);
        if (sizeMB >= 5 && sizeMB <= 60 && name.endsWith(".jar")) {
            return true;
        }

        return false;
    }

    /**
     * Returns a human-readable label for a gamepack file.
     */
    public static String getLabel(File file) {
        long sizeMB = file.length() / (1024 * 1024);
        long lastModified = file.lastModified();
        long ageMs = System.currentTimeMillis() - lastModified;
        String age;

        long ageMin = ageMs / 60000;
        if (ageMin < 60) {
            age = ageMin + "m ago";
        } else if (ageMin < 1440) {
            age = (ageMin / 60) + "h ago";
        } else {
            age = (ageMin / 1440) + "d ago";
        }

        return file.getName() + "  (" + sizeMB + " MB, " + age + ")";
    }
}