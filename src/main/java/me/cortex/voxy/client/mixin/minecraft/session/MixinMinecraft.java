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
    // 断开连接用 clearLevel(Screen) 或 clearLevel()。
    // 注入到两个重载的 HEAD,确保 sessionEnd 被调用。
    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
    private void voxy$injectWorldClose(Screen screen, CallbackInfo ci) {
        if (ClientSessionEvents.inSession) {
            ClientSessionEvents.sessionEnd();
        }
    }

    @Inject(method = "clearLevel()V", at = @At("HEAD"))
    private void voxy$injectWorldCloseNoArgs(CallbackInfo ci) {
        if (ClientSessionEvents.inSession) {
            ClientSessionEvents.sessionEnd();
        }
    }
}
