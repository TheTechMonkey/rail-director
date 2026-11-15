package com.techmonkey.raildirector;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class RailDirector implements ModInitializer {
    public static final String MOD_ID = "raildirector";
    public static final Identifier WRENCH_ID = Identifier.fromNamespaceAndPath(MOD_ID, "rail_wrench");
    public static final Identifier CONTROLLED_DETECTOR_ID = Identifier.fromNamespaceAndPath(MOD_ID, "controlled_detector_rail");
    public static final ResourceKey<Item> WRENCH_KEY = ResourceKey.create(Registries.ITEM, WRENCH_ID);
    public static final ResourceKey<Block> CONTROLLED_DETECTOR_KEY = ResourceKey.create(Registries.BLOCK, CONTROLLED_DETECTOR_ID);

    public static final ControlledDetectorRailBlock CONTROLLED_DETECTOR_RAIL = Registry.register(
        BuiltInRegistries.BLOCK,
        CONTROLLED_DETECTOR_KEY,
        new ControlledDetectorRailBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DETECTOR_RAIL)
            .setId(CONTROLLED_DETECTOR_KEY))
    );

    public static final Item RAIL_WRENCH = Registry.register(
        BuiltInRegistries.ITEM,
        WRENCH_KEY,
        new RailWrenchItem(new Item.Properties().setId(WRENCH_KEY).stacksTo(1))
    );

    @Override
    public void onInitialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .register(output -> output.accept(RAIL_WRENCH));
    }

    public static boolean isOwnedDetector(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(CONTROLLED_DETECTOR_RAIL);
    }
}
