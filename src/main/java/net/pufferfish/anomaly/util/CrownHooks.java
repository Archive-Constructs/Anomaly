package net.pufferfish.anomaly.util;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import net.pufferfish.anomaly.item.ModItems;

public final class CrownHooks {
    private CrownHooks(){}

    public static boolean isWearingCrown(LivingEntity e) {
        ItemStack head = e.getEquippedStack(EquipmentSlot.HEAD);
        return !head.isEmpty() && head.isOf(ModItems.RUNEFORGED_CROWN);
    }

    /** Pop the spire beam with a green particle burst and return true (handled). */
    public static boolean popSpireBeam(ServerWorld world, LivingEntity target) {
        Vec3d p = target.getPos().add(0, target.getHeight() * 0.6, 0);
        // bright green dust
        DustParticleEffect GREEN = new DustParticleEffect(new Vector3f(0.1f, 1.0f, 0.2f), 1.0f);
        world.spawnParticles(GREEN, p.x, p.y, p.z, 80, 0.35, 0.35, 0.35, 0.02);
        return true;
    }
}
