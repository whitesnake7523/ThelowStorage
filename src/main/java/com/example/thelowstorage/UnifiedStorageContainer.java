package com.example.thelowstorage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class UnifiedStorageContainer extends Container {
    public UnifiedStorageContainer(IInventory playerInventory, int screenHeight) {
        // プレイヤーインベントリを画面下端から逆算して配置 (80px〜100px程度の高さを確保)
        int startY = screenHeight - 82;
        int hotbarY = screenHeight - 24;

        // メインインベントリ (9x3)
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlotToContainer(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, startY + i * 18));
            }
        }
        // ホットバー
        for (int i = 0; i < 9; ++i) {
            this.addSlotToContainer(new Slot(playerInventory, i, 8 + i * 18, hotbarY));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) { return true; }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack itemstack = null;
        Slot slot = (Slot)this.inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack itemstack1 = slot.getStack();
            itemstack = itemstack1.copy();
            if (index < 27) {
                if (!this.mergeItemStack(itemstack1, 27, 36, false)) return null;
            } else if (!this.mergeItemStack(itemstack1, 0, 27, false)) return null;
            if (itemstack1.stackSize == 0) slot.putStack(null);
            else slot.onSlotChanged();
        }
        return itemstack;
    }
}