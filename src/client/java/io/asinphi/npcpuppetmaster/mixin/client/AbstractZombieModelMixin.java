package io.asinphi.npcpuppetmaster.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(net.minecraft.client.model.AbstractZombieModel.class)
public abstract class AbstractZombieModelMixin<T extends net.minecraft.world.entity.monster.Monster>
        extends net.minecraft.client.model.HumanoidModel<T> {

    // Mixin needs a matching ctor signature for the superclass
    protected AbstractZombieModelMixin(net.minecraft.client.model.geom.ModelPart root) {
        super(root);
    }

    @WrapWithCondition(
            method = "setupAnim(Lnet/minecraft/world/entity/monster/Monster;FFFFF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/AnimationUtils;animateZombieArms(Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;ZFF)V")
    )
    private boolean npcavatar$cancelZombieArms(ModelPart leftArm, ModelPart rightArm, boolean isAggressive, float attackTime, float ageInTicks, T entity) {
        // The 'entity' is usually passed as the last argument in MixinExtras if it's the method parameter
        return !shouldCancelZombieArms(entity);
    }

    @Unique
    private boolean shouldCancelZombieArms(T entity) {
//        return entity.getTags().contains("npcpuppetmaster:player_arms");
        return false;
    }
}