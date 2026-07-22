package me.cortex.voxy.client.core.util;

import net.minecraftforge.fml.ModList;

/**
 * STUB: 1.20.1 Forge 移植期间的 Iris 兼容层占位实现。
 * 原实现通过 mixin 注入 Iris 26.x 的 IrisRenderingPipeline/CustomUniforms 等内部 API,
 * 1.20.1 上的 Iris (1.6.x) 内部 API 完全不同,且本移植已禁用所有 Iris mixin。
 *
 * TODO: 若需在 1.20.1 上恢复 Iris 兼容,需基于 Iris 1.6.x API 重新实现本类与
 *       client/iris/* (已删除) + client/mixin/iris/* (已删除)。
 */
public class IrisUtil {

    public static final boolean IRIS_INSTALLED = ModList.get() != null && ModList.get().isLoaded("oculus") || (ModList.get() != null && ModList.get().isLoaded("iris"));
    public static final boolean SHADER_SUPPORT = false;

    public static boolean irisShadowActive() {
        return false;
    }

    public static void clearIrisSamplers() {
        // no-op
    }

    public static void reload() {
        // no-op
    }

    public static boolean irisShaderPackEnabled() {
        return false;
    }

    public static boolean irisShadersEnabledInConfig() {
        return false;
    }

    public static void disableIrisShaders() {
        // no-op
    }
}
