package com.hookfinder.pattern;

import com.hookfinder.core.*;

import org.objectweb.asm.*;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Finds the scene tile walking hook fields by analyzing the walking method.
 *
 * From decompiled kq class (rev 220):
 *   public void bn(int n, int n2, int n3, boolean bl) {
 *       if (kq.bp() && !bl) return;
 *       ba = true;
 *       by = bl;        // setViewportWalkingFieldHook (static boolean)
 *       bt = n;
 *       bk = n2;
 *       bm = n3;
 *       bw = -1;        // setSelectedSceneTileX (static int)
 *       bv = -1;        // setSelectedSceneTileY (static int)
 *   }
 *
 * Strategy:
 * 1. Find public instance void methods with (int, int, int, boolean) params
 * 2. Analyze bytecode for PUTSTATIC patterns:
 *    - Two static int fields set to -1 → selectedSceneTileX/Y
 *    - A static boolean field loaded from the boolean param → viewportWalkingFieldHook
 * 3. Also emit the walking method itself
 */
public class SceneTilePattern implements HookPattern {

    @Override
    public String name() { return "SceneTile (X/Y/Walking)"; }

    @Override
    public List<HookResult> find(List<Class<?>> classes) {
        for (Class<?> cls : classes) {
            Method[] methods;
            try { methods = cls.getDeclaredMethods(); }
            catch (Throwable t) { continue; }

            for (Method method : methods) {
                if (!isWalkingMethodCandidate(method)) continue;

                WalkingMethodAnalysis analysis = analyzeWalkingMethod(cls, method);
                if (analysis == null) continue;
                if (analysis.intFieldsSetToMinus1.size() < 2) continue;
                if (analysis.boolFieldFromParam == null) continue;

                String className = cls.getName();
                String methodName = method.getName();

                // Emit the walking method itself
                System.out.println("✓ walkingMethod -> " + className + "." + methodName
                        + "  (int, int, int, boolean)");
                HookFinder.emit(new HookResult("walkingMethod",
                        className, methodName,
                        "params=(int,int,int,boolean)"));

                // viewportWalkingFieldHook - the boolean field assigned from param
                FieldInfo boolField = analysis.boolFieldFromParam;
                String boolOwner = boolField.owner.replace('/', '.');
                System.out.println("✓ setViewportWalkingFieldHook -> "
                        + boolOwner + "." + boolField.name + "  [static boolean]");
                HookResult vwResult = new HookResult("setViewportWalkingFieldHook",
                        boolOwner, boolField.name, "static boolean field");
                vwResult.addDetail("type", "boolean");
                vwResult.addDetail("foundVia", className + "." + methodName);
                HookFinder.emit(vwResult);

                // selectedSceneTileX - first int field set to -1
                FieldInfo xField = analysis.intFieldsSetToMinus1.get(0);
                String xOwner = xField.owner.replace('/', '.');
                System.out.println("✓ setSelectedSceneTileX -> "
                        + xOwner + "." + xField.name + "  [static int]");
                HookResult xResult = new HookResult("setSelectedSceneTileX",
                        xOwner, xField.name, "static int field");
                xResult.addDetail("type", "int");
                xResult.addDetail("foundVia", className + "." + methodName);
                HookFinder.emit(xResult);

                // selectedSceneTileY - second int field set to -1
                FieldInfo yField = analysis.intFieldsSetToMinus1.get(1);
                String yOwner = yField.owner.replace('/', '.');
                System.out.println("✓ setSelectedSceneTileY -> "
                        + yOwner + "." + yField.name + "  [static int]");
                HookResult yResult = new HookResult("setSelectedSceneTileY",
                        yOwner, yField.name, "static int field");
                yResult.addDetail("type", "int");
                yResult.addDetail("foundVia", className + "." + methodName);
                HookFinder.emit(yResult);

                return null; // Found all hooks, stop searching
            }
        }
        System.out.println("✗ SceneTile hooks NOT FOUND");
        return null;
    }

    /**
     * Matches the walking method signature: public void xxx(int, int, int, boolean)
     * This is an instance method (non-static).
     */
    private boolean isWalkingMethodCandidate(Method method) {
        int mods = method.getModifiers();
        if (!Modifier.isPublic(mods)) return false;
        if (Modifier.isStatic(mods)) return false;
        if (!method.getReturnType().equals(void.class)) return false;
        if (method.getParameterCount() != 4) return false;

        Class<?>[] p = method.getParameterTypes();
        return p[0] == int.class && p[1] == int.class
                && p[2] == int.class && p[3] == boolean.class;
    }

    /**
     * Analyze the bytecode of a candidate walking method to extract field hooks.
     */
    private WalkingMethodAnalysis analyzeWalkingMethod(Class<?> cls, Method method) {
        try {
            String className = cls.getName().replace('.', '/') + ".class";
            InputStream is = cls.getClassLoader().getResourceAsStream(className);
            if (is == null) return null;
            ClassReader cr = new ClassReader(is);
            WalkingMethodVisitor visitor = new WalkingMethodVisitor(method.getName());
            cr.accept(visitor, 0);
            return visitor.analysis;
        } catch (Exception e) { return null; }
    }

    // ==================== Data Classes ====================

    static class FieldInfo {
        final String owner;
        final String name;
        final String desc;

        FieldInfo(String owner, String name, String desc) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FieldInfo)) return false;
            FieldInfo that = (FieldInfo) o;
            return owner.equals(that.owner) && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return (owner + "." + name).hashCode();
        }
    }

    static class WalkingMethodAnalysis {
        final List<FieldInfo> intFieldsSetToMinus1 = new ArrayList<>();
        FieldInfo boolFieldFromParam = null;
    }

    // ==================== ASM Visitor ====================

    /**
     * ASM visitor that tracks PUTSTATIC patterns in the walking method:
     * - ICONST_M1 followed by PUTSTATIC for int fields → tile X/Y
     * - ILOAD (of boolean param) followed by PUTSTATIC for boolean field → walking flag
     */
    static class WalkingMethodVisitor extends ClassVisitor {
        final String targetMethod;
        final WalkingMethodAnalysis analysis = new WalkingMethodAnalysis();

        WalkingMethodVisitor(String methodName) {
            super(Opcodes.ASM9);
            this.targetMethod = methodName;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                                         String sig, String[] ex) {
            if (!name.equals(targetMethod)) return null;
            // Match the descriptor: (IIIZ)V = (int, int, int, boolean) -> void
            if (!"(IIIZ)V".equals(desc)) return null;

            return new MethodVisitor(Opcodes.ASM9) {
                private int lastOpcode = -1;
                private int lastIloadVar = -1;

                @Override
                public void visitInsn(int opcode) {
                    if (opcode == Opcodes.ICONST_M1) {
                        lastOpcode = Opcodes.ICONST_M1;
                        lastIloadVar = -1;
                    } else {
                        lastOpcode = opcode;
                        lastIloadVar = -1;
                    }
                }

                @Override
                public void visitVarInsn(int opcode, int var) {
                    if (opcode == Opcodes.ILOAD) {
                        lastOpcode = Opcodes.ILOAD;
                        lastIloadVar = var;
                    } else {
                        lastOpcode = opcode;
                        lastIloadVar = -1;
                    }
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String fieldName,
                                           String fieldDesc) {
                    if (opcode == Opcodes.PUTSTATIC) {
                        if (lastOpcode == Opcodes.ICONST_M1 && "I".equals(fieldDesc)) {
                            // Static int field set to -1 → tile X or Y
                            FieldInfo fi = new FieldInfo(owner, fieldName, fieldDesc);
                            if (!analysis.intFieldsSetToMinus1.contains(fi)) {
                                analysis.intFieldsSetToMinus1.add(fi);
                            }
                        } else if (lastOpcode == Opcodes.ILOAD && "Z".equals(fieldDesc)) {
                            // For instance method (int, int, int, boolean):
                            //   slot 0 = this
                            //   slot 1 = int n
                            //   slot 2 = int n2
                            //   slot 3 = int n3
                            //   slot 4 = boolean bl
                            // So ILOAD 4 + PUTSTATIC Z = boolean param assignment
                            if (lastIloadVar == 4 && analysis.boolFieldFromParam == null) {
                                analysis.boolFieldFromParam =
                                        new FieldInfo(owner, fieldName, fieldDesc);
                            }
                        }
                    }
                    lastOpcode = opcode;
                    lastIloadVar = -1;
                }

                @Override
                public void visitIntInsn(int opcode, int operand) {
                    lastOpcode = opcode;
                    lastIloadVar = -1;
                }

                @Override
                public void visitTypeInsn(int opcode, String type) {
                    lastOpcode = opcode;
                    lastIloadVar = -1;
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String name,
                                            String desc, boolean itf) {
                    lastOpcode = opcode;
                    lastIloadVar = -1;
                }

                @Override
                public void visitJumpInsn(int opcode, Label label) {
                    lastOpcode = opcode;
                    lastIloadVar = -1;
                }

                @Override
                public void visitLdcInsn(Object value) {
                    lastOpcode = -1;
                    lastIloadVar = -1;
                }
            };
        }
    }
}
