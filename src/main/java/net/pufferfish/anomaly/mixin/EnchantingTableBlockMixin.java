package net.pufferfish.anomaly.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.pufferfish.anomaly.entity.ModEntities;
import net.pufferfish.anomaly.entity.WildRuneEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantingTableBlock.class)
public class EnchantingTableBlockMixin {

    // Inject after vanilla handles opening the screen; we then (maybe) spawn the rune.
    @Inject(method = "onUse", at = @At("TAIL"), cancellable = false)
    private void anomaly$spawnWildRune(BlockState state, World world, BlockPos pos,
                                       PlayerEntity player, Hand hand, BlockHitResult hit,
                                       CallbackInfoReturnable<ActionResult> cir) {
        if (world.isClient) return;

        // ~3% chance when used; tweak as you like
        if (((ServerWorld) world).random.nextFloat() > 0.01f) return;

        // spawn one block above the table, centered
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 2.5;
        double z = pos.getZ() + 0.5;

        WildRuneEntity e = new WildRuneEntity(ModEntities.WILD_RUNE, world);
        e.setPos(x, y, z);
        world.spawnEntity(e);
    }
}
