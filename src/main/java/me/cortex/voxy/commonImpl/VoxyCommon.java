package me.cortex.voxy.commonImpl;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.config.Serialization;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.Optional;

/**
 * Voxy 通用入口。在 Fabric 版本是 ModInitializer,在 Forge 1.20.1 中由 VoxyClientMod
 * (客户端 @Mod 类) 与 VoxyCommonMod (服务端 @Mod 类) 间接驱动。
 *
 * 这里的静态初始化块保留原本从 ModContainer 读取版本元数据的逻辑,改为从 Forge 的
 * ModList 读取。
 */
public class VoxyCommon {
    public static final String MOD_VERSION;
    public static final boolean IS_DEDICATED_SERVER;
    public static final boolean IS_IN_MINECRAFT;

    static {
        Optional<? extends IModInfo> modInfo = ModList.get() == null ? Optional.empty() :
                ModList.get().getModContainerById("voxy").map(c -> c.getModInfo());
        if (modInfo.isEmpty()) {
            IS_IN_MINECRAFT = false;
            Logger.error("Running voxy without minecraft");
            MOD_VERSION = "<UNKNOWN>";
            IS_DEDICATED_SERVER = false;
        } else {
            IS_IN_MINECRAFT = true;
            var version = modInfo.get().getVersion().toString();
            // commit 自定义元数据在 Forge 中不直接可用,留空
            MOD_VERSION = version;
            IS_DEDICATED_SERVER = FMLEnvironment.dist == Dist.DEDICATED_SERVER;
            Serialization.init();
        }
    }

    public static boolean isVerificationFlagOn(String name) {
        return isVerificationFlagOn(name, false);
    }

    public static boolean isVerificationFlagOn(String name, boolean defaultOn) {
        return System.getProperty("voxy." + name, defaultOn ? "true" : "false").equals("true");
    }

    public static void breakpoint() {
        int breakpoint = 0;
    }

    public interface IInstanceFactory { VoxyInstance create(); }
    private static VoxyInstance INSTANCE;
    private static IInstanceFactory FACTORY = null;

    public static void setInstanceFactory(IInstanceFactory factory) {
        if (FACTORY != null) {
            throw new IllegalStateException("Cannot set instance factory more than once");
        }
        FACTORY = factory;
    }

    public static VoxyInstance getInstance() {
        return INSTANCE;
    }

    public static void shutdownInstance() {
        if (INSTANCE != null) {
            var instance = INSTANCE;
            INSTANCE = null;
            instance.shutdown();
        }
    }

    public static void createInstance() {
        if (FACTORY == null) {
            Logger.warn("Not creating instance, factory is null (VoxyCommon not initialized)");
            return;
        }
        if (INSTANCE != null) {
            throw new IllegalStateException("Cannot create multiple instances");
        }
        long __start = System.nanoTime();
        try {
            INSTANCE = FACTORY.create();
            Logger.info("createInstance took " + ((System.nanoTime() - __start) / 1_000_000) + "ms");
            Logger.info("Voxy instance created successfully");
        } catch (DontCreateInstance e) {
            Logger.info("createInstance took " + ((System.nanoTime() - __start) / 1_000_000) + "ms");
            Logger.info("Not creating instance due to DontCreateInstance");
        } catch (Throwable e) {
            Logger.info("createInstance took " + ((System.nanoTime() - __start) / 1_000_000) + "ms");
            // 捕获所有异常 (包括 Error,如 NoClassDefFoundError/ExceptionInInitializerError),
            // 防止异常传播导致游戏崩溃或 mixin 注入失败。
            // 记录错误日志,INSTANCE 保持 null,用户可通过 /voxy reload 重试。
            Logger.error("Failed to create voxy instance", e);
        }
    }

    public static boolean isAvailable() {
        return FACTORY != null;
    }

    public static final boolean IS_MINE_IN_ABYSS = false;
}
