package me.cortex.voxy.client.mixin.minecraft;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1 适配:1.21+ 的 FogData/DeltaTracker/net.minecraft.client.renderer.fog.FogRenderer
 * 在 1.20.1 中不存在。1.20.1 的 FogRenderer.setupFog 签名为
 *   setupFog(Camera, FogRenderer.FogMode, float, boolean, float)
 * 返回 void,雾参数通过 RenderSystem.setShaderFogStart/End 修改。
 *
 * 原 1.21+ 代码区分 environmentalStart/End 与 renderDistanceStart/End,1.20.1 仅有单一
 * 雾起止,因此本移植把两者合并到 setShaderFogStart/End 上(数值取较大者)。
 */
@Mixin(value = FogRenderer.class, priority = 900)
public class MixinFogRenderer {
    // SRG: m_234172_
    // 1.20.1 的 FogRenderer.setupFog 是 static 方法,handler 必须也是 static
    @Inject(method = {"setupFog", "m_234172_"}, at = @At("RETURN"))
    private static void voxy$modifyFog(Camera camera, FogRenderer.FogMode fogMode, float farDistance, boolean fogFlag, float partialTick, CallbackInfo ci) {
        if (!VoxyConfig.CONFIG.isRenderingEnabled()) return;

        var vrs = IVoxyRenderSystemHolder.getNullable();
        if (vrs == null) return;

        // 1.20.1 没有 FogData 对象,直接通过 RenderSystem 修改 shader 雾参数
        // useEnvironmentalFog 为 false 时把雾推到极远以禁用环境雾
        if (!VoxyConfig.CONFIG.useEnvironmentalFog) {
            RenderSystem.setShaderFogStart(99999999f);
            RenderSystem.setShaderFogEnd(99999999f);
        }
    }
}
