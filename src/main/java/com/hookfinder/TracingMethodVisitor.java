package com.hookfinder;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;

public class TracingMethodVisitor extends AdviceAdapter {

    private final String className;
    private final String methodName;
    private final String methodDesc;

    private static final String HOLDER = "com/hookfinder/TransformerHolder";

    public TracingMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                                String className, TracingTransformer transformer) {
        super(Opcodes.ASM9, mv, access, name, desc);
        this.className = className;
        this.methodName = name;
        this.methodDesc = desc;
    }

    @Override
    protected void onMethodEnter() {
        mv.visitLdcInsn(className);
        mv.visitLdcInsn(methodName);
        mv.visitLdcInsn(methodDesc);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOLDER, "onMethodEnter",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
    }

    @Override
    protected void onMethodExit(int opcode) {
        mv.visitLdcInsn(className);
        mv.visitLdcInsn(methodName);
        mv.visitLdcInsn(methodDesc);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOLDER, "onMethodExit",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        super.visitFieldInsn(opcode, owner, name, descriptor);

        boolean isWrite = (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC);
        mv.visitLdcInsn(owner);
        mv.visitLdcInsn(name);
        mv.visitLdcInsn(descriptor);
        mv.visitInsn(isWrite ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOLDER, "onFieldAccess",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V", false);
    }
}