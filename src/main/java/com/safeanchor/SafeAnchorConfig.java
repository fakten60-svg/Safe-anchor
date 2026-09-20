package com.safeanchor;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * User configuration for SafeAnchor.
 *
 * <p>Persisted as JSON via {@link SafeAnchorConfigManager}. All values are
 * validated; invalid values are repaired with safe defaults.
 */
public class SafeAnchorConfig {

    public static final int DEFAULT_MIN_DELAY_MS = 60;
    public static final int DEFAULT_MAX_DELAY_MS = 70;
    public static final int MAX_ALLOWED_DELAY_MS = 5000;
    public static final String DEFAULT_PROTECTION_BLOCK_ID = "minecraft:obsidian";
    public static final String DEFAULT_SAFETY_ITEM_ID = "minecraft:totem_of_undying";
    public static final int DEFAULT_MAX_SEQUENCE_DURATION_MS = 1000;
    public static final int MIN_SEQUENCE_DURATION_MS = 250;
    public static final int MAX_SEQUENCE_DURATION_MS = 10000;

    /** Slot value meaning "find the item automatically in the hotbar". */
    public static final int AUTO_SLOT = -1;

    private boolean enabled = true;
    private int minDelayMs = DEFAULT_MIN_DELAY_MS;
    private int maxDelayMs = DEFAULT_MAX_DELAY_MS;
    private String protectionBlockId = DEFAULT_PROTECTION_BLOCK_ID;
    private boolean requireProtection = true;
    private String safetyItemId = DEFAULT_SAFETY_ITEM_ID;
    private boolean showHud = true;
    private boolean cancelOnDamage = false;
    private int maximumSequenceDurationMs = DEFAULT_MAX_SEQUENCE_DURATION_MS;
    private boolean debugLogging = false;
    private int anchorSlot = AUTO_SLOT;
    private int glowstoneSlot = AUTO_SLOT;
    private int protectionBlockSlot = AUTO_SLOT;
    private int safetyItemSlot = AUTO_SLOT;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMinDelayMs() {
        return minDelayMs;
    }

    public void setMinDelayMs(int minDelayMs) {
        this.minDelayMs = minDelayMs;
    }

    public int getMaxDelayMs() {
        return maxDelayMs;
    }

    public void setMaxDelayMs(int maxDelayMs) {
        this.maxDelayMs = maxDelayMs;
    }

    public String getProtectionBlockId() {
        return protectionBlockId;
    }

    public void setProtectionBlockId(String protectionBlockId) {
        this.protectionBlockId = protectionBlockId;
    }

    public boolean isRequireProtection() {
        return requireProtection;
    }

    public void setRequireProtection(boolean requireProtection) {
        this.requireProtection = requireProtection;
    }

    public String getSafetyItemId() {
        return safetyItemId;
    }

    public void setSafetyItemId(String safetyItemId) {
        this.safetyItemId = safetyItemId;
    }

    public boolean isShowHud() {
        return showHud;
    }

    public void setShowHud(boolean showHud) {
        this.showHud = showHud;
    }

    public boolean isCancelOnDamage() {
        return cancelOnDamage;
    }

    public void setCancelOnDamage(boolean cancelOnDamage) {
        this.cancelOnDamage = cancelOnDamage;
    }

    public int getMaximumSequenceDurationMs() {
        return maximumSequenceDurationMs;
    }

    public void setMaximumSequenceDurationMs(int maximumSequenceDurationMs) {
        this.maximumSequenceDurationMs = maximumSequenceDurationMs;
    }

    public boolean isDebugLogging() {
        return debugLogging;
    }

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    public int getAnchorSlot() {
        return anchorSlot;
    }

    public void setAnchorSlot(int anchorSlot) {
        this.anchorSlot = anchorSlot;
    }

    public int getGlowstoneSlot() {
        return glowstoneSlot;
    }

    public void setGlowstoneSlot(int glowstoneSlot) {
        this.glowstoneSlot = glowstoneSlot;
    }

    public int getProtectionBlockSlot() {
        return protectionBlockSlot;
    }

    public void setProtectionBlockSlot(int protectionBlockSlot) {
        this.protectionBlockSlot = protectionBlockSlot;
    }

    public int getSafetyItemSlot() {
        return safetyItemSlot;
    }

    public void setSafetyItemSlot(int safetyItemSlot) {
        this.safetyItemSlot = safetyItemSlot;
    }

    /**
     * Resolves the configured protection block id to a real block.
     *
     * @return the configured block, or obsidian as a safe fallback.
     */
    public Block resolveProtectionBlock() {
        try {
            if (protectionBlockId != null) {
                Identifier id = Identifier.of(protectionBlockId);
                Block block = Registries.BLOCK.get(id);
                if (block != null && block != Blocks.AIR && Registries.BLOCK.getId(block).equals(id)) {
                    return block;
                }
            }
        } catch (Exception ignored) {
            // Fall through to the safe default below.
        }
        return Blocks.OBSIDIAN;
    }

    /**
     * Resolves the configured safety item id to a real item.
     *
     * @return the configured item, or totem of undying as a safe fallback.
     */
    public Item resolveSafetyItem() {
        try {
            if (safetyItemId != null) {
                Identifier id = Identifier.of(safetyItemId);
                Item item = Registries.ITEM.get(id);
                if (item != null && item != Items.AIR && Registries.ITEM.getId(item).equals(id)) {
                    return item;
                }
            }
        } catch (Exception ignored) {
            // Fall through to the safe default below.
        }
        return Items.TOTEM_OF_UNDYING;
    }

    /**
     * Validates every value and repairs invalid ones with safe defaults.
     *
     * @return true if at least one value was repaired.
     */
    public boolean validateAndRepair() {
        boolean repaired = false;

        if (minDelayMs < 0 || minDelayMs > MAX_ALLOWED_DELAY_MS) {
            minDelayMs = DEFAULT_MIN_DELAY_MS;
            repaired = true;
        }
        if (maxDelayMs < minDelayMs || maxDelayMs > MAX_ALLOWED_DELAY_MS) {
            maxDelayMs = Math.max(minDelayMs, DEFAULT_MAX_DELAY_MS);
            if (maxDelayMs > MAX_ALLOWED_DELAY_MS) {
                maxDelayMs = MAX_ALLOWED_DELAY_MS;
            }
            repaired = true;
        }
        if (maximumSequenceDurationMs < MIN_SEQUENCE_DURATION_MS
                || maximumSequenceDurationMs > MAX_SEQUENCE_DURATION_MS) {
            maximumSequenceDurationMs = DEFAULT_MAX_SEQUENCE_DURATION_MS;
            repaired = true;
        }
        if (!resolveProtectionBlockIdMatches()) {
            protectionBlockId = DEFAULT_PROTECTION_BLOCK_ID;
            repaired = true;
        }
        if (!resolveSafetyItemIdMatches()) {
            safetyItemId = DEFAULT_SAFETY_ITEM_ID;
            repaired = true;
        }
        if (!isValidSlot(anchorSlot)) {
            anchorSlot = AUTO_SLOT;
            repaired = true;
        }
        if (!isValidSlot(glowstoneSlot)) {
            glowstoneSlot = AUTO_SLOT;
            repaired = true;
        }
        if (!isValidSlot(protectionBlockSlot)) {
            protectionBlockSlot = AUTO_SLOT;
            repaired = true;
        }
        if (!isValidSlot(safetyItemSlot)) {
            safetyItemSlot = AUTO_SLOT;
            repaired = true;
        }
        return repaired;
    }

    private boolean resolveProtectionBlockIdMatches() {
        try {
            return protectionBlockId != null
                    && Registries.BLOCK.getId(resolveProtectionBlock()).toString().equals(protectionBlockId);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean resolveSafetyItemIdMatches() {
        try {
            return safetyItemId != null
                    && Registries.ITEM.getId(resolveSafetyItem()).toString().equals(safetyItemId);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isValidSlot(int slot) {
        return slot == AUTO_SLOT || (slot >= 0 && slot <= 8);
    }
}
