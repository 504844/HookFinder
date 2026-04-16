package com.hookfinder;

import org.objectweb.asm.*;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TracingTransformer implements ClassFileTransformer {

    private final AtomicBoolean tracingEnabled = new AtomicBoolean(false);
    private final AtomicLong traceStartTime = new AtomicLong(0);
    private final AtomicLong traceDuration = new AtomicLong(500);

    private final Map<String, MethodTrace> methodTraces = new ConcurrentHashMap<>();
    private final Map<String, FieldTrace> fieldTraces = new ConcurrentHashMap<>();
    private final Set<String> transformedClasses = ConcurrentHashMap.newKeySet();

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {

        if (className == null) return null;

        // ONLY transform gamepack classes
        if (!isGamepackClass(className)) return null;

        try {
            transformedClasses.add(className);
            ClassReader cr = new ClassReader(classfileBuffer);

            // CRITICAL: Use SafeClassWriter instead of new ClassWriter(COMPUTE_FRAMES)
            // COMPUTE_FRAMES causes getCommonSuperClass() which loads classes
            // and triggers the duplicate class definition error in RuneLite
            ClassWriter cw = new SafeClassWriter(cr, ClassWriter.COMPUTE_MAXS, loader);
            ClassVisitor cv = new TracingClassVisitor(cw, className, this);
            cr.accept(cv, ClassReader.EXPAND_FRAMES);
            return cw.toByteArray();
        } catch (Exception e) {
            // On any error, return null to use original bytecode unchanged
            return null;
        }
    }

    /**
     * Safe ClassWriter that never calls ClassLoader.loadClass() during frame computation.
     * This prevents the duplicate class definition error in RuneLite's ClientLoader.
     */
    static class SafeClassWriter extends ClassWriter {
        private final ClassLoader loader;

        SafeClassWriter(ClassReader cr, int flags, ClassLoader loader) {
            super(cr, flags);
            this.loader = loader;
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            // Instead of loading classes (which causes LinkageError),
            // just return Object as the common superclass.
            // This is safe for our tracing injection — we only inject
            // simple static method calls, not anything that needs real frames.
            return "java/lang/Object";
        }
    }

    private boolean isGamepackClass(String className) {
        // Default package (no slashes): "a", "bw", "client", "gx"
        if (!className.contains("/")) return true;
        // osrs package
        if (className.startsWith("osrs/")) return true;
        // Everything else is a library — skip
        return false;
    }

    // ==================== Tracing Control ====================

    public void startTracing(long durationMs) {
        methodTraces.clear();
        fieldTraces.clear();
        traceDuration.set(durationMs);
        traceStartTime.set(System.currentTimeMillis());
        tracingEnabled.set(true);
        System.out.println("[HookFinder] Tracing started for " + durationMs + "ms");
    }

    public void stopTracing() {
        tracingEnabled.set(false);
        System.out.println("[HookFinder] Tracing stopped. Captured "
                + methodTraces.size() + " methods, " + fieldTraces.size() + " fields");
    }

    public boolean isTracingActive() {
        if (!tracingEnabled.get()) return false;
        if (System.currentTimeMillis() - traceStartTime.get() > traceDuration.get()) {
            stopTracing();
            return false;
        }
        return true;
    }

    // ==================== Callbacks from instrumented code ====================

    public void onMethodEnter(String owner, String name, String desc) {
        if (!isTracingActive()) return;
        String key = owner + "." + name + desc;
        methodTraces.computeIfAbsent(key, k -> new MethodTrace(owner, name, desc))
                .incrementCount();
    }

    public void onMethodExit(String owner, String name, String desc) {
        // Reserved for timing if needed later
    }

    public void onFieldAccess(String owner, String name, String desc, boolean isWrite) {
        if (!isTracingActive()) return;
        String key = owner + "." + name + (isWrite ? ":W" : ":R");
        fieldTraces.computeIfAbsent(key, k -> new FieldTrace(owner, name, desc, isWrite))
                .incrementCount();
    }

    // ==================== Results ====================

    public List<MethodTrace> getMethodTraces() {
        List<MethodTrace> list = new ArrayList<>(methodTraces.values());
        list.sort((a, b) -> Integer.compare(b.count, a.count));
        return list;
    }

    public List<FieldTrace> getFieldTraces() {
        List<FieldTrace> list = new ArrayList<>(fieldTraces.values());
        list.sort((a, b) -> Integer.compare(b.count, a.count));
        return list;
    }

    public Set<String> getTransformedClasses() {
        return Collections.unmodifiableSet(transformedClasses);
    }

    public void clearTraces() {
        methodTraces.clear();
        fieldTraces.clear();
    }

    // ==================== Data Classes ====================

    public static class MethodTrace {
        public final String owner;
        public final String name;
        public final String desc;
        public int count = 0;

        public MethodTrace(String owner, String name, String desc) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }

        public void incrementCount() { count++; }

        @Override
        public String toString() {
            return String.format("[%5d] %s.%s%s", count, owner, name, desc);
        }
    }

    public static class FieldTrace {
        public final String owner;
        public final String name;
        public final String desc;
        public final boolean isWrite;
        public int count = 0;

        public FieldTrace(String owner, String name, String desc, boolean isWrite) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
            this.isWrite = isWrite;
        }

        public void incrementCount() { count++; }

        @Override
        public String toString() {
            return String.format("[%5d] %s.%s (%s) %s",
                    count, owner, name, desc, isWrite ? "WRITE" : "READ");
        }
    }
}