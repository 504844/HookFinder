package com.hookfinder;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

public class ViewportWalkingPattern implements HookPattern {

    @Override
    public String name() { return "ViewportWalking"; }

    @Override
    public List<HookResult> find(List<Class<?>> classes) {
        for (Class<?> cls : classes) {
            Method[] methods;
            try { methods = cls.getDeclaredMethods(); }
            catch (Throwable t) { continue; }

            for (Method method : methods) {
                if (method.getModifiers() != (Modifier.PUBLIC | Modifier.STATIC)) continue;
                if (!method.getReturnType().equals(void.class)) continue;
                if (method.getParameterCount() != 5) continue;

                Class<?>[] p = method.getParameterTypes();
                if (p[1] == int.class && p[2] == int.class
                        && p[3] == int.class && p[4] == int.class) {
                    System.out.println("FOUND -> " + cls.getName() + "." + method.getName());
                    HookFinder.emit(new HookResult("setViewportWalkingFieldHook",
                            cls.getName(), method.getName(), ""));
                }
            }
        }
        return null;
    }
}