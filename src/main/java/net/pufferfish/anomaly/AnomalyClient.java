package net.pufferfish.anomaly;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.item.CompassAnglePredicateProvider;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.pufferfish.anomaly.client.ModModelPredicates;
import net.pufferfish.anomaly.client.render.NoRenderEntityRenderer;
import net.pufferfish.anomaly.entity.ModEntities;
import net.pufferfish.anomaly.entity.OverchargedAnomalyEntity;
import net.pufferfish.anomaly.entity.WildRuneEntity;
import net.pufferfish.anomaly.item.EcholocatorItem;
import net.pufferfish.anomaly.item.ModItems;
import net.pufferfish.anomaly.screen.ModScreenHandlers;
import net.pufferfish.anomaly.screen.TeleporterTerminalScreen;

public class AnomalyClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(ModScreenHandlers.TELEPORTER_TERMINAL, TeleporterTerminalScreen::new);
        ModModelPredicates.init();
        EntityRendererRegistry.register(
                ModEntities.OVERCHARGED_ANOMALY,
                ctx -> new EmptyEntityRenderer<OverchargedAnomalyEntity>(ctx)
        );

        EntityRendererRegistry.register(
                ModEntities.WILD_RUNE,
                ctx -> new EmptyEntityRenderer<WildRuneEntity>(ctx)
        );

        // === Echolocator: vanilla compass-style angle predicate ===
        ModelPredicateProviderRegistry.register(
                ModItems.ECHOLOCATOR,
                new Identifier("minecraft", "angle"),
                new CompassAnglePredicateProvider((ClientWorld world, ItemStack stack, Entity entity) -> {
                    NbtCompound nbt = stack.getNbt();
                    if (nbt == null || !nbt.contains(EcholocatorItem.NBT_TARGET_POS)) return null;

                    // NOTE: while debugging, do NOT return null for dimension mismatch,
                    // because that would keep you stuck on frame 00.
                    BlockPos pos = BlockPos.fromLong(nbt.getLong(EcholocatorItem.NBT_TARGET_POS));
                    return GlobalPos.create(world.getRegistryKey(), pos);
                })
        );

        EntityRendererRegistry.register(ModEntities.LANDING_PAD_PROJECTILE, NoRenderEntityRenderer::new);
    }
}