package com.safeanchor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

/**
 * Hotbar / inventory helpers. Never assumes fixed slots; every lookup scans
 * the actual inventory content.
 */
public final class SafeAnchorInventory {

    private SafeAnchorInventory() {
    }

    /**
     * @return hotbar slot (0-8) containing {@code item}, or -1.
     */
    public static int findItemInHotbar(Item item) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || item == null) {
            return -1;
        }
        PlayerInventory inventory = client.player.getInventory();
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && stack.isOf(item)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * @return main-inventory slot containing {@code item}, or -1.
     */
    public static int findItemAnywhere(Item item) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || item == null) {
            return -1;
        }
        PlayerInventory inventory = client.player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && stack.isOf(item)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * @return true when {@code item} exists anywhere (main inventory or offhand).
     */
    public static boolean hasItem(Item item) {
        return findItemAnywhere(item) >= 0 || isInOffhand(item);
    }

    /**
     * @return true when {@code item} is held in the offhand.
     */
    public static boolean isInOffhand(Item item) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || item == null) {
            return false;
        }
        ItemStack offhand = client.player.getEquippedStack(EquipmentSlot.OFFHAND);
        return !offhand.isEmpty() && offhand.isOf(item);
    }

    /**
     * @return true when the given hotbar slot currently holds {@code item}.
     */
    public static boolean slotHasItem(int slot, Item item) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || item == null
                || !PlayerInventory.isValidHotbarIndex(slot)) {
            return false;
        }
        ItemStack stack = client.player.getInventory().getStack(slot);
        return !stack.isEmpty() && stack.isOf(item);
    }

    /**
     * @return first empty hotbar slot (0-8), or -1 when the hotbar is full.
     */
    public static int findEmptyHotbarSlot() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return -1;
        }
        PlayerInventory inventory = client.player.getInventory();
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            if (inventory.getStack(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    public static int getSelectedSlot() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return -1;
        }
        return client.player.getInventory().getSelectedSlot();
    }

    /**
     * Selects a hotbar slot client-side and informs the server.
     *
     * @return true when the switch was performed.
     */
    public static boolean switchToSlot(int slot) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !PlayerInventory.isValidHotbarIndex(slot)) {
            return false;
        }
        client.player.getInventory().setSelectedSlot(slot);
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
        return true;
    }
}
