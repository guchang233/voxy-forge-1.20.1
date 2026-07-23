package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1 适配:
 * - 1.21+ 的 ClientLevel 构造器有 LevelExtractor 参数,1.20.1 没有,故移除构造器注入,
 *   bottomSectionY 改为按需计算。
 * - 1.21+ 的 net.minecraft.world.level.chunk.status.ChunkStatus 在 1.20.1 中为
 *   net.minecraft.world.level.chunk.ChunkStatus。
 */
@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {

    @Shadow public abstract ClientChunkCache getChunkSource();

    // SRG: m_6550_
    @Inject(method = {"setBlocksDirty", "m_6550_"}, at = @At("TAIL"))
    private void voxy$injectIngestOnStateChange(BlockPos pos, BlockState old, BlockState updated, CallbackInfo cir) {
        if (old == updated) return;

        if (!updated.isAir()) return;
        if (VoxyCommon.getInstance()==null) return;
        if (!VoxyConfig.CONFIG.ingestEnabled) return;

        var self = (Level)(Object)this;
        var wi = WorldIdentifier.of(self);
        if (wi == null) {
            return;
        }

        int x = pos.getX()&15;
        int y = pos.getY()&15;
        int z = pos.getZ()&15;
        if (x == 0 || x==15 || y==0 || y==15 || z==0||z==15) {
            var csp = SectionPos.of(pos);
            var chunk = self.getChunk(pos.getX()>>4, pos.getZ()>>4, ChunkStatus.FULL, false);
            if (chunk != null) {
                // 1.20.1 中 Level.getMinY() 不存在,用 getMinSection() 替代 (返回 minSectionY)
                int bottomSectionY = self.getMinSection();
                var section = chunk.getSection(csp.y() - bottomSectionY);
                var lp = self.getLightEngine();

                var blp = lp.getLayerListener(LightLayer.BLOCK).getDataLayerData(csp);
                var slp = lp.getLayerListener(LightLayer.SKY).getDataLayerData(csp);

                VoxelIngestService.rawIngest(wi, section, csp.x(), csp.y(), csp.z(), blp == null ? null : blp.copy(), slp == null ? null : slp.copy());
            }
        }
    }
}
