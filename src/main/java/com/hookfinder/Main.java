package com.hookfinder;

import com.hookfinder.gui.HookFinderGUI;
import com.hookfinder.agent.AgentAttacher;
import com.hookfinder.core.HookFinder;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            HookFinderGUI.main(args);
            return;
        }

        String command = args[0].toLowerCase();

        switch (command) {
            case "--attach":
            case "-a":
                handleAttach(args);
                break;

            case "--list":
            case "-l":
                handleList();
                break;

            case "--gui":
                HookFinderGUI.main(args);
                break;

            default:
                handleAnalyze(args[0]);
                break;
        }
    }

    private static void handleAttach(String[] args) {
        String jarPath = AgentAttacher.getOwnJarPath();
        if (jarPath == null) {
            System.err.println("Cannot determine HookFinder JAR path.");
            return;
        }
        System.out.println("[HookFinder] Agent JAR: " + jarPath);
        if (args.length >= 2) {
            AgentAttacher.attachToProcess(args[1], jarPath);
        } else {
            boolean found = AgentAttacher.attachToRuneLite(jarPath);
            if (!found) {
                System.out.println("\nTo attach manually:");
                System.out.println("  java -jar hookfinder-1.0-all.jar --attach <PID>");
            }
        }
    }

    private static void handleList() {
        System.out.println("[HookFinder] Java processes:\n");
        List<AgentAttacher.ProcessInfo> processes = AgentAttacher.listJavaProcesses();
        if (processes.isEmpty()) {
            System.out.println("  No Java processes found.");
            return;
        }
        for (AgentAttacher.ProcessInfo p : processes) {
            String marker = p.isRuneLite ? " <-- TARGET" : "";
            System.out.printf("  PID %-8s %s%s%n", p.pid, p.displayName, marker);
        }
    }

    private static void handleAnalyze(String path) throws Exception {
        File gamepackFile = new File(path);
        if (!gamepackFile.exists()) {
            System.err.println("File not found: " + path);
            return;
        }
        URL[] urls = {gamepackFile.toURI().toURL()};
        try (URLClassLoader classLoader = new URLClassLoader(urls, ClassLoader.getSystemClassLoader())) {
            HookFinder finder = new HookFinder(classLoader, gamepackFile);
            finder.run();
        }
    }
}