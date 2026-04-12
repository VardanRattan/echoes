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
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import static net.minecraft.commands.Commands.argument;
import net.minecraft.server.permissions.Permissions;

import java.util.List;
import java.util.UUID;


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

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                 CommandBuildContext registryAccess,
                                 Commands.CommandSelection env) {

        dispatcher.register(Commands.literal("echoes")
                .then(Commands.literal("clear")
                        .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(ctx -> clearNearby(ctx.getSource(), null, 16.0))
                        .then(Commands.argument("player", EntityArgument.player()) // F3: [player] arg
                                .executes(ctx -> clearNearby(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), 16.0))
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0, 256.0))
                                        .executes(ctx -> clearNearby(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), DoubleArgumentType.getDouble(ctx, "radius")))))
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0, 256.0))
                                .executes(ctx -> clearNearby(ctx.getSource(), null, DoubleArgumentType.getDouble(ctx, "radius")))))
                .then(Commands.literal("debug") // F4: /echoes debug
                        .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(ctx -> {
                            boolean current = EchoesConfig.toggleDebug();
                            ctx.getSource().sendSuccess(() -> Component.literal("Debug logging: " + (current ? "ENABLED" : "DISABLED")), true);
                            return 1;
                        }))
                .then(Commands.literal("optout")
                        .then(argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("visibility");
                                    builder.suggest("display");
                                    builder.suggest("all");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    CommandSourceStack source = ctx.getSource();
                                    ServerPlayer player = source.getPlayerOrException();
                                    if (player == null) {
                                        source.sendFailure(Component.literal("Command can only be used by a player."));
                                        return 0;
                                    }
                                    if (!EchoesConfig.get().isAllowPlayerOptout()) {
                                        source.sendFailure(Component.literal("Player opt-out is disabled on this server."));
                                        return 0;
                                    }

                                    String mode = StringArgumentType.getString(ctx, "mode");
                                    applyOptout(source, player, mode);
                                    return 1;
                                }))));
    }

    private static void applyOptout(CommandSourceStack source, ServerPlayer player, String modeRaw) {
        String mode = modeRaw.toLowerCase();
        boolean visibility = mode.equals("visibility") || mode.equals("all");
        boolean display = mode.equals("display") || mode.equals("all");

        var world = player.level();
        ServerLevel level = (ServerLevel) world;
        var state = EchoWorldState.get(level);
        PlayerEchoData data = state.getOrCreatePlayerData(player.getUUID());

        if (visibility) {
            data.setOptedOut(true);
            // G7: Purge all echoes by this player on opt-out
            state.purgeEchoesByPlayer(player.getUUID());
        }
        if (display) {
            data.setDisplayOptedOut(true);
        }
        state.setDirty();

        source.sendSuccess(() ->
                        Component.literal("Echoes opt-out updated: visibility=" + data.isOptedOut()
                                + " display=" + data.isDisplayOptedOut()),
                false);
    }

    private static int clearNearby(CommandSourceStack source, ServerPlayer targetPlayer, double radius) {
        ServerPlayer player = targetPlayer;
        if (player == null) {
            try {
                player = source.getPlayerOrException();
            } catch (Exception ignored) {
            }
        }

        if (player == null) {
            source.sendFailure(Component.literal("No player context or target specified."));
            return 0;
        }

        var world = player.level();
        ServerLevel level = (ServerLevel) world;
        var state = EchoWorldState.get(level);

        BlockPos center = player.blockPosition();
        List<EchoRecord> echoes = state.getEchoesNear(center, (int) radius, level.dimension());

        int removed = 0;
        for (EchoRecord record : echoes) {
            UUID id = record.getUUID();
            state.removeEcho(id);
            removed++;
        }

        final int removedCount = removed;
        final int displayRadius = (int) radius;
        source.sendSuccess(
                () -> Component.literal("Cleared " + removedCount + " echoes within " + displayRadius + " blocks."),
                true
        );
        Echoes.LOGGER.info("Cleared {} echoes near {} at radius {}", removed, player.getName().getString(), radius);
        return removed;
    }
}

