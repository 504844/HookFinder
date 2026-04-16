package com.hookfinder.pattern;

import com.hookfinder.core.*;

import org.objectweb.asm.*;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

public class CheckClickPattern implements HookPattern {

    @Override
    public String name() { return "SetCheckClick"; }

    @Override
    public List<HookResult> find(List<Class<?>> classes) {
        for (Class<?> cls : classes) {
            Method[] methods;
            try { methods = cls.getDeclaredMethods(); }
            catch (Throwable t) { continue; }

            for (Method method : methods) {
                if (!isInvokeCandidate(method)) continue;
                String field = findFirstStaticBooleanGet(cls, method.getName());
                if (field != null) {
                    System.out.println("✓ setCheckClick -> "
                            + cls.getName() + "." + field
                            + "  [invoke method: " + method.getName() + "]");
                    HookFinder.emit(new HookResult("setCheckClick",
                            cls.getName(), field,
                            "in invoke method " + method.getName()));
                    return null;
                }
            }
        }
        System.out.println("✗ setCheckClick NOT FOUND");
        return null;
    }

    private boolean isInvokeCandidate(Method m) {
        int mods = m.getModifiers();
        if (!Modifier.isStatic(mods)) return false;
        if (!m.getReturnType().equals(void.class)) return false;
        Class<?>[] p = m.getParameterTypes();
        if (p.length != 11) return false;
        return p[0] == int.class && p[1] == int.class
                && p[2] == int.class && p[3] == int.class
                && p[4] == int.class && p[5] == int.class
                && p[6] == String.class && p[7] == String.class
                && p[8] == int.class && p[9] == int.class
                && (p[10] == byte.class || p[10] == int.class);
    }

    private String findFirstStaticBooleanGet(Class<?> cls, String methodName) {
        try {
            String res = cls.getName().replace('.', '/') + ".class";
            InputStream is = cls.getClassLoader().getResourceAsStream(res);
            if (is == null) return null;
            ClassReader cr = new ClassReader(is);
            FirstGetstaticBoolVisitor v = new FirstGetstaticBoolVisitor(methodName);
            cr.accept(v, 0);
            return v.fieldName;
        } catch (Exception e) { return null; }
    }

    static class FirstGetstaticBoolVisitor extends ClassVisitor {
        final String targetMethod;
        String fieldName = null;

        FirstGetstaticBoolVisitor(String m) { super(Opcodes.ASM9); this.targetMethod = m; }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                                         String sig, String[] ex) {
            if (!name.equals(targetMethod)) return null;
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitFieldInsn(int opcode, String owner, String fName, String fDesc) {
                    if (fieldName == null && opcode == Opcodes.GETSTATIC && fDesc.equals("Z"))
                        fieldName = fName;
                }
            };
        }
    }
}