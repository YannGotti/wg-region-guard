package ru.voidrp.regionguard.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

/**
 * Reflection-based bridge to WorldGuard running in the Bukkit plugin classloader.
 *
 * On Ketting/Mohist, NeoForge mods and Bukkit plugins live in separate classloaders.
 * Direct class references to com.sk89q.* throw NoClassDefFoundError from mod code.
 * We resolve WorldGuard's classloader at runtime and call everything via reflection.
 */
public final class WorldGuardBridge {

    private static final Logger LOG = LoggerFactory.getLogger("WGRegionGuard/Bridge");

    private static volatile boolean initialised = false;
    private static volatile boolean available   = false;

    // Cached reflection handles (set once in init)
    private static ClassLoader wgCL;
    private static Object      wgPluginInst;      // WorldGuardPlugin instance
    private static Object      regionContainer;   // RegionContainer instance
    private static Object      buildFlag;          // Flags.BUILD
    private static Object      entryFlag;          // Flags.ENTRY

    private static Method queryMethod;        // RegionContainer#createQuery()
    private static Method adaptLocMethod;     // BukkitAdapter#adapt(Location)
    private static Method wrapPlayerMethod;   // WorldGuardPlugin#wrapPlayer(Player)
    private static Method testStateMethod;    // RegionQuery#testState(Location, LocalPlayer, StateFlag[])
    private static Method getRegionsMethod;   // ApplicableRegionSet#getRegions()
    private static Method getRegIdMethod;     // ProtectedRegion#getId()

    private static Class<?> stateFlagClass;
    private static Class<?> localPlayerClass;

    private WorldGuardBridge() {}

    public static void init() {
        if (initialised) return;
        initialised = true;
        try {
            Plugin wgPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
            if (wgPlugin == null) {
                LOG.warn("WorldGuard plugin not found in Bukkit plugin manager. Protection disabled.");
                return;
            }

            wgCL = wgPlugin.getClass().getClassLoader();

            // ── WorldGuard.getInstance().getPlatform().getRegionContainer() ──────
            Class<?> wgClass      = wgCL.loadClass("com.sk89q.worldguard.WorldGuard");
            Object   wg           = wgClass.getMethod("getInstance").invoke(null);
            Object   platform     = wgClass.getMethod("getPlatform").invoke(wg);
            regionContainer       = platform.getClass().getMethod("getRegionContainer").invoke(platform);

            // ── WorldGuardPlugin.inst() ───────────────────────────────────────────
            Class<?> wgPluginClass = wgCL.loadClass("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            wgPluginInst           = wgPluginClass.getMethod("inst").invoke(null);

            // ── BukkitAdapter#adapt(Location) ─────────────────────────────────────
            Class<?> adapterClass  = wgCL.loadClass("com.sk89q.worldedit.bukkit.BukkitAdapter");
            adaptLocMethod         = adapterClass.getMethod("adapt", Location.class);

            // ── Flags.BUILD, Flags.ENTRY ──────────────────────────────────────────
            Class<?> flagsClass = wgCL.loadClass("com.sk89q.worldguard.protection.flags.Flags");
            buildFlag           = flagsClass.getField("BUILD").get(null);
            entryFlag           = flagsClass.getField("ENTRY").get(null);

            // ── RegionContainer#createQuery() ─────────────────────────────────────
            queryMethod = regionContainer.getClass().getMethod("createQuery");

            // ── RegionQuery#testState(weLocation, LocalPlayer, StateFlag[]) ───────
            stateFlagClass    = wgCL.loadClass("com.sk89q.worldguard.protection.flags.StateFlag");
            localPlayerClass  = wgCL.loadClass("com.sk89q.worldguard.LocalPlayer");
            Class<?> weLocClass = wgCL.loadClass("com.sk89q.worldedit.util.Location");
            Class<?> queryClass = wgCL.loadClass("com.sk89q.worldguard.protection.regions.RegionQuery");
            testStateMethod     = queryClass.getMethod("testState", weLocClass, localPlayerClass,
                                      Array.newInstance(stateFlagClass, 0).getClass());

            // ── WorldGuardPlugin#wrapPlayer(Player) ───────────────────────────────
            Class<?> bPlayerClass = wgCL.loadClass("org.bukkit.entity.Player");
            wrapPlayerMethod      = wgPluginClass.getMethod("wrapPlayer", bPlayerClass);

            // ── ApplicableRegionSet#getRegions() + ProtectedRegion#getId() ────────
            Class<?> regionSetClass  = wgCL.loadClass("com.sk89q.worldguard.protection.ApplicableRegionSet");
            getRegionsMethod         = regionSetClass.getMethod("getRegions");
            Class<?> protRegionClass = wgCL.loadClass("com.sk89q.worldguard.protection.regions.ProtectedRegion");
            getRegIdMethod           = protRegionClass.getMethod("getId");

            available = true;
            LOG.info("WorldGuard bridge initialised via plugin classloader — region protection active.");

        } catch (Exception e) {
            LOG.warn("Failed to initialise WorldGuard bridge: {} — {}. Protection disabled.",
                e.getClass().getSimpleName(), e.getMessage());
        }
    }

    public static boolean isAvailable() { return available; }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /** True if the player is allowed to build (place/break) at the given position. */
    public static boolean playerCanBuild(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return testFlag(player, level, pos, buildFlag);
    }

    /** True if the player is allowed to enter the region at the given position. */
    public static boolean playerCanEnter(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return testFlag(player, level, pos, entryFlag);
    }

    /**
     * True if the position is NOT inside any user-defined protected region.
     * Used for machine/non-player actions where there is no player to check against.
     */
    public static boolean positionIsUnprotected(ServerLevel level, BlockPos pos) {
        if (!available) return true;
        try {
            Object weLoc   = toWeLocation(level, pos);
            Object query   = queryMethod.invoke(regionContainer);

            // getApplicableRegions returns ApplicableRegionSet
            Method getApplicable = query.getClass().getMethod("getApplicableRegions", weLoc.getClass());
            Object regionSet     = getApplicable.invoke(query, weLoc);

            // getRegions() → Collection<ProtectedRegion>
            Iterable<?> regions = (Iterable<?>) getRegionsMethod.invoke(regionSet);
            for (Object region : regions) {
                String id = (String) getRegIdMethod.invoke(region);
                if (!"__global__".equals(id)) return false; // at least one user region
            }
            return true;

        } catch (Exception e) {
            LOG.debug("positionIsUnprotected check failed at {}: {}", pos, e.getMessage());
            return true; // fail-open
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static boolean testFlag(ServerPlayer player, ServerLevel level, BlockPos pos, Object flag) {
        if (!available) return true;
        try {
            Object weLoc      = toWeLocation(level, pos);
            Object bPlayer    = Bukkit.getPlayer(player.getUUID());
            if (bPlayer == null) return true;

            Object query      = queryMethod.invoke(regionContainer);
            Object localPlayer = wrapPlayerMethod.invoke(wgPluginInst, bPlayer);

            Object flagArr    = Array.newInstance(stateFlagClass, 1);
            Array.set(flagArr, 0, flag);

            return (Boolean) testStateMethod.invoke(query, weLoc, localPlayer, flagArr);

        } catch (Exception e) {
            LOG.debug("testFlag check failed at {}: {}", pos, e.getMessage());
            return true; // fail-open
        }
    }

    private static Object toWeLocation(ServerLevel level, BlockPos pos) throws Exception {
        World bukkit = getBukkitWorld(level);
        if (bukkit == null) throw new IllegalStateException(
            "Cannot resolve Bukkit world for: " + level.dimension().location());
        Location loc = new Location(bukkit, pos.getX(), pos.getY(), pos.getZ());
        return adaptLocMethod.invoke(null, loc);
    }

    private static World getBukkitWorld(ServerLevel level) {
        String dimPath = level.dimension().location().getPath(); // e.g. "overworld"

        // Ketting typically maps "overworld" → "world", "the_nether" → "world_nether", etc.
        World w = Bukkit.getWorld(dimPath);
        if (w != null) return w;

        // Common Ketting/Mohist mappings
        if ("overworld".equals(dimPath))  w = Bukkit.getWorld("world");
        if (w != null) return w;
        if ("the_nether".equals(dimPath)) w = Bukkit.getWorld("world_nether");
        if (w != null) return w;
        if ("the_end".equals(dimPath))    w = Bukkit.getWorld("world_the_end");
        if (w != null) return w;

        // Fallback: try full resource path including namespace
        String full = level.dimension().location().toString().replace(':', '_').replace('/', '_');
        w = Bukkit.getWorld(full);
        if (w != null) return w;

        // Last resort: scan all worlds
        for (World candidate : Bukkit.getWorlds()) {
            if (candidate.getName().contains(dimPath) || dimPath.contains(candidate.getName())) {
                return candidate;
            }
        }
        return null;
    }
}
