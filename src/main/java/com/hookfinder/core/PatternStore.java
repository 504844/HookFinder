package com.hookfinder.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists PatternDefinitions to/from a JSON file in the user's home directory.
 */
public class PatternStore {

    private static final String STORE_DIR = ".hookfinder";
    private static final String STORE_FILE = "patterns.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path storeFile;

    public PatternStore() {
        Path home = Paths.get(System.getProperty("user.home"));
        Path dir = home.resolve(STORE_DIR);
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {}
        this.storeFile = dir.resolve(STORE_FILE);
    }

    public List<PatternDefinition> load() {
        if (!Files.exists(storeFile)) {
            return createDefaults();
        }

        try (Reader reader = Files.newBufferedReader(storeFile, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<PatternDefinition>>() {}.getType();
            List<PatternDefinition> loaded = GSON.fromJson(reader, listType);
            return loaded != null ? loaded : new ArrayList<>();
        } catch (Exception e) {
            System.err.println("Failed to load patterns: " + e.getMessage());
            return createDefaults();
        }
    }

    public void save(List<PatternDefinition> patterns) {
        try (Writer writer = Files.newBufferedWriter(storeFile, StandardCharsets.UTF_8)) {
            GSON.toJson(patterns, writer);
        } catch (IOException e) {
            System.err.println("Failed to save patterns: " + e.getMessage());
        }
    }

    public Path getStoreFile() {
        return storeFile;
    }

    /**
     * Creates default patterns that match the original hardcoded ones.
     */
    private List<PatternDefinition> createDefaults() {
        List<PatternDefinition> defaults = new ArrayList<>();

        // InvokeMenuAction
        PatternDefinition invoke = new PatternDefinition();
        invoke.setName("InvokeMenuAction");
        invoke.setDescription("Static final void method with 11 params (6 int, 2 String, 2 int, 1 byte/int)");
        invoke.setVisibility(PatternDefinition.Visibility.ANY);
        invoke.setScope(PatternDefinition.Scope.STATIC);
        invoke.setRequireFinal(true);
        invoke.setReturnType(PatternDefinition.ReturnType.VOID);
        invoke.setParamCount(11);
        invoke.setParamTypes(List.of("int", "int", "int", "int", "int", "int",
                "String", "String", "int", "int", "*"));
        invoke.setExtractJunkValue(true);
        defaults.add(invoke);

        // SceneTile (X/Y/Walking) - bytecode-based pattern
        // Finds the walking method void(int,int,int,boolean) and extracts:
        //   setViewportWalkingFieldHook (static boolean field)
        //   setSelectedSceneTileX (static int field set to -1)
        //   setSelectedSceneTileY (static int field set to -1)
        PatternDefinition sceneTile = new PatternDefinition();
        sceneTile.setName("SceneTile (X/Y/Walking)");
        sceneTile.setDescription("Finds walking method void(int,int,int,boolean) "
                + "and extracts viewportWalking, tileX, tileY field hooks via bytecode");
        sceneTile.setVisibility(PatternDefinition.Visibility.PUBLIC);
        sceneTile.setScope(PatternDefinition.Scope.INSTANCE);
        sceneTile.setReturnType(PatternDefinition.ReturnType.VOID);
        sceneTile.setParamCount(4);
        sceneTile.setParamTypes(List.of("int", "int", "int", "boolean"));
        defaults.add(sceneTile);

        // ViewportWalking
        PatternDefinition vp = new PatternDefinition();
        vp.setName("ViewportWalking");
        vp.setDescription("Public static void method with 5 params (*, int, int, int, int)");
        vp.setVisibility(PatternDefinition.Visibility.PUBLIC);
        vp.setScope(PatternDefinition.Scope.STATIC);
        vp.setReturnType(PatternDefinition.ReturnType.VOID);
        vp.setParamCount(5);
        vp.setParamTypes(List.of("*", "int", "int", "int", "int"));
        defaults.add(vp);

        save(defaults);
        return defaults;
    }
}