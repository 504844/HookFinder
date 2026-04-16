package com.hookfinder;

import org.objectweb.asm.*;

public class TracingClassVisitor extends ClassVisitor {

    private final String className;
    private final TracingTransformer transformer;

    public TracingClassVisitor(ClassVisitor cv, String className, TracingTransformer transformer) {
        super(Opcodes.ASM9, cv);
        this.className = className;
        this.transformer = transformer;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                     String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (mv == null) return null;
        if (name.equals("<init>") || name.equals("<clinit>")) return mv;
        return new TracingMethodVisitor(mv, access, name, descriptor, className, transformer);
    }
}