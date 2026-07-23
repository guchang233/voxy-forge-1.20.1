package me.cortex.voxy.client.mixin.minecraft.session;

import me.cortex.voxy.client.ClientSessionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    // 1.20.1 中 Minecraft 没有 disconnect(Screen,boolean,boolean) (1.20.4+ API),
    // 断开连接用 clearLevel(Screen) (SRG: m_91320_) 或 clearLevel() (SRG: m_91399_)。
    // 同时指定 official 名和 SRG 名,即使 refmap 未加载也能匹配。
    // 注入到两个重载的 HEAD,确保 sessionEnd 被调用。
    // SRG 名不带描述符(注解处理器不支持带描述符的 SRG 名),SRG 名本身唯一标识方法
    @Inject(method = {"clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", "m_91320_"}, at = @At("HEAD"))
    private void voxy$injectWorldClose(Screen screen, CallbackInfo ci) {
        if (ClientSessionEvents.inSession) {
            ClientSessionEvents.sessionEnd();
        }
    }

    @Inject(method = {"clearLevel()V", "m_91399_"}, at = @At("HEAD"))
    private void voxy$injectWorldCloseNoArgs(CallbackInfo ci) {
        if (ClientSessionEvents.inSession) {
            ClientSessionEvents.sessionEnd();
        }
    }
}
