package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.VoxyClientInstance;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer implements IVoxyRenderSystemHolder {
    @Unique @Nullable private WorldIdentifier identifier;
    @Unique private @Nullable VoxyRenderSystem renderer;

    @Override
    public VoxyRenderSystem voxy$getRenderSystem() {
        return this.renderer;
    }

    // close 在 1.20.1 LevelRenderer 中 SRG 名也是 close (AutoCloseable),但部分构建是 m_85543_
    @Inject(method = {"close", "m_85543_"}, at = @At("HEAD"))
    private void voxy$injectClose(CallbackInfo ci) {
        this.voxy$shutdownRenderer();
    }

    // 1.20.1: LevelRenderer.setLevel(ClientLevel) 在切换/进入世界时调用。
    // 在这里设置 identifier,并尝试创建渲染器。
    // 注意:首次进入世界时,setLevel 在 handleLogin 内部调用,早于 sessionStart,
    // 此时 instance 可能为 null,voxy$createRenderer 会安全跳过。
    // 之后 sessionStart 创建 instance 后会再次调用 voxy$createRenderer。
    // SRG: m_109701_
    @Inject(method = {"setLevel", "m_109701_"}, at = @At("TAIL"))
    private void voxy$injectSetLevel(ClientLevel level, CallbackInfo ci) {
        this.voxy$setWorld(level);
    }

    @Override
    public void voxy$shutdownRenderer() {
        if (this.renderer != null) {
            this.renderer.shutdown();
            this.renderer = null;
        }
    }

    /*
    @Override
    public void voxy$reloadRenderer() {
        this.voxy$shutdownRenderer();
        this.voxy$createRenderer();
    }*/

    @Override
    public void voxy$setWorld(Level level) {
        WorldIdentifier identifier = level==null?null:WorldIdentifier.of(level);
        if (Objects.equals(this.identifier, identifier)) return;
        this.voxy$shutdownRenderer();
        this.identifier = identifier;
        // 设置 identifier 后尝试创建渲染器。
        // 首次进入世界时,sessionStart 可能尚未执行 (instance 为 null),会安全跳过;
        // sessionStart 创建 instance 后会再次调用 voxy$createRenderer。
        // 若 sessionStart 已先执行过 (identifier 当时为 null 跳过),此处补充创建。
        if (this.identifier != null) {
            this.voxy$createRenderer();
        }
    }

    @Override
    public void voxy$createRenderer() {
        if (this.renderer != null) throw new IllegalStateException("Cannot have multiple renderers");
        if (!VoxyConfig.CONFIG.enabled) {
            Logger.info("Not creating renderer due to disabled");
            return;
        }
        if (!VoxyConfig.CONFIG.isRenderingEnabled()) {
            Logger.info("Not creating renderer due to disabled rendering");
            return;
        }
        if (this.identifier == null) {
            Logger.info("Not creating renderer due to null identifier");
            return;
        }
        var instance = (VoxyClientInstance)VoxyCommon.getInstance();
        if (instance == null) {
            //This is now legal (e.g. when the instance is disabled)
            Logger.info("Not creating renderer due to null instance");
            return;
        }
        String __thread = Thread.currentThread().getName();
        long __engineStart = System.nanoTime();
        WorldEngine world = this.identifier.getOrCreateEngine(true);
        Logger.info("getOrCreateEngine took " + ((System.nanoTime() - __engineStart) / 1_000_000) + "ms on thread " + __thread);
        if (world == null) {
            Logger.warn("Not creating renderer due to null engine");
            return;
        }
        long __start = System.nanoTime();
        this.voxy$createEngineDirect(world);
        Logger.info("voxy$createRenderer took " + ((System.nanoTime() - __start) / 1_000_000) + "ms on thread " + __thread);
    }

    @Unique
    private void voxy$createEngineDirect(WorldEngine world) {
        var instance = world.instanceIn;
        if (instance == null) throw new IllegalStateException();//in theory this could be null if is like in a test suit or something
        String __thread = Thread.currentThread().getName();
        try {
            long __renderStart = System.nanoTime();
            this.renderer = new VoxyRenderSystem(world, instance.getServiceManager());
            Logger.info("new VoxyRenderSystem took " + ((System.nanoTime() - __renderStart) / 1_000_000) + "ms on thread " + __thread);
        } catch (RuntimeException e) {
            if (IrisUtil.irisShaderPackEnabled()) {
                IrisUtil.disableIrisShaders();
            } else {
                throw e;
            }
        }
        instance.updateDedicatedThreads();
    }
}
