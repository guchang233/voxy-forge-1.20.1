package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.ICheekyClientChunkCache;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkCache.class)
public class MixinClientChunkCache implements ICheekyClientChunkCache {
    @Unique
    private static final boolean BOBBY_INSTALLED = ModList.get().isLoaded("bobby");

    @Shadow
    private volatile ClientChunkCache.Storage storage;

    @Override
    public @Nullable LevelChunk voxy$cheekyGetChunk(int x, int z) {
        //This doesnt do the in range check stuff, it just gets the chunk at all costs
        var chunk = this.storage.getChunk(this.storage.getIndex(x, z));
        if (chunk == null) {
            return null;
        }
        //Verify that the position of the chunk is the same as the requested position
        //1.20.1 中 ChunkPos.x / ChunkPos.z 是字段 (public final int),不是方法
        if (chunk.getPos().x == x && chunk.getPos().z == z) {
            return chunk;//The chunk is at the requested position
        }
        //Otherwise return null
        return null;
    }

    // 1.20.1: ClientChunkCache.drop(int x, int z) 不是 drop(ChunkPos)
    // SRG: m_104455_
    @Inject(method = {"drop", "m_104455_"}, at = @At("HEAD"))
    public void voxy$captureChunkBeforeUnload(int x, int z, CallbackInfo ci) {
        if (VoxyConfig.CONFIG.ingestEnabled && BOBBY_INSTALLED) {
            var chunk = this.voxy$cheekyGetChunk(x, z);
            if (chunk != null) {
                VoxelIngestService.tryAutoIngestChunk(chunk);
            }
        }
    }
}
