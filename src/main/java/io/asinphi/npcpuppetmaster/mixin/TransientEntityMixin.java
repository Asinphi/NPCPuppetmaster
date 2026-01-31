package io.asinphi.npcpuppetmaster.mixin;

import io.asinphi.npcpuppetmaster.attachment.ModAttachments;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class TransientEntityMixin {

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void npcpuppetmaster$preventSaving(CompoundTag compound, CallbackInfoReturnable<Boolean> cir) {
        if (isTransient()) {
            cir.setReturnValue(false);
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    @Unique
    public boolean isTransient() {
        return ((AttachmentTarget) this).getAttachedOrElse(ModAttachments.IS_TRANSIENT, false);
    }
}
