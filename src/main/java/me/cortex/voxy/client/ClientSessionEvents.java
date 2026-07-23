package me.cortex.voxy.client;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.commonImpl.VoxyCommon;

public class ClientSessionEvents {
    public static boolean inSession = false;

    public static void sessionStart() {
        if (inSession) throw new IllegalStateException("Cannot start new session while in a session");
        inSession = true;

        //Should never try creating multiple instances via session start
        if (VoxyCommon.getInstance() != null) throw new IllegalStateException();

        if (VoxyCommon.isAvailable()) {
            if (VoxyConfig.CONFIG.enabled) {
                VoxyCommon.createInstance();
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
            }
        }
    }

    public static void sessionEnd() {
        if (!inSession) throw new IllegalStateException("Cannot end a session while not in a session");
        inSession = false;

        VoxyCommon.shutdownInstance();
    }
}
