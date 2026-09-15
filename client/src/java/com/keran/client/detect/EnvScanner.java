package com.keran.client.detect;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

/**
 * 运行环境与注入检测：
 * 1) JVM 启动参数(检测 -javaagent / 可疑引导参数)
 * 2) OS / Java / 架构 / 权限 / 内存 (虚拟化与沙盒环境特征)
 * 3) 进程用户名
 */
public final class EnvScanner {

    private EnvScanner() {
    }

    /** 检测 JVM 参数中的注入痕迹 */
    public static List<String> detectJvmAgents() {
        List<String> findings = new java.util.ArrayList<>();
        try {
            RuntimeMXBean mx = ManagementFactory.getRuntimeMXBean();
            for (String arg : mx.getInputArguments()) {
                String low = arg.toLowerCase(java.util.Locale.ROOT);
                if (low.contains("-javaagent") || low.contains("-agentlib")
                        || low.contains("-agentpath")) {
                    findings.add(arg);
                }
            }
        } catch (Throwable ignored) {
        }
        return findings;
    }

    /** 可疑 JVM 参数(非 agent, 但常见于注入/破解环境) */
    public static List<String> detectSuspiciousJvmArgs() {
        List<String> findings = new java.util.ArrayList<>();
        try {
            RuntimeMXBean mx = ManagementFactory.getRuntimeMXBean();
            for (String arg : mx.getInputArguments()) {
                String low = arg.toLowerCase(java.util.Locale.ROOT);
                if (low.contains("-xbootclasspath") || low.contains("-noverify")
                        || low.contains("-xx:+unlockdiagnosticvmoptions")) {
                    findings.add(arg);
                }
            }
        } catch (Throwable ignored) {
        }
        return findings;
    }

    public static JsonObject envJson() {
        JsonObject o = new JsonObject();
        o.addProperty("os_name", safeProp("os.name"));
        o.addProperty("os_arch", safeProp("os.arch"));
        o.addProperty("os_version", safeProp("os.version"));
        o.addProperty("java_version", safeProp("java.version"));
        o.addProperty("java_vendor", safeProp("java.vendor"));
        o.addProperty("user_name", safeProp("user.name"));
        o.addProperty("user_home", safeProp("user.home"));
        o.addProperty("max_memory_mb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        o.addProperty("available_processors", Runtime.getRuntime().availableProcessors());
        o.addProperty("is_admin", isAdmin());
        return o;
    }

    /** 是否以管理员/root 运行 */
    public static boolean isAdmin() {
        String user = safeProp("user.name");
        if (user == null) {
            return false;
        }
        return user.equalsIgnoreCase("root") || user.equalsIgnoreCase("administrator");
    }

    private static String safeProp(String key) {
        try {
            return System.getProperty(key, "");
        } catch (Throwable t) {
            return "";
        }
    }

    /** JVM 参数数组(原始) */
    public static JsonArray jvmArgsJson() {
        JsonArray arr = new JsonArray();
        try {
            RuntimeMXBean mx = ManagementFactory.getRuntimeMXBean();
            for (String arg : mx.getInputArguments()) {
                arr.add(arg);
            }
        } catch (Throwable ignored) {
        }
        return arr;
    }
}
