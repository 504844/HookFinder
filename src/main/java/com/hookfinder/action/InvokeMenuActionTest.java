package com.hookfinder.action;

import java.lang.reflect.Method;
import java.util.Arrays;

public class InvokeMenuActionTest {

    private static final String CLASS_NAME  = "osrs.ChatIcon";
    private static final String METHOD_NAME = "method9367";
    private static final byte   JUNK_VALUE  = (byte) 500; // = -12

    private static volatile Method cachedMethod = null;
    private static volatile boolean searched    = false;

    // ==================== Resolution ====================

    public static Method findMethod() {
        if (searched) return cachedMethod;
        searched = true;

        try {
            Class<?> cls = findClass(CLASS_NAME);
            if (cls == null) {
                System.err.println("[InvokeTest] Class not found: " + CLASS_NAME);
                return null;
            }

            Method found = Arrays.stream(cls.getDeclaredMethods())
                    .filter(m -> m.getName().equals(METHOD_NAME))
                    .findAny()
                    .orElse(null);

            if (found != null) {
                found.setAccessible(true);
                cachedMethod = found;
                System.out.println("[InvokeTest] Resolved: "
                        + found.getDeclaringClass().getName() + "." + found.getName());
                printParamTypes(found);
            } else {
                System.err.println("[InvokeTest] Method not found: " + METHOD_NAME);
            }

        } catch (Exception e) {
            System.err.println("[InvokeTest] Error: " + e.getMessage());
            e.printStackTrace();
        }

        return cachedMethod;
    }

    private static void printParamTypes(Method m) {
        System.out.println("[InvokeTest] Param count: " + m.getParameterCount());
        Class<?>[] p = m.getParameterTypes();
        for (int i = 0; i < p.length; i++) {
            System.out.println("  [" + i + "] " + p[i].getName());
        }
        System.out.println("[InvokeTest] Junk = (byte)500 = " + JUNK_VALUE);
    }

    private static Class<?> findClass(String name) {
        try { return Class.forName(name); }
        catch (ClassNotFoundException ignored) {}
        try {
            ClassLoader ctx = Thread.currentThread().getContextClassLoader();
            if (ctx != null) return Class.forName(name, true, ctx);
        } catch (ClassNotFoundException ignored) {}
        ClassLoader cl = InvokeMenuActionTest.class.getClassLoader();
        while (cl != null) {
            try { return cl.loadClass(name); }
            catch (ClassNotFoundException ignored) {}
            cl = cl.getParent();
        }
        return null;
    }

    // ==================== Core invoke ====================

    /**
     * Actual signature:
     *   method9367(int p0, int p1, int opcode, int identifier, int itemId,
     *              int extra, String option, String target, int x, int y, byte junk)
     *
     * For WALK:
     *   p0         = scene tile X  (NOT world X, NOT mouse X)
     *   p1         = scene tile Y  (NOT world Y, NOT mouse Y)
     *   opcode     = 1007
     *   identifier = 0
     *   itemId     = 0
     *   extra      = 0
     *   option     = "Walk here"
     *   target     = ""
     *   x          = mouse canvas X (can be 0, game ignores for walk)
     *   y          = mouse canvas Y (can be 0, game ignores for walk)
     *   junk       = (byte)500 = -12
     */
    public static boolean invoke(int p0, int p1, int opcode,
                                 int identifier, int itemId, int extra,
                                 String option, String target,
                                 int x, int y) {
        Method m = findMethod();
        if (m == null) return false;

        System.out.println("[InvokeTest] Calling: p0=" + p0 + " p1=" + p1
                + " opcode=" + opcode + " id=" + identifier
                + " itemId=" + itemId + " extra=" + extra
                + " opt=\"" + option + "\" tgt=\"" + target + "\""
                + " x=" + x + " y=" + y + " junk=" + JUNK_VALUE);

        try {
            m.invoke(null,
                    p0, p1, opcode, identifier, itemId,
                    extra,      // [5] the extra int we were missing before
                    option, target,
                    x, y,
                    JUNK_VALUE  // (byte)500 = -12
            );
            System.out.println("[InvokeTest] Invoked successfully!");
            return true;
        } catch (Exception e) {
            System.err.println("[InvokeTest] Invoke failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ==================== Convenience ====================

    /**
     * Walk to a scene tile.
     * Get real scene coords by standing somewhere and reading:
     *   client.getLocalPlayer().getWorldLocation() → convert to scene:
     *   sceneX = worldX - client.getBaseX()
     *   sceneY = worldY - client.getBaseY()
     */
    public static boolean walkHere(int sceneTileX, int sceneTileY) {
        return invoke(sceneTileX, sceneTileY, 1007, 0, 0, 0,
                "Walk here", "", 0, 0);
    }

    /**
     * Attack NPC.
     * npcIndex = NPC.getIndex() from the NPC object
     */
    public static boolean attackNpc(int npcIndex, int canvasX, int canvasY) {
        return invoke(0, npcIndex, 9, npcIndex, 0, 0,
                "Attack", "", canvasX, canvasY);
    }

    /**
     * Click inventory item.
     * slot   = inventory slot 0-27
     * itemId = the item's ID
     * widgetId = packed widget ID for inventory = 9764864
     */
    public static boolean clickInventoryItem(int slot, int itemId) {
        return invoke(slot, itemId, 25, 9764864, itemId, 0,
                "Use", "", 0, 0);
    }

    // ==================== Info ====================

    public static String getMethodInfo() {
        Method m = findMethod();
        if (m == null) return "NOT FOUND — " + CLASS_NAME + "." + METHOD_NAME;

        StringBuilder sb = new StringBuilder();
        sb.append("✓ RESOLVED\n\n");
        sb.append("Class:  ").append(m.getDeclaringClass().getName()).append("\n");
        sb.append("Method: ").append(m.getName()).append("\n");
        sb.append("Params: ").append(m.getParameterCount()).append("\n");
        sb.append("Junk:   (byte)500 = ").append(JUNK_VALUE).append("\n\n");

        String[] names = {
                "p0         (scene tile X / slot)",
                "p1         (scene tile Y / item ID)",
                "opcode     (MenuAction type)",
                "identifier (entity/widget ID)",
                "itemId     (item ID, 0 if none)",
                "extra      (always 0)",
                "option     (\"Walk here\", \"Attack\"...)",
                "target     (\"\" or entity name)",
                "x          (canvas X, 0 ok for walk)",
                "y          (canvas Y, 0 ok for walk)",
                "junk       = (byte)500 = " + JUNK_VALUE
        };

        Class<?>[] params = m.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            sb.append(String.format("  [%2d] %-8s  %s\n",
                    i, params[i].getSimpleName(),
                    i < names.length ? names[i] : "?"));
        }
        return sb.toString();
    }

    public static void reset() {
        cachedMethod = null;
        searched = false;
    }
}