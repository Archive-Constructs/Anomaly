package net.pufferfish.anomaly.item;

import java.util.List;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.pufferfish.anomaly.block.ModBlocks;

/**
 * Spire Mark:
 * - Shift-right-click a Hextech Spire to bind this item to that spire (stores dim + BlockPos in NBT).
 * - If a player carries a Spire Mark bound to a given spire, that spire won't target them.
 * - Shift-right-click in the air to clear/unbind.
 */
public class SpireMarkItem extends Item {

    public static final String NBT_DIM = "SpireDim";
    public static final String NBT_X   = "SpireX";
    public static final String NBT_Y   = "SpireY";
    public static final String NBT_Z   = "SpireZ";

    public SpireMarkItem(Settings settings) {
        super(settings);
    }


    /** 1.20.1-correct tooltip signature. */
    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> lines, TooltipContext ctx) {
        NbtCompound tag = stack.getNbt();
        if (tag != null && tag.contains(NBT_DIM)) {
            String dim = tag.getString(NBT_DIM);
            int x = tag.getInt(NBT_X);
            int y = tag.getInt(NBT_Y);
            int z = tag.getInt(NBT_Z);
            lines.add(Text.literal("Carry to be ignored by that spire").formatted(Formatting.AQUA));
        } else {
            lines.add(Text.literal("Right-Click a Hextech Spire").formatted(Formatting.DARK_GRAY));
        }
    }
}
