package io.asinphi.npcpuppetmaster.gui;

import io.asinphi.npcpuppetmaster.action.Action;
import io.asinphi.payload.EntityActionPayload;
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription;
import io.github.cottonmc.cotton.gui.widget.WButton;
import io.github.cottonmc.cotton.gui.widget.WGridPanel;
import io.github.cottonmc.cotton.gui.widget.WLabel;
import io.github.cottonmc.cotton.gui.widget.WWidget;
import io.github.cottonmc.cotton.gui.widget.data.InputResult;
import io.github.cottonmc.cotton.gui.widget.data.Insets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;

public class NPCControlGui extends LightweightGuiDescription {
    private final LivingEntity entity;

    public NPCControlGui(LivingEntity targetEntity, boolean isFollowing, boolean isPossessing) {
        entity = targetEntity;

        // Root panel
        WGridPanel root = new WGridPanel();
        setRootPanel(root);
        root.setSize(200, 130);
        root.setInsets(Insets.ROOT_PANEL);

        WLabel label = new WLabel(Component.literal("Puppeteer: " + targetEntity.getName().getString()));
        root.add(label, 0, 0, 8, 1);

        // Back button to selector GUI
        WWidget backButton = new WWidget() {
            // Vanilla Sprite IDs for the Book arrow
            private final ResourceLocation IDLE = ResourceLocation.withDefaultNamespace("widget/page_backward");
            private final ResourceLocation HOVER = ResourceLocation.withDefaultNamespace("widget/page_backward_highlighted");

            @Override
            public void paint(GuiGraphics context, int x, int y, int mouseX, int mouseY) {
                boolean hovered = (mouseX >= 0 && mouseY >= 0 && mouseX < getWidth() && mouseY < getHeight());

                // The vanilla book arrow is 23x13 pixels
                int w = 23;
                int h = 13;

                int dx = x + (getWidth() - w) / 2;
                int dy = y + (getHeight() - h) / 2 - 5;

                // Render the sprite (No background!)
                context.blitSprite(hovered ? HOVER : IDLE, dx, dy, w, h);
            }

            @Override
            public InputResult onClick(int x, int y, int button) {
                if (button == 0) { // Left Click
                    // Play Click Sound
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));

                    // Action: Open Selector
                    assert Minecraft.getInstance().player != null;
                    Minecraft.getInstance().player.connection.sendCommand("puppeteer");
                    return InputResult.PROCESSED;
                }
                return InputResult.IGNORED;
            }
        };
        // Add to top-right (x=10), 1x1 slot
        root.add(backButton, 10, 0, 1, 1);

        WEntityDisplay entityWidget = new WEntityDisplay(targetEntity);
        root.add(entityWidget, 0, 1, 4, 5); // Position (0,1)

        // Control buttons
        if (isPossessing)
            addButton(root, "Stop possessing", 5, 1, Action.UNPOSSESS);
        else
            addButton(root, "Possess", 5, 1, Action.POSSESS);
        if (isFollowing)
            addButton(root, "Stop following", 5, 2, Action.UNFOLLOW);
        else
            addButton(root, "Follow me", 5, 2, Action.FOLLOW);

        addButton(root, "Come here", 5, 3, Action.MOVE_HERE);

        addButton(root, "Go away", 5, 4, Action.GO_AWAY);

        addButton(root, "Delete", 5, 5, Action.DELETE);

        root.validate(this);
    }

    private void addButton(WGridPanel root, String text, int x, int y, Action action) {
        WButton button = new WButton(Component.literal(text));
        button.setOnClick(() -> {
            sendAction(action);
            Minecraft.getInstance().setScreen(null);
        });
        root.add(button, x, y, 5, 1);
    }

    private void sendAction(Action action) {
        ClientPlayNetworking.send(new EntityActionPayload(entity.getId(), action));
    }
}