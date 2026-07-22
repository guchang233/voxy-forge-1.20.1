package me.cortex.voxy.client.core;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.world.WorldEngine;

/**
 * STUB: 1.20.1 Forge 移植期间的渲染系统占位实现。
 * 原实现深度依赖 1.21.5+ 的 com.mojang.blaze3d.opengl.* API (GlStateManager/GlConst/
 * GlCommandEncoder/GlRenderPass/RenderPipeline 等) 与 Sodium 26.x 的内部 API,
 * 这些在 1.20.1 中均不存在,因此渲染层需重新设计。
 *
 * TODO: 移植渲染层时,需基于 1.20.1 的 RenderSystem/RenderType 或直接 LWJGL 调用
 *       重新实现本类。原实现可见于 git 历史 (commit 在移植前)。
 */
public class VoxyRenderSystem {
    private final WorldEngine worldIn;

    public VoxyRenderSystem(WorldEngine world, ServiceManager sm) {
        this.worldIn = world;
        Logger.info("VoxyRenderSystem (stub) created for world: " + world);
    }

    public void shutdown() {
        Logger.info("VoxyRenderSystem (stub) shutdown");
    }

    public WorldEngine getWorld() {
        return this.worldIn;
    }
}
