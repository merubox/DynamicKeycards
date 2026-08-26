package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.item.EstateMaintenanceCardItem;
import com.mbx.dynamickeycards.item.GoldenMaintenanceCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The maintenance tier of access control - opening a device's wrench config UI, or picking it up
 * with a wrench - see {@code CardReaderBlock}'s class doc for the key-tier counterpart (register
 * mode, card registration, reset) and why the two are kept as separate cards/checks.
 *
 * <p><b>Locked vs. lock-free devices.</b> This mod deliberately splits every device into two
 * groups, and the split is the whole point of this class:
 *
 * <ul>
 *   <li><b>Locked</b> - a card reader, plus whichever peripherals are actually <em>linked to</em>
 *   one (an advanced sensor bound to a reader, a receiver whose source resolves to a reader).
 *   These have an owner to protect, so the maintenance tier is genuinely restricted: the owner
 *   themselves, a golden maintenance card, or an estate maintenance card <em>bound to that same
 *   owner</em>. Someone else's estate card does not work - see {@link #hasAccess}.</li>
 *   <li><b>Neutral</b> - a reader that is locked but has <em>no</em> owner, either because
 *   {@code /dynamickeycards release} removed it or because something other than a player placed
 *   it. Still a locked device, but with an owner nothing can match, which leaves the golden
 *   maintenance card as the only way in. Deliberately not the same as lock-free.</li>
 *   <li><b>Lock-free</b> - a transmitter, a receiver bound to anything that isn't a reader, an
 *   unbound or sensor-bound sensor, the card duplicator. These have no owner at all and are
 *   meant to work that way (a transmitter/receiver pair is a plain wireless redstone device, not
 *   an access-control one). Nothing is restricted here: a wrench works, and so does either
 *   maintenance card, acting purely as a wrench-equivalent for a Create-less game.</li>
 * </ul>
 *
 * <p>So an estate maintenance card is usable on <em>every</em> lock-free device regardless of who
 * it's bound to, and on locked devices only where its binding matches. Which of the three groups
 * a device is in is decided by its caller: a reader always goes through {@link #hasAccess}
 * directly (locked, or neutral when its owner is {@code null}), while a peripheral goes through
 * {@link #hasReaderLinkedAccess}, which answers "lock-free" when no reader is at the far end of
 * the link at all. Every device's own trigger check should therefore
 * accept {@link #isMaintenanceCard} as a whole rather than singling out the golden one - a
 * lock-free device that accepted only the golden card would be inconsistent with every other
 * lock-free device, not stricter in any meaningful way.
 */
public final class MaintenanceAccess {

    private MaintenanceAccess() {
    }

    /**
     * The reader's own check - {@code readerOwner} is that reader's directly-held owner field,
     * always known (never resolved through anything). {@code true} if {@code triggerStack} is a
     * golden maintenance card, the reader's own owner, or an estate maintenance card bound to
     * that same owner.
     */
    public static boolean hasAccess(Player player, ItemStack triggerStack, @Nullable UUID readerOwner) {
        if (triggerStack.getItem() instanceof GoldenMaintenanceCardItem) {
            return true;
        }
        if (readerOwner == null) {
            // A reader with no owner is neutral, not unlocked: nothing below can match a
            // non-existent owner, so only the golden card above gets in. That is what
            // /dynamickeycards release produces, and also what a reader placed by something
            // other than a player has always been. Devices that have no owner *concept* never
            // reach this method at all - see hasReaderLinkedAccess, which answers "open" for
            // them before delegating here.
            return false;
        }
        if (readerOwner.equals(player.getUUID())) {
            return true;
        }
        if (triggerStack.getItem() instanceof EstateMaintenanceCardItem) {
            UUID cardOwner = EstateMaintenanceCardItem.boundOwner(triggerStack);
            return cardOwner != null && cardOwner.equals(readerOwner);
        }
        return false;
    }

    /**
     * The peripheral's check (a bound advanced sensor's {@code boundReaderId}, or a receiver's
     * {@code boundSourceId}, whatever that currently happens to point at - the resolution below
     * sorts out whether it's actually a reader). {@code readerId} itself being {@code null}
     * (unbound) always grants access - there's no owner in the relationship to protect. Same for
     * every other "can't tell" case: {@code readerId} pointing at something other than a reader,
     * or its chunk being unloaded right now (never force-loaded here - see
     * {@link #resolveReader}). A reader that resolves but has no owner is <em>not</em> one of
     * those cases: it is neutral, and {@link #hasAccess} locks it to the golden card. This mod's
     * own established rule for "can't currently verify" (see {@code ROADMAP.md}'s shared-cache
     * discussion, "확인 못 함 = 일단 그대로 둠") is to not restrict access over it, not to lock
     * out an owner just because the far end of a wireless link happens to be out of range right
     * now.
     */
    public static boolean hasReaderLinkedAccess(Player player, ItemStack triggerStack, Level level, @Nullable UUID readerId) {
        if (readerId == null || triggerStack.getItem() instanceof GoldenMaintenanceCardItem
                || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        CardReaderBlockEntity reader = resolveReader(serverLevel, readerId);
        // no reader at the far end of the link - nothing here has an owner to protect, so this
        // is a lock-free device and stays open. Distinct from a reader that *has* no owner,
        // which hasAccess treats as neutral (golden card only).
        return reader == null || hasAccess(player, triggerStack, reader.getOwner());
    }

    /**
     * Whether {@code stack} is either maintenance card. Callers OR this in alongside their
     * existing {@code stack.is(Tags.Items.TOOLS_WRENCH)} check - neither is gated on Create being
     * loaded (a wrench-tagged item could come from some other mod entirely, and a
     * maintenance card never involves Create either way).
     */
    public static boolean isMaintenanceCard(ItemStack stack) {
        return stack.getItem() instanceof GoldenMaintenanceCardItem || stack.getItem() instanceof EstateMaintenanceCardItem;
    }

    /**
     * Resolves {@code readerId} to its reader, or {@code null} if it doesn't resolve to a live
     * position right now (its chunk may simply be unloaded - never force-loaded here) or doesn't
     * actually point at a {@link CardReaderBlockEntity} at all (a receiver's source can just as
     * easily be a transmitter or an advanced sensor, neither of which this tier applies to).
     */
    @Nullable
    private static CardReaderBlockEntity resolveReader(ServerLevel level, UUID readerId) {
        BlockPos pos = DeviceIndex.get(level).getPosition(readerId);
        if (pos == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof CardReaderBlockEntity reader ? reader : null;
    }
}
