package io.asinphi.npcpuppetmaster.gui;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import de.markusbordihn.easynpc.data.display.DisplayAttributeType;
import de.markusbordihn.easynpc.data.display.NameVisibilityType;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.DisplayAttributeDataCapable;
import io.asinphi.payload.OpenSelectorGuiPayload;
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription;
import io.github.cottonmc.cotton.gui.widget.*;
import io.github.cottonmc.cotton.gui.widget.data.InputResult;
import io.github.cottonmc.cotton.gui.widget.data.Insets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class SelectorGui extends LightweightGuiDescription {

    public SelectorGui(List<OpenSelectorGuiPayload.Candidate> candidates, int possessedEntityId) {
        WGridPanel root = new WGridPanel();
        setRootPanel(root);
        root.setSize(230, 200);
        root.setInsets(Insets.ROOT_PANEL);

        WLabel title = new WLabel(Component.literal("Select NPC"));
        root.add(title, 0, 0, 8, 1);

        WGridPanel listPanel = new WGridPanel();
        listPanel.setInsets(Insets.ROOT_PANEL);

        int row = 0;
        for (OpenSelectorGuiPayload.Candidate candidate : candidates) {
            boolean isPossessed = (candidate.entityId() == possessedEntityId);

            // Get entity
            assert Minecraft.getInstance().level != null;
            Entity entity = Minecraft.getInstance().level.getEntity(candidate.entityId());

            if (entity == null && !isPossessed) {
                // Skip entities that are not loaded and not possessed
                continue;
            }

            // Create the custom row widget
            WEntityRow entry = new WEntityRow(candidate.name(), entity, isPossessed,
                    !isPossessed ? () -> {
                        assert Minecraft.getInstance().player != null;
                        Minecraft.getInstance().player.connection.sendCommand("puppeteer " + candidate.uuid());
                    }: () -> {
                        assert Minecraft.getInstance().player != null;
                        Minecraft.getInstance().player.connection.sendCommand("unpossess");
                        Minecraft.getInstance().setScreen(null);
                    });

            // Add to list
            // We make it occupy the full width (e.g. 11 slots wide)
            // Height: 2 slots (roughly 36-40px)
            listPanel.add(entry, 0, row, 10, 2);

            row += 2; // Move down 2 slots for the next item
        }

        // If no NPCs were added to the list, show the help text
        if (row == 0) {
            WText helpText = new WText(Component.literal(
                    "Controllable NPCs you are looking at and NPCs you have previously controlled will show up here."));
            helpText.setColor(0xFF000000); // Light Gray
            listPanel.add(helpText, 0, 1, 10, 6);
        }

        WScrollPanel scrollPanel = new WScrollPanel(listPanel);
        root.add(scrollPanel, 0, 1, 12, 10);

        root.validate(this);
    }

    private static class WEntityRow extends WWidget {
        private final String name;
        private final LivingEntity entity;
        private final boolean isPossessed;
        private final Runnable onClick;

        public WEntityRow(String name, Entity entity, boolean isPossessed, Runnable onClick) {
            this.name = name;
            this.isPossessed = isPossessed;
            this.onClick = onClick;

            if (entity instanceof LivingEntity livingEntity) {
                this.entity = livingEntity;
            } else {
                this.entity = null;
            }
        }

        @Override
        public boolean canResize() {
            return true;
        }

        @Override
        public InputResult onClick(int x, int y, int button) {
            if (button == 0) {
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F
                        )
                );
                onClick.run();
                return InputResult.PROCESSED;
            }
            return InputResult.IGNORED;
        }

        @Override
        public void paint(GuiGraphics context, int x, int y, int mouseX, int mouseY) {
            // Full row background
            context.fill(x, y, x + getWidth(), y + getHeight(), isPossessed ? 0xFF66582C : 0xFF000000);

            int shadow = 0xFF373737;
            int highlight = 0xFFFFFFFF;

            // Borders
            context.fill(x, y, x + getWidth() - 1, y + 1, shadow);
            context.fill(x, y, x + 1, y + getHeight() - 1, shadow);
            context.fill(x, y + getHeight() - 1, x + getWidth(), y + getHeight(), highlight);
            context.fill(x + getWidth() - 1, y, x + getWidth(), y + getHeight(), highlight);

            // Hover
            if (mouseX >= 0 && mouseY >= 0 && mouseX < getWidth() && mouseY < getHeight()) {
                context.fill(x + 1, y + 1, x + getWidth() - 1, y + getHeight() - 1, 0x20FFFFFF);
            }

            int boxSize = getHeight();

            // Name text
            int textX = x + boxSize + 6;
            int textY = y + (getHeight() / 2) - 4;
            context.drawString(Minecraft.getInstance().font, name, textX, textY, 0xFFFFFF, true);

            if (isPossessed){
                String statusText = "Click to cancel possession";
                context.drawString(Minecraft.getInstance().font, statusText, textX,
                        textY + 10, 0xb677e0, true);
            }

            if (entity == null)
                return;

            // We wrap the entity render in our safe Scissor Helper
            runWithScissor(context, x + 1, y + 1, boxSize - 2, getHeight() - 2, () -> {
                renderEntityInternal(context, x, y, boxSize, mouseX, mouseY);
            });

            // Distance/possession status
            if (!isPossessed && Minecraft.getInstance().player != null) {
                int dist = (int) Minecraft.getInstance().player.distanceTo(entity);
                String distText = "(" + dist + "m)";
                int distWidth = Minecraft.getInstance().font.width(distText);
                context.drawString(Minecraft.getInstance().font, distText, x + getWidth() - distWidth - 5,
                        y + 5, 0xFFFF55, true);
            }
        }

        // This method handles the complex logic of "Safe Scissoring"
        private void runWithScissor(GuiGraphics context, int x, int y, int width, int height, Runnable renderAction) {
            Window window = Minecraft.getInstance().getWindow();
            double scale = window.getGuiScale();

            // Calculate the "Row Box" in physical pixels
            int rowLeft = (int)(x * scale);
            int rowBottom = (int)((window.getGuiScaledHeight() - (y + height)) * scale);
            int rowWidth = (int)(width * scale);
            int rowHeight = (int)(height * scale);

            // Get the "Scroll Panel" (Current) Scissor
            int[] oldScissor = new int[4];
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, oldScissor);

            // Calculate Intersection
            // If a scissor is already active (the scroll panel), we must stay inside it.
            int finalX = rowLeft;
            int finalY = rowBottom;
            int finalW = rowWidth;
            int finalH = rowHeight;

            if (oldScissor[2] > 0 && oldScissor[3] > 0) {
                int oldLeft = oldScissor[0];
                int oldBottom = oldScissor[1];
                int oldWidth = oldScissor[2];
                int oldHeight = oldScissor[3];

                // Intersect X
                int maxLeft = Math.max(rowLeft, oldLeft);
                int minRight = Math.min(rowLeft + rowWidth, oldLeft + oldWidth);
                finalX = maxLeft;
                finalW = Math.max(0, minRight - maxLeft);

                // Intersect Y
                int maxBottom = Math.max(rowBottom, oldBottom);
                int minTop = Math.min(rowBottom + rowHeight, oldBottom + oldHeight);
                finalY = maxBottom;
                finalH = Math.max(0, minTop - maxBottom);
            }

            // If the intersection is empty (row is effectively hidden), skip rendering
            if (finalW <= 0 || finalH <= 0) {
                return;
            }

            // Apply the intersected scissor
            RenderSystem.enableScissor(finalX, finalY, finalW, finalH);

            try {
                renderAction.run();
            } finally {
                // Restore the Scroll Panel's original scissor
                if (oldScissor[2] > 0 && oldScissor[3] > 0) {
                    RenderSystem.enableScissor(oldScissor[0], oldScissor[1], oldScissor[2], oldScissor[3]);
                } else {
                    RenderSystem.disableScissor();
                }
            }
        }

        private void renderEntityInternal(GuiGraphics context, int x, int y, int boxSize, int mouseX, int mouseY) {
            int scale = 35;
            float localEyeTargetY = boxSize * 0.4f;
            int posX = x + boxSize / 2;
            int posY = (int)(y + localEyeTargetY + (entity.getEyeHeight() * scale));

            float localCenterX = boxSize / 2.0f;
            float f = localCenterX - mouseX;
            float g = localEyeTargetY - mouseY;

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
                        context, posX, posY, scale, new Vector3f(), baseRotation, null, entity
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
}
