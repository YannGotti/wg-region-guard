package ru.voidrp.regionguard;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.voidrp.regionguard.bridge.WorldGuardBridge;
import ru.voidrp.regionguard.config.GuardConfig;
import ru.voidrp.regionguard.handler.BlockBreakHandler;
import ru.voidrp.regionguard.handler.PlayerActionHandler;
import ru.voidrp.regionguard.handler.ProjectileHandler;

@Mod(RegionGuardMod.MODID)
public class RegionGuardMod {

    public static final String MODID = "wgregionguard";
    private static final Logger LOG = LoggerFactory.getLogger("WGRegionGuard");

    public RegionGuardMod(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, GuardConfig.SPEC, "wg-region-guard-server.toml");

        modEventBus.addListener(this::onCommonSetup);

        // Register NeoForge game-event handlers
        NeoForge.EVENT_BUS.register(new BlockBreakHandler());
        NeoForge.EVENT_BUS.register(new PlayerActionHandler());
        NeoForge.EVENT_BUS.register(new ProjectileHandler());

        // Init WG bridge after Bukkit plugins are fully loaded
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);

        LOG.info("WG Region Guard loaded — waiting for WorldGuard...");
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // Nothing needed at mod setup time; WG initialises during Bukkit server start
    }

    private void onServerStarted(ServerStartedEvent event) {
        WorldGuardBridge.init();
    }
}
