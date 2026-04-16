package com.hookfinder.pattern;

import com.hookfinder.core.*;

import org.objectweb.asm.*;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class SetXYPattern implements HookPattern {

    @Override
    public String name() { return "SetSelectedSceneTileX/Y (bw/bf)"; }

    @Override
    public List<HookResult> find(List<Class<?>> classes) {
        for (Class<?> cls : classes) {
            Method[] methods;
            try { methods = cls.getDeclaredMethods(); }
            catch (Throwable t) { continue; }

            List<MethodInfo> candidates = new ArrayList<>();

            for (Method method : methods) {
                int mods = method.getModifiers();
                if (!Modifier.isPublic(mods)) continue;
                if (Modifier.isStatic(mods)) continue;
                if (!method.getReturnType().equals(long.class)) continue;
                if (method.getParameterCount() != 3) continue;

                Class<?>[] p = method.getParameterTypes();
                if (p[0] == int.class && p[1] == int.class && p[2] == int.class) {
                    int instructionCount = getBytecodeInstructionCount(cls, method);
                    String fieldPattern  = getFieldAccessPattern(cls, method);
                    candidates.add(new MethodInfo(
                            cls.getName() + "." + method.getName(),
                            method.getName(), instructionCount, fieldPattern));
                }
            }

            if (!candidates.isEmpty()) {
                MethodInfo mostComplex = candidates.stream()
                        .max(Comparator.comparingInt(m -> m.instructionCount))
                        .orElse(null);

                if (mostComplex != null && mostComplex.instructionCount > 50) {
                    System.out.println("✓ setSelectedSceneTileX -> " + mostComplex.fullName
                            + " (complexity: " + mostComplex.instructionCount + ")");
                    HookFinder.emit(new HookResult("setSelectedSceneTileX",
                            cls.getName(), mostComplex.methodName,
                            "complexity=" + mostComplex.instructionCount));
                }

                Map<String, List<MethodInfo>> grouped = new HashMap<>();
                for (MethodInfo m : candidates) {
                    if (m == mostComplex) continue;
                    grouped.computeIfAbsent(m.fieldPattern, k -> new ArrayList<>()).add(m);
                }

                for (Map.Entry<String, List<MethodInfo>> entry : grouped.entrySet()) {
                    if (entry.getKey().contains(".ad")) {
                        MethodInfo first = entry.getValue().stream()
                                .min(Comparator.comparing(m -> m.methodName))
                                .orElse(null);
                        if (first != null) {
                            System.out.println("✓ setSelectedSceneTileY -> " + first.fullName
                                    + " (field: " + first.fieldPattern + ")");
                            HookFinder.emit(new HookResult("setSelectedSceneTileY",
                                    cls.getName(), first.methodName,
                                    "fieldPattern=" + first.fieldPattern));
                        }
                    }
                }
            }
        }
        return null;
    }

    private int getBytecodeInstructionCount(Class<?> cls, Method method) {
        try {
            String className = cls.getName().replace('.', '/') + ".class";
            InputStream is = cls.getClassLoader().getResourceAsStream(className);
            if (is == null) return 0;
            ClassReader cr = new ClassReader(is);
            InstructionCounter counter = new InstructionCounter(method.getName());
            cr.accept(counter, 0);
            return counter.count;
        } catch (Exception e) { return 0; }
    }

    private String getFieldAccessPattern(Class<?> cls, Method method) {
        try {
            String className = cls.getName().replace('.', '/') + ".class";
            InputStream is = cls.getClassLoader().getResourceAsStream(className);
            if (is == null) return "";
            ClassReader cr = new ClassReader(is);
            FieldAccessDetector detector = new FieldAccessDetector(method.getName());
            cr.accept(detector, 0);
            return detector.pattern;
        } catch (Exception e) { return ""; }
    }

    static class MethodInfo {
        String fullName, methodName, fieldPattern;
        int instructionCount;
        MethodInfo(String fn, String mn, int c, String fp) {
            fullName = fn; methodName = mn; instructionCount = c; fieldPattern = fp;
        }
    }

    static class InstructionCounter extends ClassVisitor {
        String targetMethod; int count = 0;
        InstructionCounter(String m) { super(Opcodes.ASM9); this.targetMethod = m; }

        @Override
        public MethodVisitor visitMethod(int a, String name, String d, String s, String[] e) {
            if (!name.equals(targetMethod)) return null;
            return new MethodVisitor(Opcodes.ASM9) {
                @Override public void visitInsn(int o)                                  { count++; }
                @Override public void visitIntInsn(int o, int op)                       { count++; }
                @Override public void visitVarInsn(int o, int v)                        { count++; }
                @Override public void visitTypeInsn(int o, String t)                    { count++; }
                @Override public void visitFieldInsn(int o, String ow, String n, String d) { count++; }
                @Override public void visitMethodInsn(int o, String ow, String n, String d, boolean i) { count++; }
                @Override public void visitJumpInsn(int o, Label l)                     { count++; }
            };
        }
    }

    static class FieldAccessDetector extends ClassVisitor {
        String targetMethod; String pattern = "";
        FieldAccessDetector(String m) { super(Opcodes.ASM9); this.targetMethod = m; }

        @Override
        public MethodVisitor visitMethod(int a, String name, String d, String s, String[] e) {
            if (!name.equals(targetMethod)) return null;
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitFieldInsn(int opcode, String owner, String fn, String desc) {
                    if (opcode == Opcodes.GETFIELD) pattern += "." + fn;
                }
            };
        }
    }
}