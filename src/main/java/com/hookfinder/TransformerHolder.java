package com.hookfinder;

/**
 * Static holder for the transformer instance.
 * Instrumented bytecode calls these static methods.
 *
 * IMPORTANT: This class MUST be loadable by the game's classloader.
 * Since it's in the agent JAR which is on the system classpath
 * (via -javaagent), it should be visible to all classloaders.
 */
public class TransformerHolder {

    private static volatile TracingTransformer transformer;

    public static void setTransformer(TracingTransformer t) {
        transformer = t;
    }

    public static void onMethodEnter(String owner, String name, String desc) {
        TracingTransformer t = transformer;
        if (t != null) {
            try {
                t.onMethodEnter(owner, name, desc);
            } catch (Throwable ignored) {
                // Never let tracing crash the game
            }
        }
    }

    public static void onMethodExit(String owner, String name, String desc) {
        TracingTransformer t = transformer;
        if (t != null) {
            try {
                t.onMethodExit(owner, name, desc);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void onFieldAccess(String owner, String name, String desc, boolean isWrite) {
        TracingTransformer t = transformer;
        if (t != null) {
            try {
                t.onFieldAccess(owner, name, desc, isWrite);
            } catch (Throwable ignored) {
            }
        }
    }
}