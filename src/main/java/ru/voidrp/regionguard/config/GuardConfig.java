package ru.voidrp.regionguard.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class GuardConfig {

    public static final ModConfigSpec SPEC;

    // ── Machine block interactions ────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue BLOCK_MACHINE_BREAK;
    public static final ModConfigSpec.BooleanValue BLOCK_MACHINE_PLACE;

    // ── Player-enhanced destruction (vein mining, falling-tree) ───────────────
    public static final ModConfigSpec.BooleanValue BLOCK_VEIN_MINING;
    public static final ModConfigSpec.BooleanValue BLOCK_FALLING_TREE;

    // ── Supplementaries (slingshot projectiles, speaker block, etc.) ──────────
    public static final ModConfigSpec.BooleanValue BLOCK_SUPPLEMENTARIES;

    // ── Create mod ────────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue BLOCK_CREATE_DEPLOYER;
    public static final ModConfigSpec.BooleanValue BLOCK_CREATE_CANNONS;

    // ── Industrial Foregoing ──────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue BLOCK_IF_BLOCK_PLACER;
    public static final ModConfigSpec.BooleanValue BLOCK_IF_BLOCK_BREAKER;

    // ── Carry On (lifting tile entities into protected zones) ─────────────────
    public static final ModConfigSpec.BooleanValue BLOCK_CARRY_ON;

    // ── Mekanism (teleporter) ─────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue BLOCK_MEKANISM_TELEPORTER;

    // ── L_Ender's Cataclysm ───────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue BLOCK_CATACLYSM;

    // ── Immersive Aircraft ────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue BLOCK_IMMERSIVE_AIRCRAFT;

    // ── Explosions originating in or affecting protected regions ─────────────
    public static final ModConfigSpec.BooleanValue BLOCK_MODDED_EXPLOSIONS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("WG Region Guard configuration").push("guard");

        builder.comment("Machine interactions").push("machines");
        BLOCK_MACHINE_BREAK = builder
            .comment("Cancel block-break events caused by non-player entities (Create Drill, IF Block Breaker, etc.)")
            .define("blockMachineBreak", true);
        BLOCK_MACHINE_PLACE = builder
            .comment("Cancel block-place events caused by non-player entities (Create Deployer, IF Block Placer, etc.)")
            .define("blockMachinePlace", true);
        builder.pop();

        builder.comment("Player-enhanced destruction").push("playerEnhanced");
        BLOCK_VEIN_MINING = builder
            .comment("Prevent vein-mining mod from breaking blocks in a different protected region than the origin block")
            .define("blockVeinMining", true);
        BLOCK_FALLING_TREE = builder
            .comment("Prevent FallingTree from chopping logs that are inside a protected region")
            .define("blockFallingTree", true);
        builder.pop();

        builder.comment("Supplementaries").push("supplementaries");
        BLOCK_SUPPLEMENTARIES = builder
            .comment("Block Supplementaries mechanics (slingshot hits, speaker activation) in protected regions")
            .define("blockSupplementaries", true);
        builder.pop();

        builder.comment("Create mod").push("create");
        BLOCK_CREATE_DEPLOYER = builder
            .comment("Prevent Create Deployer from placing/interacting in protected regions (covered by blockMachinePlace)")
            .define("blockCreateDeployer", true);
        BLOCK_CREATE_CANNONS = builder
            .comment("Prevent Create Big Cannons projectiles from dealing block damage in protected regions")
            .define("blockCreateCannons", true);
        builder.pop();

        builder.comment("Industrial Foregoing").push("industrialForegoing");
        BLOCK_IF_BLOCK_PLACER = builder
            .comment("Prevent IF Block Placer from placing in protected regions (covered by blockMachinePlace)")
            .define("blockIFPlacer", true);
        BLOCK_IF_BLOCK_BREAKER = builder
            .comment("Prevent IF Block Breaker from breaking in protected regions (covered by blockMachineBreak)")
            .define("blockIFBreaker", true);
        builder.pop();

        builder.comment("Carry On").push("carryOn");
        BLOCK_CARRY_ON = builder
            .comment("Prevent players from picking up (Carry On) block entities inside protected regions they cannot manage")
            .define("blockCarryOn", true);
        builder.pop();

        builder.comment("Mekanism").push("mekanism");
        BLOCK_MEKANISM_TELEPORTER = builder
            .comment("Prevent Mekanism Teleporter from sending players into regions that deny entry")
            .define("blockMekanismTeleporter", true);
        builder.pop();

        builder.comment("L_Ender's Cataclysm").push("cataclysm");
        BLOCK_CATACLYSM = builder
            .comment("Prevent L_Ender's Cataclysm boss projectiles from dealing block damage in protected regions")
            .define("blockCataclysm", true);
        builder.pop();

        builder.comment("Immersive Aircraft").push("immersiveAircraft");
        BLOCK_IMMERSIVE_AIRCRAFT = builder
            .comment("Prevent Immersive Aircraft bullets and bombs from dealing block damage in protected regions")
            .define("blockImmersiveAircraft", true);
        builder.pop();

        builder.comment("Explosions").push("explosions");
        BLOCK_MODDED_EXPLOSIONS = builder
            .comment("Cancel explosion block-damage inside protected regions (from modded devices, boss fights, etc.)")
            .define("blockModdedExplosions", true);
        builder.pop();

        builder.pop(); // guard
        SPEC = builder.build();
    }
}