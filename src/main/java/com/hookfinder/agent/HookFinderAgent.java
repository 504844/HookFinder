package com.hookfinder.agent;

import java.lang.instrument.Instrumentation;

public class HookFinderAgent {

    private static Instrumentation instrumentation;
    private static TracingTransformer transformer;

    public static void premain(String args, Instrumentation inst) {
        System.out.println("[HookFinder] Agent loaded (premain)");
        initialize(args, inst);
    }

    public static void agentmain(String args, Instrumentation inst) {
        System.out.println("[HookFinder] Agent attached (agentmain)");
        initialize(args, inst);
    }

    private static void initialize(String args, Instrumentation inst) {
        instrumentation = inst;
        transformer = new TracingTransformer();

        // Register transformer — new classes will be instrumented as they load
        // The transformer itself handles filtering (gamepack only)
        inst.addTransformer(transformer, true);

        // Set up the static holder so instrumented code can call back
        TransformerHolder.setTransformer(transformer);

        // Launch GUI in background — wait for RuneLite to fully start
        new Thread(() -> {
            try {
                // Wait for RuneLite + gamepack to fully load before we retransform
                // and before showing the GUI
                System.out.println("[HookFinder] Waiting 15 seconds for RuneLite to fully load...");
                Thread.sleep(15000);

                retransformGamepackClasses(inst);
                TracingControlGUI.launch(transformer, inst);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "HookFinder-Init").start();

        System.out.println("[HookFinder] Transformer registered. Game loading...");
    }

    private static void retransformGamepackClasses(Instrumentation inst) {
        int count = 0;
        int skipped = 0;

        for (Class<?> cls : inst.getAllLoadedClasses()) {
            if (!inst.isModifiableClass(cls)) continue;

            String name = cls.getName();

            // ONLY retransform gamepack classes (default package or osrs.*)
            boolean isGamepack = !name.contains(".") || name.startsWith("osrs.");
            if (!isGamepack) continue;

            try {
                inst.retransformClasses(cls);
                count++;
            } catch (Exception e) {
                skipped++;
            }
        }

        System.out.println("[HookFinder] Retransformed " + count
                + " gamepack classes (" + skipped + " skipped)");
    }
}