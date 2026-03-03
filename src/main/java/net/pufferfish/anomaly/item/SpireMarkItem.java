package net.pufferfish.anomaly.item;

import java.util.List;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class SpireMarkItem extends Item {

    public static final String NBT_BINDS = "SpireBinds";
    public static final int MAX_BINDS = 16;

    public static final String KEY_DIM = "Dim";
    public static final String KEY_X   = "X";
    public static final String KEY_Y   = "Y";
    public static final String KEY_Z   = "Z";

    public SpireMarkItem(Settings settings) {
        super(settings);
    }

    public static NbtList getOrCreateBinds(ItemStack stack) {
        NbtCompound tag = stack.getOrCreateNbt();
        if (!tag.contains(NBT_BINDS, NbtElement.LIST_TYPE)) {
            tag.put(NBT_BINDS, new NbtList());
        }
        return tag.getList(NBT_BINDS, NbtElement.COMPOUND_TYPE);
    }

    /** @return true if added (or already present), false if list is full */
    public static boolean addBind(ItemStack stack, String dimId, int x, int y, int z) {
        NbtList list = getOrCreateBinds(stack);

        // Already present -> ok
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            if (dimId.equals(c.getString(KEY_DIM))
                    && x == c.getInt(KEY_X)
                    && y == c.getInt(KEY_Y)
                    && z == c.getInt(KEY_Z)) {
                return true;
            }
        }

        // hard cap
        if (list.size() >= MAX_BINDS) return false;

        NbtCompound c = new NbtCompound();
        c.putString(KEY_DIM, dimId);
        c.putInt(KEY_X, x);
        c.putInt(KEY_Y, y);
        c.putInt(KEY_Z, z);
        list.add(c);

        return true;
    }

    /** @return removed bind compound, or null if none */
    public static NbtCompound popLastBind(ItemStack stack) {
        NbtCompound tag = stack.getNbt();
        if (tag == null || !tag.contains(NBT_BINDS, NbtElement.LIST_TYPE)) return null;

        NbtList list = tag.getList(NBT_BINDS, NbtElement.COMPOUND_TYPE);
        if (list.isEmpty()) return null;

        int last = list.size() - 1;
        NbtCompound removed = list.getCompound(last).copy();
        list.remove(last);

        // cleanup if empty
        if (list.isEmpty()) tag.remove(NBT_BINDS);

        return removed;
    }

    public static int getBindCount(ItemStack stack) {
        NbtCompound tag = stack.getNbt();
        if (tag == null || !tag.contains(NBT_BINDS, NbtElement.LIST_TYPE)) return 0;
        return tag.getList(NBT_BINDS, NbtElement.COMPOUND_TYPE).size();
    }

    /** Shift-right-click in air: remove last bound spire */
    @Override
    public TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!user.isSneaking()) {
            return TypedActionResult.pass(stack);
        }

        if (world.isClient) {
            return TypedActionResult.success(stack);
        }

        NbtCompound removed = popLastBind(stack);
        if (removed == null) {
            user.sendMessage(Text.literal("No spires bound.").formatted(Formatting.RED), true);
            return TypedActionResult.fail(stack);
        }

        String dim = removed.getString(KEY_DIM);
        int x = removed.getInt(KEY_X);
        int y = removed.getInt(KEY_Y);
        int z = removed.getInt(KEY_Z);

        String dimShort = dim;
        int colon = dim.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < dim.length()) dimShort = dim.substring(colon + 1);

        user.sendMessage(
                Text.literal("Removed: " + dimShort + " @ " + x + " " + y + " " + z)
                        .formatted(Formatting.YELLOW),
                true
        );

        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> lines, TooltipContext ctx) {
        lines.add(Text.literal("Carry to be ignored by bound spires.").formatted(Formatting.AQUA));

        NbtCompound tag = stack.getNbt();
        if (tag == null || !tag.contains(NBT_BINDS, NbtElement.LIST_TYPE)) {
            lines.add(Text.literal("Right-Click a Hextech Spire").formatted(Formatting.DARK_GRAY));
            lines.add(Text.literal("Shift+Right-Click (air) to remove last bound").formatted(Formatting.GRAY));
            return;
        }

        NbtList list = tag.getList(NBT_BINDS, NbtElement.COMPOUND_TYPE);
        if (list.isEmpty()) {
            lines.add(Text.literal("Right-Click a Hextech Spire").formatted(Formatting.DARK_GRAY));
            lines.add(Text.literal("Shift+Right-Click (air) to remove last bound").formatted(Formatting.GRAY));
            return;
        }

        lines.add(Text.literal("Bound spires (" + list.size() + "/" + MAX_BINDS + "):").formatted(Formatting.GRAY));

        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            String dim = c.getString(KEY_DIM);
            int x = c.getInt(KEY_X);
            int y = c.getInt(KEY_Y);
            int z = c.getInt(KEY_Z);

            String dimShort = dim;
            int colon = dim.lastIndexOf(':');
            if (colon >= 0 && colon + 1 < dim.length()) dimShort = dim.substring(colon + 1);

            lines.add(Text.literal("• " + dimShort + " @ " + x + " " + y + " " + z)
                    .formatted(Formatting.DARK_AQUA));
        }

        lines.add(Text.literal("Shift+Right-Click (air) to remove last bound").formatted(Formatting.GRAY));
    }
}