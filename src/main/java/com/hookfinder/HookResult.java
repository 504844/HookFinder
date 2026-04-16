package com.hookfinder;

import java.util.LinkedHashMap;
import java.util.Map;

public class HookResult {
    private final String patternName;
    private final String className;
    private final String methodName;
    private final Map<String, String> details = new LinkedHashMap<>();

    public HookResult(String patternName, String className, String methodName, String extra) {
        this.patternName = patternName;
        this.className   = className;
        this.methodName  = methodName;
        if (extra != null && !extra.isEmpty()) details.put("info", extra);
    }

    public String getPatternName()            { return patternName; }
    public String getClassName()              { return className;   }
    public String getMethodName()             { return methodName;  }
    public Map<String, String> getDetails()   { return details;     }
    public void addDetail(String k, String v) { details.put(k, v); }

    @Override
    public String toString() {
        return patternName + " -> " + className + "." + methodName;
    }
}