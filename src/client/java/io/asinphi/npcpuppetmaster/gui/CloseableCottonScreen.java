package io.asinphi.npcpuppetmaster.gui;

import io.github.cottonmc.cotton.gui.client.CottonClientScreen;
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription;

/**
 * A Cotton Screen that can be closed with the Inventory key (default: E).
 */
public class CloseableCottonScreen extends CottonClientScreen {

    public CloseableCottonScreen(LightweightGuiDescription description) {
        super(description);
    }

    @Override
    public boolean keyPressed(int ch, int keyCode, int modifiers) {
        // Let the widgets handle the key first (e.g. typing in a text box)
        if (super.keyPressed(ch, keyCode, modifiers)) {
            return true;
        }

        // If no widget consumed it, check if it's the Inventory Key (E)
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(ch, keyCode)) {
            this.onClose();
            return true;
        }

        return false;
    }
}
