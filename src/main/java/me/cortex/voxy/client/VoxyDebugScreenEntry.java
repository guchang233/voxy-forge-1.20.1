package me.cortex.voxy.client;

/**
 * 1.20.1 适配:1.21+ 的 DebugScreenEntry/DebugScreenDisplayer 接口在 1.20.1 中
 * 不存在。原实现通过 DebugScreenEntries.register() 注册自定义调试条目,1.20.1 中
 * 调试屏幕由 DebugScreenOverlay 管理,API 完全不同。
 *
 * 本桩保留为空类以维持 VoxyClient.onInitializeClient() 调用链不断。
 */
public class VoxyDebugScreenEntry {
    // no-op
}
