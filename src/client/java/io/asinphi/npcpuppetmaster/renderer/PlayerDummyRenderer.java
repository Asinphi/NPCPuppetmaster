package io.asinphi.npcpuppetmaster.renderer;

import io.asinphi.npcpuppetmaster.entity.PlayerDummyEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

public class PlayerDummyRenderer extends LivingEntityRenderer<PlayerDummyEntity, PlayerModel<PlayerDummyEntity>> {

    private final PlayerModel<PlayerDummyEntity> normalModel;
    private final PlayerModel<PlayerDummyEntity> slimModel;

    public PlayerDummyRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);

        this.normalModel = this.getModel(); // The one passed to super
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);

        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer(new ElytraLayer<>(this, context.getModelSet()));
    }

    @Override
    public void render(PlayerDummyEntity entity, float entityYaw, float partialTicks,
                       com.mojang.blaze3d.vertex.PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Determine if we should use the slim model or normal model
        boolean isSlim = false;
        Optional<UUID> ownerUuid = entity.getOwnerUUID();
        if (ownerUuid.isPresent()) {
            UUID id = ownerUuid.get();
            var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
            if (connection != null) {
                var info = connection.getPlayerInfo(id);
                if (info != null) {
                    isSlim = info.getSkin().model() == PlayerSkin.Model.SLIM;
                } else {
                    // Fallback to offline UUID calculation if player info is missing
                    isSlim = DefaultPlayerSkin.get(id).model() == PlayerSkin.Model.SLIM;
                }
            } else {
                isSlim = DefaultPlayerSkin.get(id).model() == PlayerSkin.Model.SLIM;
            }
        }
        this.model = isSlim ? this.slimModel : this.normalModel;

        // Force the model to be 'standing' (not sitting/crouching by default)
        this.model.setAllVisible(true);

        // Mimic standard Humanoid setup
        this.model.crouching = entity.isCrouching();
        HumanoidModel.ArmPose mainHandPose = getArmPose(entity, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose offHandPose = getArmPose(entity, InteractionHand.OFF_HAND);

        if (entity.getMainArm() == HumanoidArm.RIGHT) {
            this.model.rightArmPose = mainHandPose;
            this.model.leftArmPose = offHandPose;
        } else {
            this.model.rightArmPose = offHandPose;
            this.model.leftArmPose = mainHandPose;
        }

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private HumanoidModel.ArmPose getArmPose(PlayerDummyEntity entity, InteractionHand hand) {
        ItemStack itemStack = entity.getItemInHand(hand);
        if (itemStack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }

        return HumanoidModel.ArmPose.ITEM;
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerDummyEntity entity) {
        Optional<UUID> ownerUuid = entity.getOwnerUUID();
        if (ownerUuid.isPresent()) {
            UUID id = ownerUuid.get();
            var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
            if (connection != null) {
                var info = connection.getPlayerInfo(id);
                if (info != null) return info.getSkin().texture();
            }
            return DefaultPlayerSkin.get(id).texture();
        }
        return DefaultPlayerSkin.getDefaultTexture();
    }
}