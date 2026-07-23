package me.cortex.voxy.client.mixin.minecraft.session;

import me.cortex.voxy.client.ClientSessionEvents;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {
    // 1.20.1 的 ClientboundLoginPacket 没有 commonPlayerSpawnInfo() (1.20.2+ API),
    // 用 TAIL 注入到 handleLogin 末尾,确保登录处理完成后再创建 Voxy 实例
    // SRG: m_5998_
    @Inject(method = {"handleLogin", "m_5998_"}, at = @At("TAIL"))
    private void voxy$init(ClientboundLoginPacket packet, CallbackInfo ci) {
        if (!ClientSessionEvents.inSession) {
            ClientSessionEvents.sessionStart();
        }
    }
}
