package com.hookfinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages a collection of ActionTests with save/load and comparison features.
 */
public class ActionTestSuite {

    private static final String STORE_DIR = ".hookfinder";
    private static final String TESTS_FILE = "action_tests.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path storeFile;
    private List<ActionTest> tests = new ArrayList<>();
    private String gamepackVersion = "unknown";

    public ActionTestSuite() {
        Path home = Paths.get(System.getProperty("user.home"));
        Path dir = home.resolve(STORE_DIR);
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {}
        this.storeFile = dir.resolve(TESTS_FILE);
        load();
    }

    // ==================== Persistence ====================

    public void load() {
        if (!Files.exists(storeFile)) {
            tests = new ArrayList<>();
            return;
        }

        try (Reader reader = Files.newBufferedReader(storeFile, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<ActionTest>>() {}.getType();
            tests = GSON.fromJson(reader, listType);
            if (tests == null) tests = new ArrayList<>();
        } catch (Exception e) {
            System.err.println("[ActionTestSuite] Failed to load: " + e.getMessage());
            tests = new ArrayList<>();
        }
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(storeFile, StandardCharsets.UTF_8)) {
            GSON.toJson(tests, writer);
        } catch (IOException e) {
            System.err.println("[ActionTestSuite] Failed to save: " + e.getMessage());
        }
    }

    public void exportToFile(File file) throws IOException {
        try (Writer writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(tests, writer);
        }
    }

    public void importFromFile(File file) throws IOException {
        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<ActionTest>>() {}.getType();
            List<ActionTest> imported = GSON.fromJson(reader, listType);
            if (imported != null) {
                tests.addAll(imported);
                save();
            }
        }
    }

    // ==================== Test Management ====================

    public List<ActionTest> getTests() {
        return tests;
    }

    public void addTest(ActionTest test) {
        tests.add(test);
        save();
    }

    public void removeTest(ActionTest test) {
        tests.remove(test);
        save();
    }

    public void clearAll() {
        tests.clear();
        save();
    }

    public ActionTest getTestByName(String name) {
        return tests.stream()
                .filter(t -> t.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    // ==================== Analysis ====================

    /**
     * Find methods that appear in ALL tests (common/noise methods like game loop).
     */
    public List<ActionTest.MethodSignature> findCommonMethods() {
        if (tests.isEmpty()) return Collections.emptyList();

        Set<String> common = tests.get(0).getMethods().stream()
                .map(ActionTest.MethodSignature::getFullSignature)
                .collect(Collectors.toSet());

        for (int i = 1; i < tests.size(); i++) {
            Set<String> current = tests.get(i).getMethods().stream()
                    .map(ActionTest.MethodSignature::getFullSignature)
                    .collect(Collectors.toSet());
            common.retainAll(current);
        }

        return tests.get(0).getMethods().stream()
                .filter(m -> common.contains(m.getFullSignature()))
                .collect(Collectors.toList());
    }

    /**
     * Find methods UNIQUE to a specific action (not in any other test).
     */
    public List<ActionTest.MethodSignature> findUniqueMethods(ActionTest target) {
        Set<String> otherMethods = new HashSet<>();

        for (ActionTest test : tests) {
            if (test == target) continue;
            for (ActionTest.MethodSignature m : test.getMethods()) {
                otherMethods.add(m.getFullSignature());
            }
        }

        return target.getMethods().stream()
                .filter(m -> !otherMethods.contains(m.getFullSignature()))
                .sorted((a, b) -> Integer.compare(b.callCount, a.callCount))
                .collect(Collectors.toList());
    }

    /**
     * Compare two tests and find differences.
     */
    public ComparisonResult compare(ActionTest test1, ActionTest test2) {
        ComparisonResult result = new ComparisonResult();

        Set<String> sigs1 = test1.getMethods().stream()
                .map(ActionTest.MethodSignature::getFullSignature)
                .collect(Collectors.toSet());

        Set<String> sigs2 = test2.getMethods().stream()
                .map(ActionTest.MethodSignature::getFullSignature)
                .collect(Collectors.toSet());

        // Only in test1
        result.onlyInFirst = test1.getMethods().stream()
                .filter(m -> !sigs2.contains(m.getFullSignature()))
                .collect(Collectors.toList());

        // Only in test2
        result.onlyInSecond = test2.getMethods().stream()
                .filter(m -> !sigs1.contains(m.getFullSignature()))
                .collect(Collectors.toList());

        // In both
        result.inBoth = test1.getMethods().stream()
                .filter(m -> sigs2.contains(m.getFullSignature()))
                .collect(Collectors.toList());

        return result;
    }

    public static class ComparisonResult {
        public List<ActionTest.MethodSignature> onlyInFirst = new ArrayList<>();
        public List<ActionTest.MethodSignature> onlyInSecond = new ArrayList<>();
        public List<ActionTest.MethodSignature> inBoth = new ArrayList<>();
    }

    /**
     * Generate hook candidates: methods that are unique to specific actions.
     */
    public Map<String, List<ActionTest.MethodSignature>> generateHookCandidates() {
        Map<String, List<ActionTest.MethodSignature>> candidates = new LinkedHashMap<>();
        List<ActionTest.MethodSignature> common = findCommonMethods();
        Set<String> commonSigs = common.stream()
                .map(ActionTest.MethodSignature::getFullSignature)
                .collect(Collectors.toSet());

        for (ActionTest test : tests) {
            List<ActionTest.MethodSignature> unique = test.getMethods().stream()
                    .filter(m -> !commonSigs.contains(m.getFullSignature()))
                    .sorted((a, b) -> Integer.compare(b.callCount, a.callCount))
                    .limit(20)
                    .collect(Collectors.toList());

            candidates.put(test.getName(), unique);
        }

        return candidates;
    }

    // ==================== Predefined Action Templates ====================

    public static final String[] COMMON_ACTIONS = {
            "Walk Here",
            "Attack NPC",
            "Talk to NPC",
            "Pick Up Item",
            "Use Item",
            "Open Bank",
            "Deposit Item",
            "Withdraw Item",
            "Open Inventory",
            "Equip Item",
            "Cast Spell",
            "Use Object",
            "Examine",
            "Trade Player",
            "Follow Player"
    };

    public String getGamepackVersion() { return gamepackVersion; }
    public void setGamepackVersion(String version) { this.gamepackVersion = version; }
}