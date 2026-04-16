package com.hookfinder.agent;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Attaches HookFinder agent to a running JVM (OldSchool.exe / RuneLite .exe).
 *
 * OldSchool.exe bundles a JRE and launches java.exe under the hood.
 * We find that java.exe process and inject our agent into it.
 */
public class AgentAttacher {

    public static List<ProcessInfo> listJavaProcesses() {
        List<ProcessInfo> result = new ArrayList<>();
        for (VirtualMachineDescriptor vmd : VirtualMachine.list()) {
            result.add(new ProcessInfo(
                    vmd.id(),
                    vmd.displayName(),
                    isTargetProcess(vmd.displayName())
            ));
        }
        return result;
    }

    /**
     * Auto-find OldSchool / RuneLite process and attach.
     */
    public static boolean attachToRuneLite(String agentJarPath) {
        for (VirtualMachineDescriptor vmd : VirtualMachine.list()) {
            if (isTargetProcess(vmd.displayName())) {
                System.out.println("[HookFinder] Found target: PID " + vmd.id()
                        + " (" + vmd.displayName() + ")");
                return attachToProcess(vmd.id(), agentJarPath);
            }
        }

        System.err.println("[HookFinder] No OldSchool/RuneLite process found.");
        System.err.println("Available Java processes:");
        for (VirtualMachineDescriptor vmd : VirtualMachine.list()) {
            System.err.println("  PID " + vmd.id() + " - " + vmd.displayName());
        }
        return false;
    }

    public static boolean attachToProcess(String pid, String agentJarPath) {
        try {
            System.out.println("[HookFinder] Attaching to PID " + pid + "...");

            VirtualMachine vm = VirtualMachine.attach(pid);
            vm.loadAgent(agentJarPath);
            vm.detach();

            System.out.println("[HookFinder] Agent loaded successfully!");
            System.out.println("[HookFinder] Tracer GUI should appear on the target process.");
            return true;

        } catch (AttachNotSupportedException e) {
            System.err.println("[HookFinder] Cannot attach to PID " + pid);
            System.err.println("  This JVM doesn't support dynamic attach.");
            System.err.println("  The .exe may bundle a JRE that blocks attach.");
            System.err.println();
            System.err.println("  WORKAROUND: Find where OldSchool.exe stores its JRE");
            System.err.println("  (look for a 'jre' folder next to the .exe) and launch manually:");
            System.err.println("    jre\\bin\\java.exe -javaagent:hookfinder.jar -jar OldSchool_BETA.jar");
            return false;
        } catch (IOException e) {
            System.err.println("[HookFinder] IO error: " + e.getMessage());
            System.err.println("  Make sure you run as the same user that launched the game.");
            return false;
        } catch (Exception e) {
            System.err.println("[HookFinder] Failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static String getOwnJarPath() {
        try {
            File jar = new File(AgentAttacher.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return jar.getAbsolutePath();
        } catch (Exception e) {
            File fallback = new File("hookfinder-1.0-all.jar");
            if (fallback.exists()) return fallback.getAbsolutePath();
            return null;
        }
    }

    private static boolean isTargetProcess(String displayName) {
        if (displayName == null) return false;
        String lower = displayName.toLowerCase();

        // Don't detect ourselves
        if (lower.contains("hookfinder")) return false;

        return lower.contains("oldschool") ||
                lower.contains("runelite") ||
                lower.contains("net.runelite") ||
                lower.contains("osrs") ||
                lower.contains("runescape") ||
                lower.contains("jagex");
    }

    public static class ProcessInfo {
        public final String pid;
        public final String displayName;
        public final boolean isRuneLite;

        public ProcessInfo(String pid, String displayName, boolean isRuneLite) {
            this.pid = pid;
            this.displayName = displayName;
            this.isRuneLite = isRuneLite;
        }
    }
}