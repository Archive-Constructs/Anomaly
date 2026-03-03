package net.pufferfish.anomaly.item;

import net.pufferfish.anomaly.block.OverchargedTeleporterBlock;

public class NukeControllerItem extends BoundStrikeControllerItem {
    public NukeControllerItem(Settings settings) {
        super(settings, OverchargedTeleporterBlock.Mode.NUKE);
    }
}