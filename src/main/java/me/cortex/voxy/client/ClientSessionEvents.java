package me.cortex.voxy.client;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ClientSessionEvents {
    public static boolean inSession = false;

    public static void sessionStart() {
        if (inSession) throw new IllegalStateException("Cannot start new session while in a session");
        inSession = true;

        //Should never try creating multiple instances via session start
        if (VoxyCommon.getInstance() != null) throw new IllegalStateException();

        if (VoxyCommon.isAvailable()) {
            if (VoxyConfig.CONFIG.enabled) {
                // 异步化: 把 createInstance + createRenderer (RocksDB native 库加载 + RocksDB.open +
                // Mapper 全表扫描 + 创建工作线程) 的重活放到 Voxy-Init 线程, 主线程立即返回,
                // 避免堆积在客户端主线程导致"读取 0% 卡死"。
                Thread initThread = new Thread(ClientSessionEvents::runAsyncInit, "Voxy-Init");
                initThread.setDaemon(false);
                initThread.setPriority(Thread.NORM_PRIORITY);
                initThread.start();
            }
        }
    }

    private static void runAsyncInit() {
        // 边缘情况: sessionEnd 可能在 Voxy-Init 线程仍在运行时被调用。
        // 创建 instance 前检查 inSession 是否仍为 true (若已 end 则跳过)。
        if (!inSession) {
            Logger.warn("Voxy-Init thread started but session already ended, skipping init");
            return;
        }
        try {
            VoxyCommon.createInstance();
            if (!inSession) {
                Logger.warn("Session ended during createInstance, skipping renderer creation");
                return;
            }
            // 创建实例后尝试创建渲染器。
            // LevelRenderer.setLevel 在 handleLogin 内部早于本方法调用,
            // 此时 identifier 已由 voxy$setWorld 设置,可以创建渲染器。
            // 如果 instance 仍未创建 (createInstance 失败),voxy$createRenderer 会安全跳过。
            if (VoxyCommon.getInstance() != null) {
                var holder = IVoxyRenderSystemHolder.getNullableHolder();
                if (holder != null) {
                    holder.voxy$createRenderer();
                }
            }
            Minecraft.getInstance().execute(() -> {
                var inst = Minecraft.getInstance();
                if (inst.player != null) {
                    inst.gui.getChat().addMessage(
                            Component.literal("Voxy 初始化完成").withStyle(s -> s.withColor(0x55FF55)));
                }
            });
        } catch (Throwable t) {
            Logger.error("Voxy async init failed", t);
            // 提取根因
            Throwable root = t;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            String rootCause = root.getClass().getName() + ": " + root.getMessage();
            Minecraft.getInstance().execute(() -> {
                var inst = Minecraft.getInstance();
                if (inst.player != null) {
                    inst.gui.getChat().addMessage(
                            Component.literal("Voxy 初始化失败: " + rootCause).withStyle(s -> s.withColor(0xFF5555)));
                }
            });
        }
    }

    public static void sessionEnd() {
        if (!inSession) throw new IllegalStateException("Cannot end a session while not in a session");
        inSession = false;

        VoxyCommon.shutdownInstance();
    }
}
