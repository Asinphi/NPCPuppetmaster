package io.asinphi.npcpuppetmaster.gui;

import de.markusbordihn.easynpc.data.display.DisplayAttributeType;
import de.markusbordihn.easynpc.data.display.NameVisibilityType;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.DisplayAttributeDataCapable;
import io.github.cottonmc.cotton.gui.widget.WWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A custom widget to render a LivingEntity.
 */
public class WEntityDisplay extends WWidget {
    private final LivingEntity entity;

    public WEntityDisplay(LivingEntity entity) {
        this.entity = entity;
        this.setSize(80, 90);
    }

    @Override
    public boolean canResize() {
        return true;
    }

    @Override
    public void paint(GuiGraphics context, int x, int y, int mouseX, int mouseY) {
        // Background and border
        context.fill(x + 1, y + 1, x + getWidth() - 1, y + getHeight() - 1, 0xFF000000);

        // Shadows (top and left) - dark gray
        int shadowColor = 0xFF373737;
        context.fill(x, y, x + getWidth() - 1, y + 1, shadowColor); // Top
        context.fill(x, y, x + 1, y + getHeight() - 1, shadowColor); // Left

        // Highlights (bottom and right) - white
        int highlightColor = 0xFFFFFFFF;
        context.fill(x, y + getHeight() - 1, x + getWidth(), y + getHeight(), highlightColor); // Bottom
        context.fill(x + getWidth() - 1, y, x + getWidth(), y + getHeight(), highlightColor);   // Right

        // Entity render position
        int scale = 30;
        int posX = x + getWidth() / 2;
        int posY = y + getHeight() - 10;

        // Calculate Look Logic using LOCAL Coordinates
        // mouseX/Y are already relative to the widget (0,0 is top-left of widget)
        // So we compare them to the widget's center (width/2), not the screen center.

        // Center of the entity relative to the widget
        float localCenterX = getWidth() / 2.0f;
        float localCenterY = getHeight() - 10.0f; // Matches the posY padding above
        float eyeOffset = 50.0f; // Vanilla standard eye height offset

        // Calculate the delta directly
        float f = localCenterX - mouseX;
        float g = (localCenterY - eyeOffset) - mouseY;

        // Standard Vanilla Rotation Math
        float originalBodyRot = entity.yBodyRot;
        float originalYRot = entity.getYRot();
        float originalXRot = entity.getXRot();
        float originalHeadRot = entity.yHeadRot;
        float originalHeadRotO = entity.yHeadRotO;

        Component originalName = entity.getCustomName();
        boolean originalNameVisible = entity.isCustomNameVisible();
        NameVisibilityType originalNameVisibility = null;

        try {
            // Hide nametag
            entity.setCustomName(null);
            entity.setCustomNameVisible(false);
            if (entity instanceof EasyNPC<?> npc) {
                DisplayAttributeDataCapable<?> displayData = npc.getEasyNPCDisplayAttributeData();
                if (displayData != null) {
                    originalNameVisibility = displayData.getDisplayEnumAttribute(
                            DisplayAttributeType.NAME_VISIBILITY, NameVisibilityType.class
                    );
                    displayData.setDisplayAttribute(
                            DisplayAttributeType.NAME_VISIBILITY, NameVisibilityType.NEVER
                    );
                }
            }

            // Vanilla uses atan to curve the rotation (clamping it naturally)
            float bodyRot = (float)Math.atan(f / 40.0F);
            float pitchRot = (float)Math.atan(g / 40.0F);

            entity.yBodyRot = 180.0F + (bodyRot * 20.0F);
            entity.setYRot(180.0F + (bodyRot * 40.0F));
            entity.setXRot(-pitchRot * 20.0F);
            entity.yHeadRot = entity.getYRot();
            entity.yHeadRotO = entity.getYRot();

            Quaternionf baseRotation = new Quaternionf().rotateZ((float)Math.PI);
            Quaternionf pitchRotation = new Quaternionf().rotateX(pitchRot * 20.0F * ((float)Math.PI / 180.0F));
            baseRotation.mul(pitchRotation);

            InventoryScreen.renderEntityInInventory(
                    context,
                    posX, posY,
                    scale,
                    new Vector3f(),
                    baseRotation,
                    null,
                    entity
            );
        } finally {
            entity.yBodyRot = originalBodyRot;
            entity.setYRot(originalYRot);
            entity.setXRot(originalXRot);
            entity.yHeadRot = originalHeadRot;
            entity.yHeadRotO = originalHeadRotO;

            entity.setCustomName(originalName);
            entity.setCustomNameVisible(originalNameVisible);

            if (entity instanceof EasyNPC<?> npc && originalNameVisibility != null) {
                DisplayAttributeDataCapable<?> displayData = npc.getEasyNPCDisplayAttributeData();
                if (displayData != null) {
                    displayData.setDisplayAttribute(
                            DisplayAttributeType.NAME_VISIBILITY, originalNameVisibility
                    );
                }
            }
        }
    }
}
