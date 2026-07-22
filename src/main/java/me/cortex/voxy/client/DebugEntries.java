package me.cortex.voxy.client;

import me.cortex.voxy.client.core.util.GPUTiming;

import java.util.List;
import java.util.Map;

/**
 * 1.20.1 适配:1.21+ 的 DebugScreenEntries/DebugScreenEntry/DebugScreenDisplayer/
 * DebugScreenEntryStatus/Identifier.fromNamespaceAndPath 在 1.20.1 中均不存在。
 * 1.20.1 的调试屏幕通过 DebugScreenOverlay + RenderGuiOverlayEvent/ClientForgeEvents
 * 实现,API 完全不同。
 *
 * 本移植将 init() 与 onRebuild() 桩化为 no-op,以保留 VoxyClient.onInitializeClient()
 * 调用链不断。若要在 1.20.1 上恢复调试信息显示,需基于 DebugScreenOverlay 重新实现。
 */
public class DebugEntries {
    public static void init() {
        // no-op: 1.21+ DebugScreenEntries API 不可用
    }

    private static boolean previousGpuDebugEnabled = false;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onRebuild(Map allStatuses, List enabled) {
        // no-op: 1.21+ DebugScreenEntryStatus API 不可用,仅保留签名以便其他位置调用不报错
        boolean nowEnabled = !enabled.isEmpty();
        if (nowEnabled != previousGpuDebugEnabled) {
            previousGpuDebugEnabled ^= true;
            GPUTiming.INSTANCE.setEnabled(previousGpuDebugEnabled);
            RenderStatistics.enabled = previousGpuDebugEnabled;
            // 1.20.1 中 Minecraft 没有 levelExtractor,跳过 allChanged() 调用
        }
    }
}
