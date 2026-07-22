package me.cortex.voxy.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Voxy Forge 1.20.1 主模组入口。
 *
 * - @Mod value 必须与 mods.toml 中的 modId 一致 ("voxy")
 * - 客户端逻辑通过 FMLClientSetupEvent 触发
 * - 命令通过 RegisterClientCommandsEvent 注册
 */
@Mod(VoxyMod.MODID)
public class VoxyMod {
    public static final String MODID = "voxy";

    public VoxyMod() {
        // 通用 (客户端+服务端) setup
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        // 仅客户端 setup
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        // 客户端命令注册
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // VoxyCommon 的静态块已经处理了 ModContainer 读取与 Serialization.init
        // 这里不需要额外动作
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            VoxyClient.initVoxyClient();
            VoxyClient.onInitializeClient();
        });
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (VoxyCommon.isAvailable()) {
            LiteralArgumentBuilder<CommandSourceStack> cmd = VoxyCommands.register();
            event.getDispatcher().register(cmd);
        }
    }
}
