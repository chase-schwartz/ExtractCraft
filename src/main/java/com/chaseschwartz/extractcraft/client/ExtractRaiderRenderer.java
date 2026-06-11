package com.chaseschwartz.extractcraft.client;

import com.chaseschwartz.extractcraft.ai.ExtractRaiderEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class ExtractRaiderRenderer extends HumanoidMobRenderer<ExtractRaiderEntity, HumanoidModel<ExtractRaiderEntity>> {
    private static final ResourceLocation PLACEHOLDER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png");

    public ExtractRaiderRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(ExtractRaiderEntity entity) {
        return PLACEHOLDER_TEXTURE;
    }
}
