package me.cortex.voxy.client;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.common.DebugUtils;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import me.cortex.voxy.commonImpl.importers.DHImporter;
import me.cortex.voxy.commonImpl.importers.WorldImporter;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Voxy 客户端命令 (/voxy reload / import / debug ...)。
 *
 * 在 Fabric 版本使用 ClientCommands API + FabricClientCommandSource,
 * 在 Forge 1.20.1 中 RegisterClientCommandsEvent.getDispatcher() 返回
 * CommandDispatcher<CommandSourceStack>,因此用 CommandSourceStack。
 *
 * 注意: Brigadier 的 literal()/argument() 静态方法是泛型 <S>,在 var 链中
 * 类型推断会回退到 <Object>,导致方法引用 (CommandContext<CommandSourceStack>)
 * 类型不匹配。因此本类不用 static import,改为显式带类型参数调用
 * LiteralArgumentBuilder.<CommandSourceStack>literal(...)
 * RequiredArgumentBuilder.<CommandSourceStack>argument(...)
 */
public class VoxyCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        var imports = LiteralArgumentBuilder.<CommandSourceStack>literal("import")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("world")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("world_name", StringArgumentType.string())
                                .suggests(VoxyCommands::importWorldSuggester)
                                .executes(VoxyCommands::importWorld)))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("bobby")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("world_name", StringArgumentType.string())
                                .suggests(VoxyCommands::importBobbySuggester)
                                .executes(VoxyCommands::importBobby)))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("raw")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("path", StringArgumentType.string())
                                .executes(VoxyCommands::importRaw)))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("zip")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("zipPath", StringArgumentType.string())
                                .executes(VoxyCommands::importZip)
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("innerPath", StringArgumentType.string())
                                        .executes(VoxyCommands::importZip))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("current")
                        .executes(VoxyCommands::importCurrentWorldIn))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("cancel")
                        .executes(VoxyCommands::cancelImport));

        if (DHImporter.HasRequiredLibraries) {
            imports = imports
                    .then(LiteralArgumentBuilder.<CommandSourceStack>literal("distant_horizons")
                            .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("sqlDbPath", StringArgumentType.string())
                                    .executes(VoxyCommands::importDistantHorizons)));
        }

        var debug = LiteralArgumentBuilder.<CommandSourceStack>literal("debug")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("verifyTLNChildMask")
                        .executes(ctx -> verifyTLNs(ctx, false))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Boolean>argument("attemptRepair", BoolArgumentType.bool())
                                .executes(ctx -> verifyTLNs(ctx, BoolArgumentType.getBool(ctx, "attemptRepair")))));

        return LiteralArgumentBuilder.<CommandSourceStack>literal("voxy")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("reload")
                        .executes(VoxyCommands::reloadInstance))
                .then(imports)
                .then(debug);
    }

    private static void sendError(CommandContext<CommandSourceStack> ctx, Component msg) {
        ctx.getSource().sendSystemMessage(Component.literal("").append(msg).setStyle(Style.EMPTY.withColor(0xFF5555)));
    }

    private static int reloadInstance(CommandContext<CommandSourceStack> ctx) {
        try {
            return reloadInstance0(ctx);
        } catch (Throwable e) {
            // 捕获所有异常 (包括 Error),把根因直接发给用户,
            // 避免 Brigadier 包裹成 "试图执行该命令时出现意外错误"。
            var root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            var msg = Component.literal("Voxy reload failed: " + root.getClass().getSimpleName() + ": " + root.getMessage());
            sendError(ctx, msg);
            // 同时记录完整堆栈到日志
            me.cortex.voxy.common.Logger.error("reloadInstance command failed", e);
            return 1;
        }
    }

    private static int reloadInstance0(CommandContext<CommandSourceStack> ctx) {
        // 如果 instance 为 null,尝试创建实例 (可能 sessionStart 未触发或创建失败)
        if (VoxyCommon.getInstance() == null) {
            if (!VoxyCommon.isAvailable()) {
                sendError(ctx, Component.literal("Voxy is not available (mod not initialized properly)"));
                return 1;
            }
            if (!VoxyConfig.CONFIG.enabled) {
                sendError(ctx, Component.literal("Voxy is disabled in config (set enabled=true in voxy-config.json)"));
                return 1;
            }
            // 尝试创建实例
            VoxyCommon.createInstance();
            if (VoxyCommon.getInstance() == null) {
                sendError(ctx, Component.literal("Failed to create Voxy instance, check logs for errors"));
                return 1;
            }
            // 实例刚创建,尝试创建渲染器
            var holder = IVoxyRenderSystemHolder.getNullableHolder();
            if (holder != null) {
                holder.voxy$createRenderer();
            }
            ctx.getSource().sendSystemMessage(Component.literal("Voxy instance created").setStyle(Style.EMPTY.withColor(0x55FF55)));
            return 0;
        }

        var vrsh = IVoxyRenderSystemHolder.getNullableHolder();
        if (vrsh != null) {
            vrsh.voxy$shutdownRenderer();
        }

        VoxyCommon.shutdownInstance();
        System.gc();
        VoxyCommon.createInstance();

        var r = Minecraft.getInstance().levelRenderer;
        if (r instanceof IVoxyRenderSystemHolder h) {
            h.voxy$createRenderer();
        }
        return 0;
    }

    private static int verifyTLNs(CommandContext<CommandSourceStack> ctx, boolean attemptRepair) {
        var instance = VoxyCommon.getInstance();
        if (instance == null) {
            sendError(ctx, Component.translatable("Voxy must be enabled in settings to use this"));
            return 1;
        }
        if (Minecraft.getInstance().level == null) {
            throw new IllegalStateException("How you even do this");
        }
        var engine = WorldIdentifier.ofEngine(Minecraft.getInstance().level);
        if (engine != null) {
            DebugUtils.verifyAllTopLevelNodes(engine, attemptRepair);
            return 0;
        }
        return 1;
    }

    private static int importDistantHorizons(CommandContext<CommandSourceStack> ctx) {
        var instance = VoxyCommon.getInstance();
        if (instance == null) {
            sendError(ctx, Component.translatable("Voxy must be enabled in settings to use this"));
            return 1;
        }
        var dbFile = new File(ctx.getArgument("sqlDbPath", String.class));
        if (!dbFile.exists()) {
            return 1;
        }
        if (dbFile.isDirectory()) {
            dbFile = dbFile.toPath().resolve("DistantHorizons.sqlite").toFile();
            if (!dbFile.exists()) {
                return 1;
            }
        }

        File dbFile_ = dbFile;
        var engine = WorldIdentifier.ofEngine(Minecraft.getInstance().level);
        if (engine == null) return 1;
        return instance.getImportManager().makeAndRunIfNone(engine, () ->
                new DHImporter(dbFile_, engine, Minecraft.getInstance().level, instance.getServiceManager(), instance.savingServiceRateLimiter)) ? 0 : 1;
    }

    private static boolean fileBasedImporter(File directory) {
        var instance = VoxyCommon.getInstance();
        if (instance == null) {
            return false;
        }

        var engine = WorldIdentifier.ofEngine(Minecraft.getInstance().level);
        if (engine == null) return false;
        return instance.getImportManager().makeAndRunIfNone(engine, () -> {
            var importer = new WorldImporter(engine, Minecraft.getInstance().level, instance.getServiceManager(), instance.savingServiceRateLimiter);
            importer.importRegionDirectoryAsync(directory);
            return importer;
        });
    }

    private static int importRaw(CommandContext<CommandSourceStack> ctx) {
        if (VoxyCommon.getInstance() == null) {
            sendError(ctx, Component.translatable("Voxy must be enabled in settings to use this"));
            return 1;
        }
        return fileBasedImporter(new File(ctx.getArgument("path", String.class))) ? 0 : 1;
    }

    private static int importBobby(CommandContext<CommandSourceStack> ctx) {
        if (VoxyCommon.getInstance() == null) {
            sendError(ctx, Component.translatable("Voxy must be enabled in settings to use this"));
            return 1;
        }
        var file = new File(".bobby").toPath().resolve(ctx.getArgument("world_name", String.class)).toFile();
        return fileBasedImporter(file) ? 0 : 1;
    }

    private static CompletableFuture<Suggestions> importWorldSuggester(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder sb) {
        return fileDirectorySuggester(Minecraft.getInstance().gameDirectory.toPath().resolve("saves"), sb);
    }

    private static CompletableFuture<Suggestions> importBobbySuggester(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder sb) {
        return fileDirectorySuggester(Minecraft.getInstance().gameDirectory.toPath().resolve(".bobby"), sb);
    }

    private static CompletableFuture<Suggestions> fileDirectorySuggester(Path dir, SuggestionsBuilder sb) {
        var str = sb.getRemaining().replace("\\\\", "\\").replace("\\", "/");
        if (str.startsWith("\"")) {
            str = str.substring(1);
        }
        if (str.endsWith("\"")) {
            str = str.substring(0, str.length() - 1);
        }
        var remaining = str;
        if (str.contains("/")) {
            int idx = str.lastIndexOf('/');
            remaining = str.substring(idx + 1);
            try {
                dir = dir.resolve(str.substring(0, idx));
            } catch (Exception e) {
                return Suggestions.empty();
            }
            str = str.substring(0, idx + 1);
        } else {
            str = "";
        }

        try {
            var worlds = Files.list(dir).toList();
            for (var world : worlds) {
                if (!world.toFile().isDirectory()) {
                    continue;
                }
                var wn = world.getFileName().toString();
                if (wn.equals(remaining)) {
                    continue;
                }
                if (SharedSuggestionProvider.matchesSubStr(remaining, wn) || SharedSuggestionProvider.matchesSubStr(remaining, '"' + wn)) {
                    wn = str + wn + "/";
                    sb.suggest(StringArgumentType.escapeIfRequired(wn));
                }
            }
        } catch (IOException e) {
        }

        return sb.buildFuture();
    }

    private static int importCurrentWorldIn(CommandContext<CommandSourceStack> ctx) {
        if (VoxyCommon.getInstance() == null) {
            sendError(ctx, Component.translatable("Voxy must be enabled in settings to use this"));
            return 1;
        }

        var localServer = Minecraft.getInstance().getSingleplayerServer();
        if (localServer == null) {
            sendError(ctx, Component.translatable("You must be in single player to use this command"));
            return 1;
        }
        var regionPath = DimensionType.getStorageFolder(Minecraft.getInstance().level.dimension(), localServer.getWorldPath(LevelResource.ROOT)).resolve("region");
        if ((!regionPath.toFile().exists()) || !regionPath.toFile().isDirectory()) {
            sendError(ctx, Component.translatable("Cannot find region folder for current dimension"));
            return 1;
        }
        return fileBasedImporter(regionPath.toFile()) ? 0 : 1;
    }

    private static int importWorld(CommandContext<CommandSourceStack> ctx) {
        if (VoxyCommon.getInstance() == null) {
            sendError(ctx, Component.translatable("Voxy must be enabled in settings to use this"));
            return 1;
        }

        var name = ctx.getArgument("world_name", String.class);
        var file = new File("saves").toPath().resolve(name);
        name = name.toLowerCase(Locale.ROOT);
        if (name.endsWith("/")) {
            name = name.substring(0, name.length() - 1);
        }
        if (file.resolve("level.dat").toFile().exists()) {
            var dimFile = DimensionType.getStorageFolder(Minecraft.getInstance().level.dimension(), file)
                    .resolve("region")
                    .toFile();
            if (!dimFile.isDirectory()) return 1;
            return fileBasedImporter(dimFile) ? 0 : 1;
        } else {
            if (!(name.endsWith("region"))) {
                file = file.resolve("region");
            }
            return fileBasedImporter(file.toFile()) ? 0 : 1;
        }
    }

    private static int importZip(CommandContext<CommandSourceStack> ctx) {
        var zip = new File(ctx.getArgument("zipPath", String.class));
        var innerDir = "region/";
        try {
            innerDir = ctx.getArgument("innerPath", String.class);
        } catch (Exception e) {
        }

        var instance = VoxyCommon.getInstance();
        if (instance == null) {
            sendError(ctx, Component.translatable("Voxy must be enabled in settings to use this"));
            return 1;
        }
        String finalInnerDir = innerDir;

        var engine = WorldIdentifier.ofEngine(Minecraft.getInstance().level);
        if (engine != null) {
            return instance.getImportManager().makeAndRunIfNone(engine, () -> {
                var importer = new WorldImporter(engine, Minecraft.getInstance().level, instance.getServiceManager(), instance.savingServiceRateLimiter);
                importer.importZippedRegionDirectoryAsync(zip, finalInnerDir);
                return importer;
            }) ? 0 : 1;
        }
        return 1;
    }

    private static int cancelImport(CommandContext<CommandSourceStack> ctx) {
        var instance = VoxyCommon.getInstance();
        if (instance == null) {
            sendError(ctx, Component.translatable("Voxy must be enabled in settings to use this"));
            return 1;
        }
        var world = WorldIdentifier.ofEngineNullable(Minecraft.getInstance().level);
        if (world != null) {
            return instance.getImportManager().cancelImport(world) ? 0 : 1;
        }
        return 1;
    }
}
