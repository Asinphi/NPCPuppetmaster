package io.asinphi.npcpuppetmaster.keybinds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {

    public static KeyMapping openSelector;

    public static void init() {
        // Register the Key (Default: V)
        openSelector = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.npcp.open_selector",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.npcp.title"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSelector.consumeClick()) {
                if (client.player != null) {
                    client.player.connection.sendCommand("puppeteer");
                }
            }
        });
    }
}
