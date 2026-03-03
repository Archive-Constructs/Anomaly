package net.pufferfish.anomaly.item;

import net.pufferfish.anomaly.block.OverchargedTeleporterBlock;

public class StabshotControllerItem extends BoundStrikeControllerItem {
    public StabshotControllerItem(Settings settings) {
        super(settings, OverchargedTeleporterBlock.Mode.STABSHOT);
    }
}