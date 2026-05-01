package ru.voidrp.regionguard.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.voidrp.regionguard.bridge.WorldGuardBridge;
import ru.voidrp.regionguard.config.GuardConfig;

/**
 * Handles player-triggered modded interactions and world-altering events:
 * - Carry On (picking up tile entities in protected regions)
 * - Mekanism Teleporter (teleporting into protected regions)
 * - Modded explosions affecting protected blocks
 * - Supplementaries player interactions (speaker, rope, etc.)
 */
public class PlayerActionHandler {

    private static final Logger LOG = LoggerFactory.getLogger("WGRegionGuard/PlayerAction");

    // ── Carry On ─────────────────────────────────────────────────────────────

    /**
     * Carry On raises a right-click-block event when a player lifts a tile entity.
     * We check the item held to detect Carry On's empty-hand carry mechanic.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack held = player.getMainHandItem();

        // Carry On: player's hand is empty + sneaking when lifting a block entity
        if (GuardConfig.BLOCK_CARRY_ON.get() && held.isEmpty() && player.isShiftKeyDown()) {
            BlockPos pos = event.getPos();
            // Only act if the block at that pos is a block entity (Carry On requires it)
            if (level.getBlockEntity(pos) != null) {
                if (!WorldGuardBridge.playerCanBuild(player, level, pos)) {
                    event.setCanceled(true);
                    LOG.debug("Blocked Carry On lift by {} at {}", player.getScoreboardName(), pos);
                }
            }
        }

        // Supplementaries right-click interactions (slingshot, flute, flag, rope, etc.)
        if (GuardConfig.BLOCK_SUPPLEMENTARIES.get()) {
            String ns = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(held.getItem()).getNamespace();
            if (ns.equals("supplementaries")) {
                BlockPos pos = event.getPos();
                if (!WorldGuardBridge.playerCanBuild(player, level, pos)) {
                    event.setCanceled(true);
                    LOG.debug("Blocked Supplementaries interaction by {} at {} (item={})",
                        player.getScoreboardName(), pos, held.getItem());
                }
            }
        }
    }

    // ── Mekanism Teleporter ───────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (!GuardConfig.BLOCK_MEKANISM_TELEPORTER.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // Only intercept Mekanism teleport (check caller)
        String caller = getCallerClass();
        if (!caller.contains("mekanism") && !caller.contains("Mekanism")) return;

        BlockPos dest = BlockPos.containing(event.getTargetX(), event.getTargetY(), event.getTargetZ());
        // Use the destination level if cross-dimensional (approximate using current level)
        if (!WorldGuardBridge.playerCanEnter(player, level, dest)) {
            event.setCanceled(true);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§cТелепортация заблокирована: регион защищён."));
            LOG.debug("Blocked Mekanism teleport of {} to {}", player.getScoreboardName(), dest);
        }
    }

    // ── Modded explosions ─────────────────────────────────────────────────────

    /**
     * Filters out protected blocks from explosion affected-block lists.
     * Catches CannonBallExplosion / BombExplosion from Supplementaries and other
     * modded explosions that bypass ProjectileImpactEvent or fire independently.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!GuardConfig.BLOCK_MODDED_EXPLOSIONS.get()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (event.getAffectedBlocks().isEmpty()) return;

        net.minecraft.world.level.Explosion explosion = event.getExplosion();

        // Identify source: the entity/direct cause of this explosion
        net.minecraft.world.entity.Entity source = explosion.getDirectSourceEntity() != null
            ? explosion.getDirectSourceEntity()
            : explosion.getIndirectSourceEntity();

        String sourceClass = source != null ? source.getClass().getName() : "";

        // Always block Supplementaries cannon/bomb explosions regardless of caller
        boolean isSupplementaries = sourceClass.contains("supplementaries");

        // For other explosions: skip vanilla TNT / creeper (WorldGuard handles those via Bukkit)
        // but catch modded device explosions
        boolean isVanillaCause = sourceClass.startsWith("net.minecraft") || source == null;

        if (!isSupplementaries && isVanillaCause) {
            // Vanilla explosion — WorldGuard already handles it via Bukkit EntityExplodeEvent
            return;
        }

        // Player-owned explosion source: respect their region permissions
        if (source instanceof ServerPlayer player) {
            event.getAffectedBlocks().removeIf(pos -> !WorldGuardBridge.playerCanBuild(player, level, pos));
        } else {
            // Machine/mob/cannonball explosion: block all protected blocks
            event.getAffectedBlocks().removeIf(pos -> !WorldGuardBridge.positionIsUnprotected(level, pos));
        }

        if (isSupplementaries) {
            LOG.debug("Filtered Supplementaries explosion from {} — {} blocks protected",
                sourceClass, event.getAffectedBlocks().size());
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static String getCallerClass() {
        try {
            return StackWalker.getInstance()
                .walk(s -> s.skip(3).limit(8)
                    .map(f -> f.getClassName())
                    .filter(n -> !n.startsWith("net.minecraft") && !n.startsWith("net.neoforged")
                              && !n.startsWith("ru.voidrp.regionguard"))
                    .findFirst()
                    .orElse(""));
        } catch (Exception e) {
            return "";
        }
    }
}
