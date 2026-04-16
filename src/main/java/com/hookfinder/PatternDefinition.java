package com.hookfinder;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PatternDefinition {

    // ── Enums required by PatternEditorPanel and PatternStore ─────────────
    public enum Visibility { ANY, PUBLIC, PRIVATE, PROTECTED, PACKAGE }
    public enum Scope      { ANY, STATIC, INSTANCE }
    public enum ReturnType { ANY, VOID, INT, LONG, BOOLEAN, OBJECT }

    // ── Fields ────────────────────────────────────────────────────────────
    private String  name              = "";
    private String  description       = "";
    private boolean enabled           = true;
    private int     minScore          = 5;

    // Signature criteria
    private Visibility visibility     = Visibility.ANY;
    private Scope      scope          = Scope.ANY;
    private boolean    requireFinal   = false;
    private ReturnType returnType     = ReturnType.ANY;
    private int        paramCount     = -1;
    private List<String> paramTypes   = new ArrayList<>();

    // Bytecode criteria
    private int minInstructionCount   = -1;
    private int maxInstructionCount   = -1;
    private List<String> requiredFieldAccesses  = new ArrayList<>();
    private List<String> requiredMethodCalls    = new ArrayList<>();
    private boolean extractJunkValue  = false;

    // Legacy score-based fields (used by CustomPattern)
    private Boolean mustBeStatic      = null;
    private Boolean mustBeFinal       = null;
    private Boolean mustBePublic      = null;
    private Class<?> scoreReturnType  = null;
    private int exactParamCount       = -1;
    private final List<Class<?>> scoreParamTypes = new ArrayList<>();

    // ── Constructors ────────────────────────────────────────��─────────────
    public PatternDefinition() {}
    public PatternDefinition(String name) { this.name = name; }

    // ── Getters ───────────────────────────────────────────────────────────
    public String     getName()                    { return name;                  }
    public String     getDescription()             { return description;           }
    public boolean    isEnabled()                  { return enabled;               }
    public int        getMinScore()                { return minScore;              }
    public Visibility getVisibility()              { return visibility;            }
    public Scope      getScope()                   { return scope;                 }
    public boolean    isRequireFinal()             { return requireFinal;          }
    public ReturnType getReturnType()              { return returnType;            }
    public int        getParamCount()              { return paramCount;            }
    public List<String> getParamTypes()            { return paramTypes;            }
    public int        getMinInstructionCount()     { return minInstructionCount;   }
    public int        getMaxInstructionCount()     { return maxInstructionCount;   }
    public List<String> getRequiredFieldAccesses() { return requiredFieldAccesses; }
    public List<String> getRequiredMethodCalls()   { return requiredMethodCalls;   }
    public boolean    isExtractJunkValue()         { return extractJunkValue;      }

    // ── Setters ───────────────────────────────────────────────────────────
    public void setName(String v)                         { this.name = v;                  }
    public void setDescription(String v)                  { this.description = v;           }
    public void setEnabled(boolean v)                     { this.enabled = v;               }
    public void setVisibility(Visibility v)               { this.visibility = v;            }
    public void setScope(Scope v)                         { this.scope = v;                 }
    public void setRequireFinal(boolean v)                { this.requireFinal = v;          }
    public void setReturnType(ReturnType v)               { this.returnType = v;            }
    public void setParamCount(int v)                      { this.paramCount = v;            }
    public void setParamTypes(List<String> v)             { this.paramTypes = v;            }
    public void setMinInstructionCount(int v)             { this.minInstructionCount = v;   }
    public void setMaxInstructionCount(int v)             { this.maxInstructionCount = v;   }
    public void setRequiredFieldAccesses(List<String> v)  { this.requiredFieldAccesses = v; }
    public void setRequiredMethodCalls(List<String> v)    { this.requiredMethodCalls = v;   }
    public void setExtractJunkValue(boolean v)            { this.extractJunkValue = v;      }

    // ── Builder-style setters (used by CustomPattern / legacy) ────────────
    public PatternDefinition setMinScore(int s)         { this.minScore = s;           return this; }
    public PatternDefinition setMustBeStatic(boolean v) { this.mustBeStatic = v;       return this; }
    public PatternDefinition setMustBeFinal(boolean v)  { this.mustBeFinal = v;        return this; }
    public PatternDefinition setMustBePublic(boolean v) { this.mustBePublic = v;       return this; }
    public PatternDefinition setScoreReturnType(Class<?> t) { this.scoreReturnType = t; return this; }
    public PatternDefinition setExactParamCount(int n)  { this.exactParamCount = n;    return this; }
    public PatternDefinition addParamType(Class<?> t)   { this.scoreParamTypes.add(t); return this; }

    // ── Scoring (used by CustomPattern) ───────────────────────────────────
    public int score(Class<?> cls, Method method) {
        int score = 0;
        int mods = method.getModifiers();

        if (mustBeStatic != null && (mustBeStatic == Modifier.isStatic(mods)))  score += 2;
        if (mustBeFinal  != null && (mustBeFinal  == Modifier.isFinal(mods)))   score += 2;
        if (mustBePublic != null && (mustBePublic == Modifier.isPublic(mods)))  score += 2;
        if (scoreReturnType != null && method.getReturnType().equals(scoreReturnType)) score += 3;
        if (exactParamCount >= 0 && method.getParameterCount() == exactParamCount)    score += 2;

        if (!scoreParamTypes.isEmpty()) {
            Class<?>[] actual = method.getParameterTypes();
            for (int i = 0; i < scoreParamTypes.size() && i < actual.length; i++)
                if (actual[i].equals(scoreParamTypes.get(i))) score++;
        }

        // Also score from enum-based criteria
        if (visibility == Visibility.PUBLIC && Modifier.isPublic(mods))        score += 1;
        if (scope == Scope.STATIC   && Modifier.isStatic(mods))                score += 1;
        if (scope == Scope.INSTANCE && !Modifier.isStatic(mods))               score += 1;
        if (requireFinal && Modifier.isFinal(mods))                            score += 1;
        if (paramCount >= 0 && method.getParameterCount() == paramCount)       score += 2;

        return score;
    }
}