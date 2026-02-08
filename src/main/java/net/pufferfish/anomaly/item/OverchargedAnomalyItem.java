package net.pufferfish.anomaly.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;

import net.pufferfish.anomaly.entity.ModEntities;
import net.pufferfish.anomaly.entity.OverchargedAnomalyEntity;

import java.util.List;

public class OverchargedAnomalyItem extends Item {
    public OverchargedAnomalyItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> lines, TooltipContext ctx) {
        lines.add(Text.literal("The perfect intersection of order and chaos").formatted(Formatting.GOLD, Formatting.ITALIC, Formatting.OBFUSCATED));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            // spawn the anomaly entity in front of the player
            Vec3d look = user.getRotationVec(1.0f).normalize();
            Vec3d spawnPos = user.getEyePos().add(look.multiply(1.5));

            OverchargedAnomalyEntity anomaly = new OverchargedAnomalyEntity(ModEntities.OVERCHARGED_ANOMALY, world);
            anomaly.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
            anomaly.setOwner(user.getUuid()); // Optionally bind to the player

            world.spawnEntity(anomaly);

            // Decrease stack by 1 (if not in creative mode)
            if (!user.getAbilities().creativeMode) {
                stack.decrement(1);
            }
        }
        return TypedActionResult.success(stack, world.isClient());
    }
}
