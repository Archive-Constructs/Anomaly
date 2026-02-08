package net.pufferfish.anomaly;

import net.minecraft.util.Identifier;

public final class Ids {
    public static Identifier id(String path) {
        return new Identifier(Anomaly.MOD_ID, path);
    }
}
