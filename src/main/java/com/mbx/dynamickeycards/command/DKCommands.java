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
 * {@code /dynamickeycards transfer <player>} — hands the card reader the executor is
 * looking at over to another player. Allowed for the reader's owner and for operators;
 * registrations and the pulse override are kept, only the owner changes.
 */
@EventBusSubscriber(modid = DynamicKeycards.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class DKCommands {

    private static final SimpleCommandExceptionType NOT_LOOKING_AT_READER = new SimpleCommandExceptionType(
            Component.translatable("dynamickeycards.command.transfer.not_reader"));
    private static final SimpleCommandExceptionType NOT_OWNER = new SimpleCommandExceptionType(
            Component.translatable("dynamickeycards.command.transfer.not_owner"));
    private static final SimpleCommandExceptionType ALREADY_OWNER = new SimpleCommandExceptionType(
            Component.translatable("dynamickeycards.command.transfer.self"));

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dynamickeycards")
                .then(Commands.literal("transfer")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> transfer(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "target"))))));
    }

    private static int transfer(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(5.0, 0.0f, false);
        BlockPos pos = hit.getType() == HitResult.Type.BLOCK ? ((BlockHitResult) hit).getBlockPos() : null;
        if (pos == null || !(player.level().getBlockEntity(pos) instanceof CardReaderBlockEntity reader)) {
            throw NOT_LOOKING_AT_READER.create();
        }
        if (!reader.isOwner(player) && !source.hasPermission(2)) {
            throw NOT_OWNER.create();
        }
        if (reader.isOwner(target)) {
            throw ALREADY_OWNER.create();
        }
        reader.setOwner(target.getUUID());
        ServerPlayer executor = player;
        source.sendSuccess(
                () -> Component.translatable("dynamickeycards.command.transfer.success", target.getName()), true);
        target.displayClientMessage(
                Component.translatable("dynamickeycards.command.transfer.received", executor.getName()), false);
        return 1;
    }
}
