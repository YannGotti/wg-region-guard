package ru.voidrp.regionguard.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.voidrp.regionguard.bridge.WorldGuardBridge;
import ru.voidrp.regionguard.config.GuardConfig;

/**
 * Blocks modded projectiles (Create Big Cannons shells, Mahoutsukai spells,
 * Supplementaries slingshot) from dealing block damage inside protected regions.
 */
public class ProjectileHandler {

    private static final Logger LOG = LoggerFactory.getLogger("WGRegionGuard/Projectile");

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (!(event.getRayTraceResult() instanceof BlockHitResult hit)) return;

        BlockPos pos = hit.getBlockPos();
        if (!(event.getEntity() instanceof Projectile projectile)) return;

        // Identify the owner (shooter) of the projectile
        Entity owner = projectile.getOwner();

        boolean isModded = isModdedProjectile(projectile);
        if (!isModded) return;

        if (owner instanceof ServerPlayer player) {
            // Player-launched modded projectile: respect their WG permissions
            if (!WorldGuardBridge.playerCanBuild(player, level, pos)) {
                event.setCanceled(true);
                LOG.debug("Blocked modded projectile {} by {} at {}",
                    projectile.getClass().getSimpleName(), player.getScoreboardName(), pos);
            }
        } else {
            // Machine/cannon projectile with no player owner
            if (!WorldGuardBridge.positionIsUnprotected(level, pos)) {
                event.setCanceled(true);
                LOG.debug("Blocked ownerless modded projectile {} at {}",
                    projectile.getClass().getSimpleName(), pos);
            }
        }
    }

    /**
     * Identifies projectiles from mods that can break blocks on impact.
     * We don't block vanilla arrows / snowballs / etc. here — WG already handles those.
     */
    private static boolean isModdedProjectile(Projectile p) {
        String cls = p.getClass().getName();

        if (GuardConfig.BLOCK_CREATE_CANNONS.get() && cls.contains("createbigcannons")) {
            return true;
        }

        // Any Supplementaries projectile entity (CannonBallEntity, SlingshotProjectileEntity, BombEntity)
        if (GuardConfig.BLOCK_SUPPLEMENTARIES.get() && cls.contains("supplementaries")) {
            return true;
        }

        // L_Ender's Cataclysm boss projectiles (CMAbstractHurtingProjectile and all subclasses)
        if (GuardConfig.BLOCK_CATACLYSM.get() && cls.contains("cataclysm")) {
            return true;
        }

        // Immersive Aircraft weapons (BulletEntity, bomb drops)
        if (GuardConfig.BLOCK_IMMERSIVE_AIRCRAFT.get() && cls.contains("immersive_aircraft")) {
            return true;
        }

        // Mahoutsukai spell projectiles
        if (cls.contains("mahoutsukai")) {
            return true;
        }

        return false;
    }
}
