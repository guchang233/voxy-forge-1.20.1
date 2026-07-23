package me.cortex.voxy.client;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.RenderResourceReuse;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.StorageConfigUtil;
import me.cortex.voxy.common.config.ConfigBuildCtx;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.config.section.SectionStorageConfig;
import me.cortex.voxy.commonImpl.ImportManager;
import me.cortex.voxy.commonImpl.VoxyInstance;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/**
 * Voxy 客户端 VoxyInstance 实现。
 *
 * 1.20.1 Forge 移植说明:
 * - 移除了 Sodium 的 SodiumWorldRenderer.instanceNullable() 调用 (Sodium 26.x API 不可用),
 *   改为直接使用配置的 serviceThreads。
 * - 移除了 FlashbackCompat.getReplayStoragePath() (Flashback 集成已禁用)。
 */
public class VoxyClientInstance extends VoxyInstance {
    private final Config config;
    private final Path basePath;
    private final boolean noIngestOverride;

    public VoxyClientInstance() {
        super();
        Path path = getBasePath();
        this.noIngestOverride = false;
        var basePath = this.basePath = path.normalize();
        this.config = StorageConfigUtil.getCreateStorageConfig(Config.class, c -> c.version == 1 && c.sectionStorageConfig != null, () -> DEFAULT_STORAGE_CONFIG, basePath);
        this.updateDedicatedThreads();
    }

    @Override
    protected boolean shouldCreateInstance() {
        // 注意:此方法在父类 VoxyInstance 构造函数 (super()) 中被调用,
        // 此时 this.config 尚未初始化 (为 null)。必须处理 null 情况,
        // 否则会抛 NullPointerException,导致实例创建失败,
        // 进而触发 "Voxy must be enabled in settings" 错误。
        return this.config == null || !this.config.disabled;
    }

    @Override
    public void updateDedicatedThreads() {
        // 原实现会从 Sodium 的 RenderSectionManager 获取已用线程数,这里直接使用配置值
        this.setNumThreads(VoxyConfig.CONFIG.serviceThreads);
    }

    @Override
    protected ImportManager createImportManager() {
        return new ClientImportManager();
    }

    @Override
    protected SectionStorage createStorage(WorldIdentifier identifier) {
        var ctx = new ConfigBuildCtx();
        ctx.setProperty(ConfigBuildCtx.BASE_SAVE_PATH, this.basePath.toString());
        ctx.setProperty(ConfigBuildCtx.WORLD_IDENTIFIER, identifier.getWorldId());
        ctx.setProperty(ConfigBuildCtx.PLAYER_UUID, Minecraft.getInstance().getUser().getProfileId().toString().replace(':', '-'));
        ctx.pushPath(ConfigBuildCtx.DEFAULT_STORAGE_PATH);
        return this.config.sectionStorageConfig.build(ctx);
    }

    public Path getStorageBasePath() {
        return this.basePath;
    }

    @Override
    public boolean isIngestEnabled(WorldIdentifier worldId) {
        return (!this.noIngestOverride) && VoxyConfig.CONFIG.ingestEnabled;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        RenderResourceReuse.clearResources();
    }

    private static class Config {
        public int version = 1;
        public boolean disabled = false;
        public SectionStorageConfig sectionStorageConfig;
    }

    private static final Config DEFAULT_STORAGE_CONFIG;
    static {
        var config = new Config();
        config.sectionStorageConfig = StorageConfigUtil.createDefaultSerializer();
        DEFAULT_STORAGE_CONFIG = config;
    }

    private static Path getBasePath() {
        Path basePath = Minecraft.getInstance().gameDirectory.toPath().resolve(".voxy").resolve("saves");
        var iserver = Minecraft.getInstance().getSingleplayerServer();
        if (iserver != null) {
            basePath = iserver.getWorldPath(LevelResource.ROOT).resolve("voxy");
        } else {
            // 1.20.1 中 Minecraft.getCurrentServer() 直接返回 ServerData
            var info = Minecraft.getInstance().getCurrentServer();
            if (info == null) {
                Logger.error("Server info null");
                basePath = basePath.resolve("UNKNOWN");
            } else {
                // 1.20.1 ServerData 没有 isRealm(),简化处理:用 ip 直接命名
                basePath = basePath.resolve(info.ip.replace(":", "_"));
            }
        }
        return basePath.toAbsolutePath();
    }
}
