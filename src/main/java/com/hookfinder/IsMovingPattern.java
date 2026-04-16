package com.hookfinder;

import org.objectweb.asm.*;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;

public class IsMovingPattern implements HookPattern {

    private static final int EXPECTED_JUNK = -1921456255;

    @Override
    public String name() { return "IsMoving"; }

    @Override
    public List<HookResult> find(List<Class<?>> classes) {
        boolean found = false;
        for (Class<?> cls : classes) {
            Method[] methods;
            try { methods = cls.getDeclaredMethods(); }
            catch (Throwable t) { continue; }

            for (Method method : methods) {
                Class<?>[] p = method.getParameterTypes();
                if (p.length != 1 || p[0] != int.class) continue;
                Class<?> ret = method.getReturnType();
                if (ret != boolean.class && ret != int.class) continue;

                if (containsJunkValue(cls, method.getName(), EXPECTED_JUNK)) {
                    System.out.println("✓ isMoving -> "
                            + cls.getName() + "." + method.getName()
                            + "  [junk=" + EXPECTED_JUNK + "]");
                    HookFinder.emit(new HookResult("isMoving",
                            cls.getName(), method.getName(),
                            "junk=" + EXPECTED_JUNK));
                    found = true;
                }
            }
        }
        if (!found) System.out.println("✗ isMoving NOT FOUND");
        return null;
    }

    private boolean containsJunkValue(Class<?> cls, String methodName, int junk) {
        try {
            String res = cls.getName().replace('.', '/') + ".class";
            InputStream is = cls.getClassLoader().getResourceAsStream(res);
            if (is == null) return false;
            ClassReader cr = new ClassReader(is);
            JunkSearchVisitor v = new JunkSearchVisitor(methodName, junk);
            cr.accept(v, 0);
            return v.found;
        } catch (Exception e) { return false; }
    }

    static class JunkSearchVisitor extends ClassVisitor {
        final String targetMethod;
        final int junkValue;
        boolean found = false;

        JunkSearchVisitor(String m, int junk) {
            super(Opcodes.ASM9);
            this.targetMethod = m;
            this.junkValue    = junk;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                                         String sig, String[] ex) {
            if (!name.equals(targetMethod)) return null;
            return new MethodVisitor(Opcodes.ASM9) {
                @Override public void visitLdcInsn(Object value) {
                    if (value instanceof Integer && (Integer) value == junkValue) found = true;
                }
                @Override public void visitIntInsn(int opcode, int operand) {
                    if (operand == junkValue) found = true;
                }
            };
        }
    }
}