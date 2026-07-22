package me.cortex.voxy.client;

import net.minecraft.world.level.chunk.LevelChunk;

import javax.annotation.Nullable;

public interface ICheekyClientChunkCache {
    @Nullable
    LevelChunk voxy$cheekyGetChunk(int x, int z);
}
