package com.hookfinder.core;

import com.hookfinder.pattern.*;

import java.io.File;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class HookFinder {

    private final URLClassLoader classLoader;
    private final File gamepackFile;
    private final List<HookPattern> patterns = new ArrayList<>();

    private final List<HookResult> collectedResults = new ArrayList<>();
    private static HookFinder current;

    private int loadedClassCount = 0;
    private int failedClassCount = 0;

    public HookFinder(URLClassLoader classLoader, File gamepackFile) {
        this.classLoader  = classLoader;
        this.gamepackFile = gamepackFile;
    }

    public static void emit(HookResult result) {
        if (current != null) current.collectedResults.add(result);
    }

    public void registerBuiltinPatterns() {
        patterns.add(new InvokeMenuActionPattern());
        patterns.add(new SetXYPattern());
        patterns.add(new ViewportWalkingPattern());
        patterns.add(new CheckClickPattern());
        patterns.add(new IsMovingPattern());
    }

    public void registerCustomPatterns(List<PatternDefinition> defs) {
        if (defs == null) return;
        for (PatternDefinition def : defs)
            if (def.isEnabled()) patterns.add(new CustomPattern(def));
    }

    public List<HookPattern> getPatterns()  { return patterns;         }
    public int getLoadedClassCount()        { return loadedClassCount; }
    public int getFailedClassCount()        { return failedClassCount; }

    public List<HookResult> run(List<HookPattern> enabledPatterns) throws Exception {
        collectedResults.clear();
        current = this;
        try {
            List<Class<?>> allClasses = loadAllClasses();
            System.out.println("Loaded " + allClasses.size() + " classes\n");
            for (HookPattern pattern : enabledPatterns) {
                System.out.println("=== " + pattern.name() + " ===");
                pattern.find(allClasses);
                System.out.println();
            }
        } finally {
            current = null;
        }
        return new ArrayList<>(collectedResults);
    }

    public void run() throws Exception {
        registerBuiltinPatterns();
        run(patterns);
    }

    private List<Class<?>> loadAllClasses() throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        loadedClassCount = 0;
        failedClassCount = 0;

        JarFile jar = new JarFile(gamepackFile);
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!entry.getName().endsWith(".class")) continue;
            String className = entry.getName().replace("/", ".").replace(".class", "");
            try {
                classes.add(classLoader.loadClass(className));
                loadedClassCount++;
            } catch (Throwable t) {
                failedClassCount++;
            }
        }
        jar.close();
        return classes;
    }
}