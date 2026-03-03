package net.pufferfish.anomaly.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.pufferfish.anomaly.block.OverchargedTeleporterBlock;

public class BoundStrikeControllerItem extends Item {
    private static final String NBT_DIM = "bound_dim";
    private static final String NBT_X = "bound_x";
    private static final String NBT_Y = "bound_y";
    private static final String NBT_Z = "bound_z";

    private final OverchargedTeleporterBlock.Mode mode;

    public BoundStrikeControllerItem(Settings settings, OverchargedTeleporterBlock.Mode mode) {
        super(settings);
        this.mode = mode;
    }

    /**
     * Sneak-right-click an Overcharged Teleporter to bind.
     */
    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World w = ctx.getWorld();
        if (w.isClient) return ActionResult.SUCCESS;

        var player = ctx.getPlayer();
        if (player == null) return ActionResult.PASS;

        if (!player.isSneaking()) return ActionResult.PASS;

        BlockPos clicked = ctx.getBlockPos();
        if (!(w.getBlockState(clicked).getBlock() instanceof OverchargedTeleporterBlock)) {
            player.sendMessage(Text.literal("Not an Overcharged Teleporter."), true);
            return ActionResult.PASS;
        }

        ItemStack stack = ctx.getStack();
        NbtCompound nbt = stack.getOrCreateNbt();

        // Store dimension + pos
        nbt.putString(NBT_DIM, ((ServerWorld) w).getRegistryKey().getValue().toString());
        nbt.putInt(NBT_X, clicked.getX());
        nbt.putInt(NBT_Y, clicked.getY());
        nbt.putInt(NBT_Z, clicked.getZ());

        player.sendMessage(Text.literal("Controller bound to Overcharged Teleporter at " + clicked), true);
        return ActionResult.SUCCESS;
    }

    /**
     * Right-click to fire at the block you are looking at.
     * Consumes 1 item on success.
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);

        ServerWorld sw = (ServerWorld) world;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_DIM) || !nbt.contains(NBT_X) || !nbt.contains(NBT_Y) || !nbt.contains(NBT_Z)) {
            user.sendMessage(Text.literal("This controller is not bound. Sneak-right-click an Overcharged Teleporter to bind."), true);
            return TypedActionResult.fail(stack);
        }

        // Check dimension match
        String dimStr = nbt.getString(NBT_DIM);
        RegistryKey<World> here = sw.getRegistryKey();
        if (!here.getValue().toString().equals(dimStr)) {
            user.sendMessage(Text.literal("Bound teleporter is in a different dimension."), true);
            return TypedActionResult.fail(stack);
        }

        BlockPos telePos = new BlockPos(nbt.getInt(NBT_X), nbt.getInt(NBT_Y), nbt.getInt(NBT_Z));

        // Raycast what block the user is looking at
        HitResult hit = user.raycast(1_000_000.0, 0.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            user.sendMessage(Text.literal("Look at a block to strike."), true);
            return TypedActionResult.fail(stack);
        }

        BlockPos target = ((net.minecraft.util.hit.BlockHitResult) hit).getBlockPos();

        boolean ok = OverchargedTeleporterBlock.triggerStrikeFromItem(sw, telePos, mode, target);
        if (!ok) {
            user.sendMessage(Text.literal("Strike failed (teleporter missing, unpowered, wrong mesh count, or on cooldown)."), true);
            return TypedActionResult.fail(stack);
        }

// Play break sound
        sw.playSound(
                null,
                user.getBlockPos(),
                SoundEvents.ENTITY_ITEM_BREAK,
                SoundCategory.PLAYERS,
                1.5f,
                1.0f
        );

// Consume controller
        stack.decrement(1);

        return TypedActionResult.success(stack);
    }
}