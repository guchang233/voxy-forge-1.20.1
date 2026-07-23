package me.cortex.voxy.client;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.minecraft.client.Minecraft;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.channels.NonWritableChannelException;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Voxy 客户端引导逻辑。在 Fabric 版本实现 ClientModInitializer,在 Forge 1.20.1 中
 * 由 VoxyMod (主 @Mod 类) 在客户端 setup 事件中调用。
 *
 * STUB: 渲染层相关的 Capabilities / SharedIndexBuffer 初始化已移除,因为它们依赖
 * 已删除的 client/core/gl/* 与 client/core/rendering/* (1.21.5+ API)。
 */
public class VoxyClient {
    private static final HashSet<String> FREX = new HashSet<>();
    private static FileLock EXCLUSIVE_LOCK;

    public static void initVoxyClient() {
        // Capabilities / 系统能力检测已 stub,假定系统支持
        boolean systemSupported = true;

        if (systemSupported && System.getProperty("voxy.exclusiveLock", "false").equalsIgnoreCase("true")) {
            var vf = Minecraft.getInstance().gameDirectory.toPath().resolve(".voxy");
            if (!vf.toFile().isDirectory()) {
                vf.toFile().mkdir();
            }
            try {
                FileOutputStream fis = new FileOutputStream(vf.resolve("voxy.lock").toFile());
                EXCLUSIVE_LOCK = fis.getChannel().lock(0, Long.MAX_VALUE, false);
            } catch (NonWritableChannelException | IOException e) {
                Logger.error("Failed to acquire exclusive voxy lock file, mod will be disabled");
                systemSupported = false;
            }
        }

        if (systemSupported) {
            // SharedIndexBuffer.INSTANCE.id() 已 stub,跳过
            VoxyCommon.setInstanceFactory(VoxyClientInstance::new);
            // factory 注册后重新加载配置,确保 VoxyConfig 从磁盘加载/创建
            // (静态初始化可能在 setInstanceFactory 之前执行,导致配置未写入磁盘)
            VoxyConfig.reload();
        }
    }

    /**
     * 由 VoxyMod 在客户端 setup 事件中调用。负责注册命令等客户端侧初始化。
     * 命令注册在 Forge 中通过 RegisterClientCommandsEvent 完成,本方法仅做兼容入口。
     */
    public static void onInitializeClient() {
        DebugEntries.init();
        // FREX 入口点在 Forge 中没有直接对应,保留为空集合即可
        // VoxyCommands 的注册由 VoxyMod 通过 RegisterClientCommandsEvent 事件触发
    }

    public static boolean isFrexActive() {
        return !FREX.isEmpty();
    }

    public static int getOcclusionDebugState() {
        return 0;
    }

    public static boolean disableSodiumChunkRender() {
        return false;
    }
}
