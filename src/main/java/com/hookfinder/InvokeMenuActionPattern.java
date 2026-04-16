package com.hookfinder;

import org.objectweb.asm.*;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

public class InvokeMenuActionPattern implements HookPattern {

    @Override
    public String name() { return "InvokeMenuAction"; }

    @Override
    public List<HookResult> find(List<Class<?>> classes) {
        for (Class<?> cls : classes) {
            Method[] methods;
            try { methods = cls.getDeclaredMethods(); }
            catch (Throwable t) { continue; }

            for (Method method : methods) {
                int score = 0;
                if (method.getReturnType().equals(void.class)) score += 3;

                int mods = method.getModifiers();
                if (Modifier.isStatic(mods) && Modifier.isFinal(mods)) score += 2;

                Class<?>[] params = method.getParameterTypes();
                if (params.length != 11) continue;
                score += 1;

                if (params[0] == int.class && params[1] == int.class &&
                        params[2] == int.class && params[3] == int.class &&
                        params[4] == int.class && params[5] == int.class &&
                        params[6] == String.class && params[7] == String.class &&
                        params[8] == int.class && params[9] == int.class) score += 4;

                if (params[10] == byte.class || params[10] == int.class) score += 1;

                if (score >= 10) {
                    int junkValue = extractJunkValue(cls, method);
                    System.out.println("✓ FOUND InvokeMenuAction:");
                    System.out.println("  └─ Method: " + cls.getName() + "." + method.getName());
                    System.out.println("  └─ Junk Value: " + junkValue);
                    System.out.println("  └─ Last param type: " + params[10].getSimpleName());

                    HookResult r = new HookResult("invokeMenuActionHook",
                            cls.getName(), method.getName(),
                            "junk=" + junkValue + " lastParam=" + params[10].getSimpleName());
                    r.addDetail("junkValue", String.valueOf(junkValue));
                    r.addDetail("lastParamType", params[10].getSimpleName());
                    HookFinder.emit(r);
                }
            }
        }
        return null;
    }

    private int extractJunkValue(Class<?> cls, Method method) {
        try {
            String className = cls.getName().replace('.', '/') + ".class";
            InputStream is = cls.getClassLoader().getResourceAsStream(className);
            if (is == null) return 0;
            ClassReader cr = new ClassReader(is);
            JunkValueExtractor extractor = new JunkValueExtractor(method.getName());
            cr.accept(extractor, 0);
            return extractor.junkValue;
        } catch (Exception e) { return 0; }
    }

    static class JunkValueExtractor extends ClassVisitor {
        String targetMethod;
        int junkValue = 0;

        JunkValueExtractor(String methodName) {
            super(Opcodes.ASM9);
            this.targetMethod = methodName;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                                         String sig, String[] ex) {
            if (!name.equals(targetMethod)) return null;
            return new MethodVisitor(Opcodes.ASM9) {
                private int lastIntConst = 0;
                private boolean nextIsComparison = false;

                @Override public void visitLdcInsn(Object value) {
                    if (value instanceof Integer) { lastIntConst = (Integer) value; nextIsComparison = true; }
                }
                @Override public void visitIntInsn(int opcode, int operand) {
                    if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) { lastIntConst = operand; nextIsComparison = true; }
                }
                @Override public void visitJumpInsn(int opcode, Label label) {
                    if (nextIsComparison && (opcode == Opcodes.IF_ICMPNE || opcode == Opcodes.IF_ICMPEQ))
                        if (junkValue == 0) junkValue = lastIntConst;
                    nextIsComparison = false;
                }
                @Override public void visitVarInsn(int opcode, int var) {
                    if (opcode == Opcodes.ILOAD) nextIsComparison = true;
                }
            };
        }
    }
}