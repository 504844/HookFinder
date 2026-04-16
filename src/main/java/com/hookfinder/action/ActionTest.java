package com.hookfinder.action;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a single recorded action test (e.g., "Walk", "Attack NPC").
 * Captures which methods/fields fired during the trace window.
 */
public class ActionTest {

    private String name;
    private String description;
    private long timestamp;
    private int traceDurationMs;

    private List<MethodSignature> methods = new ArrayList<>();
    private List<FieldSignature> fields = new ArrayList<>();

    public ActionTest() {
        this.timestamp = System.currentTimeMillis();
    }

    public ActionTest(String name) {
        this();
        this.name = name;
    }

    // ==================== Method Signatures ====================

    public static class MethodSignature {
        public String owner;
        public String name;
        public String desc;
        public int callCount;
        public int rank;

        public MethodSignature() {}

        public MethodSignature(String owner, String name, String desc, int callCount) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
            this.callCount = callCount;
        }

        public String getFullSignature() {
            return owner + "." + name + desc;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MethodSignature)) return false;
            MethodSignature that = (MethodSignature) o;
            return owner.equals(that.owner) && name.equals(that.name) && desc.equals(that.desc);
        }

        @Override
        public int hashCode() {
            return (owner + name + desc).hashCode();
        }

        @Override
        public String toString() {
            return String.format("[%d] %s.%s%s", callCount, owner, name, desc);
        }
    }

    // ==================== Field Signatures ====================

    public static class FieldSignature {
        public String owner;
        public String name;
        public String desc;
        public int readCount;
        public int writeCount;

        public FieldSignature() {}

        public FieldSignature(String owner, String name, String desc, int readCount, int writeCount) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
            this.readCount = readCount;
            this.writeCount = writeCount;
        }

        public String getFullSignature() {
            return owner + "." + name + ":" + desc;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FieldSignature)) return false;
            FieldSignature that = (FieldSignature) o;
            return owner.equals(that.owner) && name.equals(that.name) && desc.equals(that.desc);
        }

        @Override
        public int hashCode() {
            return (owner + name + desc).hashCode();
        }

        @Override
        public String toString() {
            return String.format("%s.%s [R:%d W:%d]", owner, name, readCount, writeCount);
        }
    }

    // ==================== Getters/Setters ====================

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getTraceDurationMs() { return traceDurationMs; }
    public void setTraceDurationMs(int traceDurationMs) { this.traceDurationMs = traceDurationMs; }

    public List<MethodSignature> getMethods() { return methods; }
    public void setMethods(List<MethodSignature> methods) { this.methods = methods; }

    public List<FieldSignature> getFields() { return fields; }
    public void setFields(List<FieldSignature> fields) { this.fields = fields; }

    // ==================== Utilities ====================

    public List<MethodSignature> getTopMethods(int n) {
        return methods.stream()
                .sorted((a, b) -> Integer.compare(b.callCount, a.callCount))
                .limit(n)
                .collect(Collectors.toList());
    }

    public List<MethodSignature> getUniqueMethods(List<MethodSignature> commonMethods) {
        return methods.stream()
                .filter(m -> !commonMethods.contains(m))
                .sorted((a, b) -> Integer.compare(b.callCount, a.callCount))
                .collect(Collectors.toList());
    }

    public List<MethodSignature> findMethodsByDesc(String descPattern) {
        return methods.stream()
                .filter(m -> m.desc.contains(descPattern))
                .collect(Collectors.toList());
    }
}