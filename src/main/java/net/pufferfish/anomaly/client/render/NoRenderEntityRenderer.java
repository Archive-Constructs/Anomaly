package net.pufferfish.anomaly.client.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

public class NoRenderEntityRenderer<T extends Entity> extends EntityRenderer<T> {
    public NoRenderEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(T entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        // render nothing (particles are handled by the entity tick)
    }

    @Override
    public Identifier getTexture(T entity) {
        return null;
    }
}