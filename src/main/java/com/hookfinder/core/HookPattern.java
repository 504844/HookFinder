package com.hookfinder.core;

import java.util.List;

public interface HookPattern {
    String name();
    List<HookResult> find(List<Class<?>> classes);
}