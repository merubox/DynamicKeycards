package com.mbx.dynamickeycards.command;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.block.CardReaderBlockEntity;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Commands operating on whichever card reader the executor is looking at. Both are allowed for
 * the reader's owner and for operators, and neither touches registrations or the pulse override -
 * only who owns the reader changes.
 *
 * <ul>
 *   <li>{@code /dynamickeycards transfer <player>} — hands the reader over to another player.</li>
 *   <li>{@code /dynamickeycards release} — drops the owner entirely, leaving the reader
 *   <em>neutral</em>: nothing can match an owner that doesn't exist, so the golden keycard (key
 *   tier) and golden maintenance card (maintenance tier) become the only way to administer it.
 *   Notably this is not reversible by the same command - see {@link #release}.</li>
 * </ul>
 */
@EventBusSubscriber(modid = DynamicKeycards.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class DKCommands {

    private static final SimpleCommandExceptionType NOT_LOOKING_AT_READER = new SimpleCommandExceptionType(
            Component.translatable("dynamickeycards.command.transfer.not_reader"));
    private static final SimpleCommandExceptionType NOT_OWNER = new SimpleCommandExceptionType(
            Component.translatable("dynamickeycards.command.transfer.not_owner"));
    private static final SimpleCommandExceptionType ALREADY_OWNER = new SimpleCommandExceptionType(
            Component.translatable("dynamickeycards.command.transfer.self"));
    private static final SimpleCommandExceptionType ALREADY_NEUTRAL = new SimpleCommandExceptionType(
            Component.translatable("dynamickeycards.command.release.already"));

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var root = event.getDispatcher().register(Commands.literal("dynamickeycards")
                .then(Commands.literal("transfer")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> transfer(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "target")))))
                .then(Commands.literal("release")
                        .executes(context -> release(context.getSource()))));
        // Short aliases - both, rather than picking just one, since either could already be
        // claimed by another mod in a given modpack and there's no real cost to offering both.
        event.getDispatcher().register(Commands.literal("dk").redirect(root));
        event.getDispatcher().register(Commands.literal("dks").redirect(root));
    }

    /**
     * Drops the reader's owner, leaving it neutral - only the golden cards can administer it
     * afterwards.
     *
     * <p>There is deliberately no inverse, and in particular no way to claim a neutral reader with
     * a golden card. A golden card is effectively operator-level access <em>within</em> the card
     * system, but ownership is the thing that system is built on, so handing it out is kept to
     * real operator commands ({@code transfer}) rather than to an item any player might end up
     * holding. Placing a reader remains the only other way an owner is ever set.
     */
    private static int release(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CardReaderBlockEntity reader = lookedAtReader(player);
        if (!reader.isOwner(player) && !source.hasPermission(2)) {
            throw NOT_OWNER.create();
        }
        if (reader.getOwner() == null) {
            throw ALREADY_NEUTRAL.create();
        }
        reader.setOwner(null);
        source.sendSuccess(() -> Component.translatable("dynamickeycards.command.release.success"), true);
        return 1;
    }

    /** The card reader the player is looking at, within reach. */
    private static CardReaderBlockEntity lookedAtReader(ServerPlayer player) throws CommandSyntaxException {
        HitResult hit = player.pick(5.0, 0.0f, false);
        BlockPos pos = hit.getType() == HitResult.Type.BLOCK ? ((BlockHitResult) hit).getBlockPos() : null;
        if (pos == null || !(player.level().getBlockEntity(pos) instanceof CardReaderBlockEntity reader)) {
            throw NOT_LOOKING_AT_READER.create();
        }
        return reader;
    }

    private static int transfer(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CardReaderBlockEntity reader = lookedAtReader(player);
        if (!reader.isOwner(player) && !source.hasPermission(2)) {
            throw NOT_OWNER.create();
        }
        if (reader.isOwner(target)) {
            throw ALREADY_OWNER.create();
        }
        reader.setOwner(target.getUUID());
        source.sendSuccess(
                () -> Component.translatable("dynamickeycards.command.transfer.success", target.getName()), true);
        target.displayClientMessage(
                Component.translatable("dynamickeycards.command.transfer.received", player.getName()), false);
        return 1;
    }
}
