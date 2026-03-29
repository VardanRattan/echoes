package com.vardanrattan.echoes.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.vardanrattan.echoes.Echoes;
import com.vardanrattan.echoes.config.EchoesConfig;
import com.vardanrattan.echoes.data.EchoRecord;
import com.vardanrattan.echoes.data.EchoWorldState;
import com.vardanrattan.echoes.data.PlayerEchoData;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /echoes command tree:
 * - /echoes optout [visibility|display|all]
 * - /echoes clear [radius]
 */
public final class EchoesCommand {

    private EchoesCommand() {
    }

    public static void registerCallback() {
        CommandRegistrationCallback.EVENT.register(EchoesCommand::register);
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                 CommandRegistryAccess registryAccess,
                                 CommandManager.RegistrationEnvironment env) {

        dispatcher.register(CommandManager.literal("echoes")
                .then(CommandManager.literal("clear")
                        .requires(s -> s.hasPermissionLevel(2)) // F3: Permission check
                        .executes(ctx -> clearNearby(ctx.getSource(), null, 16.0))
                        .then(argument("player", EntityArgumentType.player()) // F3: [player] arg
                                .executes(ctx -> clearNearby(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), 16.0))
                                .then(argument("radius", DoubleArgumentType.doubleArg(1.0, 256.0))
                                        .executes(ctx -> clearNearby(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), DoubleArgumentType.getDouble(ctx, "radius")))))
                        .then(argument("radius", DoubleArgumentType.doubleArg(1.0, 256.0))
                                .executes(ctx -> clearNearby(ctx.getSource(), null, DoubleArgumentType.getDouble(ctx, "radius")))))
                .then(CommandManager.literal("debug") // F4: /echoes debug
                        .requires(s -> s.hasPermissionLevel(2))
                        .executes(ctx -> {
                            boolean current = EchoesConfig.toggleDebug();
                            ctx.getSource().sendFeedback(() -> Text.literal("Debug logging: " + (current ? "ENABLED" : "DISABLED")), true);
                            return 1;
                        }))
                .then(CommandManager.literal("optout")
                        .then(argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("visibility");
                                    builder.suggest("display");
                                    builder.suggest("all");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerCommandSource source = ctx.getSource();
                                    ServerPlayerEntity player = source.getPlayer();
                                    if (player == null) {
                                        source.sendError(Text.literal("Command can only be used by a player."));
                                        return 0;
                                    }
                                    if (!EchoesConfig.get().isAllowPlayerOptout()) {
                                        source.sendError(Text.literal("Player opt-out is disabled on this server."));
                                        return 0;
                                    }

                                    String mode = StringArgumentType.getString(ctx, "mode");
                                    applyOptout(source, player, mode);
                                    return 1;
                                }))));
    }

    private static void applyOptout(ServerCommandSource source, ServerPlayerEntity player, String modeRaw) {
        String mode = modeRaw.toLowerCase();
        boolean visibility = mode.equals("visibility") || mode.equals("all");
        boolean display = mode.equals("display") || mode.equals("all");

        var world = player.getEntityWorld();
        var state = EchoWorldState.get((ServerWorld) world);
        PlayerEchoData data = state.getOrCreatePlayerData(player.getUuid());

        if (visibility) {
            data.setOptedOut(true);
            // G7: Purge all echoes by this player on opt-out
            state.purgeEchoesByPlayer(player.getUuid());
        }
        if (display) {
            data.setDisplayOptedOut(true);
        }
        state.markDirty();

        source.sendFeedback(() ->
                        Text.literal("Echoes opt-out updated: visibility=" + data.isOptedOut()
                                + " display=" + data.isDisplayOptedOut()),
                false);
    }

    private static int clearNearby(ServerCommandSource source, ServerPlayerEntity targetPlayer, double radius) {
        ServerPlayerEntity player = targetPlayer;
        if (player == null) {
            try {
                player = source.getPlayer();
            } catch (Exception ignored) {
            }
        }

        if (player == null) {
            source.sendError(Text.literal("No player context or target specified."));
            return 0;
        }

        var world = player.getEntityWorld();
        var state = EchoWorldState.get((ServerWorld) world);

        BlockPos center = player.getBlockPos();
        List<EchoRecord> echoes = state.getEchoesNear(center, (int) radius, world.getRegistryKey());

        int removed = 0;
        for (EchoRecord record : echoes) {
            UUID id = record.getUuid();
            state.removeEcho(id);
            removed++;
        }

        final int removedCount = removed;
        final int displayRadius = (int) radius;
        source.sendFeedback(
                () -> Text.literal("Cleared " + removedCount + " echoes within " + displayRadius + " blocks."),
                true
        );
        Echoes.LOGGER.info("Cleared {} echoes near {} at radius {}", removed, player.getName().getString(), radius);
        return removed;
    }
}

