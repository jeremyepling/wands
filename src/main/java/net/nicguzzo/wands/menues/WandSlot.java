package net.nicguzzo.wands.menues;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

class WandSlot extends Slot {
    WandSlot(Container container, int index, int x, int y) {
        //? if >=26.4 {
        /*super(container, index, x, y, container instanceof Inventory
                ? (index < 9 ? Section.HOTBAR : Section.INVENTORY)
                : Section.CONTAINER);
        *///? } else {
        super(container, index, x, y);
        //? }
    }
}
