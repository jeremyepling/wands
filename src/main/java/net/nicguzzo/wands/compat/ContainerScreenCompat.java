//?if >= 26.4-pre-2 {
/*package net.nicguzzo.wands.compat;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.inventory.SlotGroup;
import net.minecraft.client.gui.components.inventory.SlotWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

public final class ContainerScreenCompat {
    private ContainerScreenCompat() {}

    public static SlotWidget getSlotWidget(AbstractContainerScreen<?> screen, Slot slot) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof SlotGroup group) {
                for (SlotWidget widget : group.children()) {
                    if (widget.slot() == slot) {
                        return widget;
                    }
                }
            }
        }
        return null;
    }
}
*///?}
