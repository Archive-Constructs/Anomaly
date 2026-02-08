package net.pufferfish.anomaly.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.pufferfish.anomaly.util.CrownHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow public abstract boolean isAlive();

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void anomaly$blockMagicWhenCrowned(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.isAlive()) return;

        // Only act if wearing the Runeforged Crown
        if (!CrownHooks.isWearingCrown(self)) return;

        // Vanilla "magic" families on 1.20.1
        boolean isMagic = source.isOf(DamageTypes.MAGIC) || source.isOf(DamageTypes.INDIRECT_MAGIC);
        // If your spire uses a custom DamageType, add it here:
        // isMagic = isMagic || source.isOf(YourRegistry.HEXTECH_MAGIC);

        if (isMagic) {
            if (self.getWorld() instanceof ServerWorld sw) {
                CrownHooks.popSpireBeam(sw, self); // green burst
            }
            // Cancel the hit entirely
            cir.setReturnValue(false);
        }
    }
}
