package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.entity.RedDiamondSwordProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ExampleMod.RED_DIAMOND_SWORD_PROJECTILE.get(), RedDiamondSwordProjectileRenderer::new);
    }

    public static class RedDiamondSwordProjectileRenderer extends EntityRenderer<RedDiamondSwordProjectile> {

        public RedDiamondSwordProjectileRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public void render(RedDiamondSwordProjectile entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
            poseStack.pushPose();
            
            // Rotate the sword based on movement
            float ageInTicks = entity.tickCount + partialTicks;
            
            // Spin the sword
            poseStack.mulPose(Axis.YP.rotationDegrees(ageInTicks * 20.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(ageInTicks * 10.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(ageInTicks * 5.0F));
            
            // Center the sword
            poseStack.translate(-0.5, -0.5, -0.5);
            poseStack.scale(1.5F, 1.5F, 1.5F);
            
            // Render the sword item
            var itemRenderer = net.minecraft.client.Minecraft.getInstance().getItemRenderer();
            itemRenderer.renderStatic(entity.itemStack, ItemDisplayContext.GROUND, packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());
            
            poseStack.popPose();
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        }
    }
}