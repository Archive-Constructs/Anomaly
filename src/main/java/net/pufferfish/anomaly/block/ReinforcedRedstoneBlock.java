package net.pufferfish.anomaly.block;

import net.minecraft.block.MapColor;
import net.minecraft.block.RedstoneBlock;
import net.minecraft.sound.BlockSoundGroup;

public class ReinforcedRedstoneBlock extends RedstoneBlock {
    public ReinforcedRedstoneBlock(Settings settings) {
        super(settings);
    }

    /** Convenience factory with sane defaults. */
    public static ReinforcedRedstoneBlock create() {
        return new ReinforcedRedstoneBlock(
                Settings.create()
                        // hardness, resistance — 3.0F is redstone block-ish hardness;
                        // 3_600_000F is “bedrock” blast resistance (won’t be destroyed by explosions)
                        .strength(3.0F, 3_600_000.0F)
                        .mapColor(MapColor.BRIGHT_RED)
                        .sounds(BlockSoundGroup.METAL)
                        .requiresTool() // needs a pickaxe
        );
    }
}
