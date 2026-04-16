package com.hookfinder;

import java.lang.reflect.Method;
import java.util.List;

public class CustomPattern implements HookPattern {

    private final PatternDefinition definition;

    public CustomPattern(PatternDefinition definition) {
        this.definition = definition;
    }

    @Override
    public String name() { return definition.getName(); }

    @Override
    public List<HookResult> find(List<Class<?>> classes) {
        for (Class<?> cls : classes) {
            if (isLibraryClass(cls)) continue;
            Method[] methods;
            try { methods = cls.getDeclaredMethods(); }
            catch (Throwable t) { continue; }

            for (Method method : methods) {
                int score = definition.score(cls, method);
                if (score >= definition.getMinScore()) {
                    System.out.println("✓ [" + definition.getName() + "] -> "
                            + cls.getName() + "." + method.getName()
                            + " score=" + score);
                    HookFinder.emit(new HookResult(definition.getName(),
                            cls.getName(), method.getName(), "score=" + score));
                }
            }
        }
        return null;
    }

    private boolean isLibraryClass(Class<?> cls) {
        String name = cls.getName();
        return name.startsWith("java.") || name.startsWith("javax.")
                || name.startsWith("sun.") || name.startsWith("com.sun.")
                || name.startsWith("net.runelite.") || name.startsWith("org.");
    }
}