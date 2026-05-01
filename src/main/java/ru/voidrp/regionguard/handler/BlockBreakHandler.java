package ru.voidrp.regionguard.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.voidrp.regionguard.bridge.WorldGuardBridge;
import ru.voidrp.regionguard.config.GuardConfig;

/**
 * Intercepts block-break and block-place events that originate from modded
 * machines or player-enhanced tools (vein mining, falling-tree, etc.).
 *
 * WorldGuard already handles vanilla player breaks via Bukkit's BlockBreakEvent.
 * This handler catches the NeoForge-side events that Bukkit never sees.
 */
public class BlockBreakHandler {

    private static final Logger LOG = LoggerFactory.getLogger("WGRegionGuard/Break");

    // ── Modded machine / entity block-breaking ────────────────────────────────

    /**
     * Fires when any entity (or game mechanic) breaks a block.
     * Priority HIGH so we run before most other handlers but after logging ones.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        Entity breaker = event.getPlayer(); // may be a fake player for machines

        if (breaker instanceof ServerPlayer player && !(breaker instanceof FakePlayer)) {
            // Real player: only block if the action was enhanced (vein/tree) AND
            // the secondary block is in a different protected region.
            // WorldGuard has already handled the primary block via Bukkit.
            // We check here for enhanced tools that call destroyBlock() directly.
            handlePlayerBreak(event, player, level, pos);
        } else {
            // Machine/fake-player (Mekanism Digital Miner, Mahoutsukai ButterflyEntity, etc.)
            handleMachineBreak(event, level, pos);
        }
    }

    private void handlePlayerBreak(BlockEvent.BreakEvent event, ServerPlayer player,
                                    ServerLevel level, BlockPos pos) {
        if (!GuardConfig.BLOCK_VEIN_MINING.get() && !GuardConfig.BLOCK_FALLING_TREE.get()) return;

        // WorldGuard already checks the block the player directly targets via Bukkit.
        // Vein mining / FallingTree then break additional blocks by calling destroyBlock()
        // which re-fires BlockEvent.BreakEvent — but Bukkit's BlockBreakEvent may NOT
        // fire for those secondary calls, so we must catch them here.
        //
        // Heuristic: if the broken block is more than 5 blocks from the player's eye pos,
        // it wasn't a direct click — it's an extended/chained break by some mod.
        double dist = player.getEyePosition().distanceTo(
            net.minecraft.world.phys.Vec3.atCenterOf(pos));
        if (dist <= 6.0) return; // close enough → already handled by Bukkit/WG

        if (!WorldGuardBridge.playerCanBuild(player, level, pos)) {
            event.setCanceled(true);
            LOG.debug("Blocked remote break by {} at {} (dist={:.1f}) — vein/tree mod",
                player.getScoreboardName(), pos, dist);
        }
    }

    private void handleMachineBreak(BlockEvent.BreakEvent event, ServerLevel level, BlockPos pos) {
        if (!GuardConfig.BLOCK_MACHINE_BREAK.get()) return;

        if (!WorldGuardBridge.positionIsUnprotected(level, pos)) {
            event.setCanceled(true);
            LOG.debug("Blocked machine block-break at {}", pos);
        }
    }

    // ── Modded entity block-placing ───────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        Entity placer = event.getEntity();

        if (placer instanceof ServerPlayer player && !(placer instanceof FakePlayer)) {
            // Real player placing — WorldGuard covers this via Bukkit.
            // We only act if flagged mods are detected.
            handleSupplementariesPlace(event, player, level, pos);
        } else {
            // Machine/fake-player placing (Create Deployer, IF Block Placer, Mahoutsukai butterfly, etc.)
            if (!GuardConfig.BLOCK_MACHINE_PLACE.get()) return;
            if (!WorldGuardBridge.positionIsUnprotected(level, pos)) {
                event.setCanceled(true);
                LOG.debug("Blocked machine block-place at {} by entity {}", pos, placer);
            }
        }
    }

    /**
     * Supplementaries places blocks (ropes, flags, windvanes, etc.) via its own
     * right-click handler. We detect by checking whether the held item's registry
     * namespace is "supplementaries".
     */
    private void handleSupplementariesPlace(BlockEvent.EntityPlaceEvent event, ServerPlayer player,
                                             ServerLevel level, BlockPos pos) {
        if (!GuardConfig.BLOCK_SUPPLEMENTARIES.get()) return;

        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        String ns = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getKey(held.getItem()).getNamespace();
        if (!ns.equals("supplementaries")) return;

        if (!WorldGuardBridge.playerCanBuild(player, level, pos)) {
            event.setCanceled(true);
            LOG.debug("Blocked Supplementaries place by {} at {} (item={})",
                player.getScoreboardName(), pos, held.getItem());
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Returns the name of the calling class three frames up in the stack.
     * Used to detect which mod triggered the event without hard-coding class references.
     * Cost: one StackWalker call per event — acceptable on a low-frequency code path.
     */
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
