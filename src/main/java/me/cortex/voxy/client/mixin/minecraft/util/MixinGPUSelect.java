package me.cortex.voxy.client.mixin.minecraft.util;

import me.cortex.voxy.client.GPUSelectorWindows2;
import me.cortex.voxy.common.util.ThreadUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinGPUSelect {
    // 1.20.1: Minecraft.<init> 不调用 Options.save() (1.21+ 才有),改用 RETURN 注入。
    // GPU 选择在构造函数末尾执行,窗口可能已创建,GPU 选择功能受限但不影响核心逻辑。
    @Inject(method = "<init>", at = @At("RETURN"))
    private void voxy$injectInitWindow(GameConfig gc, CallbackInfo ci) {
        //System.load("C:\\Program Files\\RenderDoc\\renderdoc.dll");
        var prop = System.getProperty("voxy.forceGpuSelectionIndex", "NO");
        if (!prop.equals("NO")) {
            GPUSelectorWindows2.doSelector(Integer.parseInt(prop));
        }

        //Force the current thread priority to be realtime
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        ThreadUtils.SetSelfThreadPriorityWin32(ThreadUtils.WIN32_THREAD_PRIORITY_TIME_CRITICAL);
    }
}
