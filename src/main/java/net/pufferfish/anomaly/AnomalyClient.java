package net.pufferfish.anomaly;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.item.CompassAnglePredicateProvider;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import net.pufferfish.anomaly.entity.ModEntities;
import net.pufferfish.anomaly.entity.OverchargedAnomalyEntity;
import net.pufferfish.anomaly.entity.WildRuneEntity;
import net.pufferfish.anomaly.item.EcholocatorCompassHandler;
import net.pufferfish.anomaly.item.RecoveryCompassHextechHandler;
import net.pufferfish.anomaly.screen.ModScreenHandlers;
import net.pufferfish.anomaly.screen.TeleporterTerminalScreen;

import java.util.UUID;

public class AnomalyClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(ModScreenHandlers.TELEPORTER_TERMINAL, TeleporterTerminalScreen::new);

        EntityRendererRegistry.register(
                ModEntities.OVERCHARGED_ANOMALY,
                ctx -> new EmptyEntityRenderer<OverchargedAnomalyEntity>(ctx)
        );

        EntityRendererRegistry.register(
                ModEntities.WILD_RUNE,
                ctx -> new EmptyEntityRenderer<WildRuneEntity>(ctx)
        );

        // Register ONE provider for recovery compass angle, handling all custom modes + vanilla fallback.
        FabricModelPredicateProviderRegistry.register(
                Items.RECOVERY_COMPASS,
                new Identifier("angle"),
                new CompassAnglePredicateProvider((ClientWorld world, ItemStack stack, Entity entity) -> {
                    if (entity == null) return null;

                    NbtCompound nbt = stack.getNbt();

                    // 1) Echolocator mode: compass points to signed player UUID (if present)
                    if (nbt != null && nbt.containsUuid(EcholocatorCompassHandler.NBT_ECHO_TARGET_UUID)) {
                        UUID target = nbt.getUuid(EcholocatorCompassHandler.NBT_ECHO_TARGET_UUID);

                        PlayerEntity targetPlayer = null;
                        for (PlayerEntity p : world.getPlayers()) {
                            if (p.getUuid().equals(target)) {
                                targetPlayer = p;
                                break;
                            }
                        }

                        // If player isn't present client-side (offline / different dimension), spin
                        if (targetPlayer == null) return null;

                        return GlobalPos.create(world.getRegistryKey(), targetPlayer.getBlockPos());
                    }

                    // 2) Hextech mode: compass points to stored block pos in stored dimension
                    if (nbt != null
                            && nbt.contains(RecoveryCompassHextechHandler.NBT_TARGET_POS)
                            && nbt.contains(RecoveryCompassHextechHandler.NBT_TARGET_DIM)) {

                        long packed = nbt.getLong(RecoveryCompassHextechHandler.NBT_TARGET_POS);
                        Identifier dimId = Identifier.tryParse(nbt.getString(RecoveryCompassHextechHandler.NBT_TARGET_DIM));
                        if (dimId == null) return null;

                        RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, dimId);

                        // If not in same dimension, spin
                        if (!world.getRegistryKey().equals(dimKey)) return null;

                        return GlobalPos.create(dimKey, BlockPos.fromLong(packed));
                    }

                    // 3) Vanilla fallback: last death position
                    if (entity instanceof PlayerEntity p) {
                        return p.getLastDeathPos().orElse(null);
                    }

                    return null;
                })
        );
    }
}
