package com.safeanchor;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * State machine for the one-shot SafeAnchor sequence.
 *
 * <p>All methods run on the Minecraft client thread (driven by
 * {@link SafeAnchorClient}'s end-tick handler). Each tick performs only cheap
 * checks; actions run only after their random delay elapsed.
 */
public class SafeAnchorController {

    private static final long IDLE_MESSAGE_TTL_NANOS = 2_500_000_000L;

    private SafeAnchorState state = SafeAnchorState.IDLE;
    private boolean active = false;
    private final SafeAnchorDelayManager delayManager = new SafeAnchorDelayManager();

    private long sequenceStartNanos;
    private float startHealth;
    private ClientWorld sequenceWorld;
    private ClientPlayerEntity sequencePlayer;

    private SafeAnchorActionExecutor.AimTarget aimTarget;
    private BlockPos anchorPos;
    private BlockPos protectionPos;

    private int anchorSlot = -1;
    private int glowstoneSlot = -1;
    private int protectionSlot = -1;
    private int safetySlot = -1;
    private int detonationSlot = -1;
    private boolean safetyInOffhand;

    private Block protectionBlock;
    private Item safetyItem;

    private String lastResultMessage;
    private long lastResultTimeNanos;

    /**
     * Starts the sequence once. Ignored when a sequence is already running,
     * so the activation hotkey can never stack two sequences.
     */
    public void startSequence() {
        if (state != SafeAnchorState.IDLE) {
            return;
        }
        SafeAnchorConfig config = SafeAnchorConfigManager.get();
        if (!config.isEnabled()) {
            sendFeedback("SafeAnchor is disabled in the config.");
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null || !player.isAlive()) {
            return;
        }

        this.protectionBlock = config.resolveProtectionBlock();
        this.safetyItem = config.resolveSafetyItem();
        if (protectionBlock.asItem() == Items.AIR) {
            abortStart("Protection block has no placeable item: " + config.getProtectionBlockId());
            return;
        }

        this.anchorSlot = resolveSlot(config.getAnchorSlot(), Items.RESPAWN_ANCHOR);
        this.glowstoneSlot = resolveSlot(config.getGlowstoneSlot(), Items.GLOWSTONE);
        this.protectionSlot = resolveSlot(config.getProtectionBlockSlot(), protectionBlock.asItem());
        this.safetySlot = resolveSlot(config.getSafetyItemSlot(), safetyItem);
        this.safetyInOffhand = SafeAnchorInventory.isInOffhand(safetyItem);

        if (anchorSlot < 0) {
            abortStart("Respawn Anchor missing in hotbar!");
            return;
        }
        if (glowstoneSlot < 0) {
            abortStart("Glowstone missing in hotbar!");
            return;
        }

        SafeAnchorActionExecutor.AimTarget target =
                SafeAnchorActionExecutor.findAnchorAimTarget(client);
        if (target == null || !SafeAnchorActionExecutor.isAimTargetStillValid(client, target)) {
            abortStart("No valid anchor target (look at a reachable block with free space).");
            return;
        }
        if (!SafeAnchorActionExecutor.anchorWouldExplode(world, target.placePos())) {
            abortStart("Anchor would NOT explode in this dimension.");
            return;
        }

        BlockPos expectedAnchor = target.placePos();
        BlockPos expectedProtection = SafeAnchorProtectionHelper
                .findProtectionPosition(expectedAnchor, player.getBlockPos())
                .orElse(null);
        boolean alreadyProtected = expectedProtection != null && SafeAnchorProtectionHelper
                .isValidProtectionBlock(world.getBlockState(expectedProtection), protectionBlock);

        if (config.isRequireProtection() && !alreadyProtected && protectionSlot < 0) {
            abortStart("Protection block (" + config.getProtectionBlockId() + ") missing in hotbar!");
            return;
        }
        if (safetySlot < 0 && !safetyInOffhand) {
            abortStart("Safety item (" + config.getSafetyItemId() + ") missing in hotbar/offhand!");
            return;
        }

        this.aimTarget = target;
        this.anchorPos = expectedAnchor;
        this.protectionPos = expectedProtection;
        this.sequenceWorld = world;
        this.sequencePlayer = player;
        this.sequenceStartNanos = System.nanoTime();
        this.startHealth = player.getHealth();
        this.active = true;
        this.lastResultMessage = null;

        setState(SafeAnchorState.PLACE_ANCHOR);
        delayManager.scheduleNextAction(config.getMinDelayMs(), config.getMaxDelayMs());
        SafeAnchorLogger.info("Sequence started (anchor target " + formatPosition(anchorPos) + ")");
        SafeAnchorLogger.debug("State: PLACE_ANCHOR, Delay: " + delayManager.getCurrentDelayMs() + "ms");
    }

    /**
     * Advances the state machine. Called once per client tick.
     */
    public void tick() {
        if (state == SafeAnchorState.IDLE) {
            return;
        }
        if (state == SafeAnchorState.CANCELLED || state == SafeAnchorState.FINISHED) {
            setState(SafeAnchorState.IDLE);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        SafeAnchorConfig config = SafeAnchorConfigManager.get();

        if (client.player == null || client.world == null) {
            cancelSequence("world/player gone");
            return;
        }
        if (client.world != sequenceWorld || client.player != sequencePlayer) {
            cancelSequence("world or player changed");
            return;
        }
        if (!client.player.isAlive()) {
            cancelSequence("player dead");
            return;
        }
        if (!config.isEnabled()) {
            cancelSequence("disabled mid-sequence");
            return;
        }
        long elapsedMs = (System.nanoTime() - sequenceStartNanos) / 1_000_000L;
        if (elapsedMs > config.getMaximumSequenceDurationMs()) {
            cancelSequence("timeout (" + elapsedMs + "ms)");
            return;
        }
        if (config.isCancelOnDamage() && client.player.getHealth() < startHealth) {
            cancelSequence("damage taken");
            return;
        }
        if (!delayManager.isReady()) {
            return;
        }
        executeCurrentState(client, config);
    }

    /**
     * Aborts the sequence without further actions and resets all state.
     */
    public void cancelSequence() {
        cancelSequence("cancelled");
    }

    /**
     * Aborts the sequence with a reason shown in chat, HUD and log.
     */
    public void cancelSequence(String reason) {
        if (state == SafeAnchorState.IDLE) {
            return;
        }
        SafeAnchorLogger.debug("Sequence cancelled: " + reason);
        sendFeedback("Sequence cancelled (" + reason + ").");
        setResultMessage("Cancelled: " + reason);
        cleanup();
        // CANCELLED is a transition marker; cleanup always leaves the
        // controller in the externally visible IDLE state.
        setState(SafeAnchorState.CANCELLED);
        setState(SafeAnchorState.IDLE);
    }

    public SafeAnchorState getCurrentState() {
        return state;
    }

    public boolean isRunning() {
        return active && state.isRunningState();
    }

    public SafeAnchorDelayManager getDelayManager() {
        return delayManager;
    }

    /**
     * @return the state that follows the current one (for the HUD).
     */
    public SafeAnchorState getNextState() {
        return switch (state) {
            case PLACE_ANCHOR -> SafeAnchorState.INSERT_GLOWSTONE;
            case INSERT_GLOWSTONE -> SafeAnchorState.PLACE_PROTECTION_BLOCK;
            case PLACE_PROTECTION_BLOCK -> SafeAnchorState.SWITCH_TO_DETONATION;
            case SWITCH_TO_DETONATION -> SafeAnchorState.DETONATE;
            case DETONATE -> SafeAnchorState.SWITCH_TO_SAFETY;
            case SWITCH_TO_SAFETY -> SafeAnchorState.FINISHED;
            default -> SafeAnchorState.IDLE;
        };
    }

    /**
     * @return short idle line for the HUD ("Ready" or the recent result).
     */
    public String getIdleLine() {
        if (lastResultMessage != null
                && System.nanoTime() - lastResultTimeNanos < IDLE_MESSAGE_TTL_NANOS) {
            return lastResultMessage;
        }
        return "Ready";
    }

    private void executeCurrentState(MinecraftClient client, SafeAnchorConfig config) {
        switch (state) {
            case PLACE_ANCHOR -> executePlaceAnchor(config);
            case INSERT_GLOWSTONE -> executeInsertGlowstone(client, config);
            case PLACE_PROTECTION_BLOCK -> executePlaceProtection(client, config);
            case SWITCH_TO_DETONATION -> executeSwitchDetonation(client, config);
            case DETONATE -> executeDetonate(client, config);
            case SWITCH_TO_SAFETY -> executeSwitchSafety(client);
            default -> cancelSequence("illegal state " + state);
        }
    }

    private void executePlaceAnchor(SafeAnchorConfig config) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!SafeAnchorActionExecutor.isAimTargetStillValid(client, aimTarget)) {
            cancelSequence("anchor target became invalid");
            return;
        }
        if (!SafeAnchorInventory.slotHasItem(anchorSlot, Items.RESPAWN_ANCHOR)) {
            anchorSlot = SafeAnchorInventory.findItemInHotbar(Items.RESPAWN_ANCHOR);
            if (anchorSlot < 0) {
                cancelSequence("anchor no longer in hotbar");
                return;
            }
        }
        if (!SafeAnchorActionExecutor.placeAnchor(MinecraftClient.getInstance(), anchorSlot, aimTarget)) {
            cancelSequence("could not place anchor");
            return;
        }
        advanceTo(SafeAnchorState.INSERT_GLOWSTONE, config);
    }

    private void executeInsertGlowstone(MinecraftClient client, SafeAnchorConfig config) {
        ClientWorld world = client.world;
        if (!SafeAnchorActionExecutor.isAnchorAt(world, anchorPos)) {
            if (world.getBlockState(anchorPos).isReplaceable()) {
                // Server confirm pending: wait one more delay instead of cancelling.
                waitAndRetry(config, "Waiting for anchor placement confirm");
                return;
            }
            cancelSequence("anchor placement blocked");
            return;
        }
        int slot = SafeAnchorInventory.slotHasItem(glowstoneSlot, Items.GLOWSTONE)
                ? glowstoneSlot
                : SafeAnchorInventory.findItemInHotbar(Items.GLOWSTONE);
        if (slot < 0) {
            cancelSequence("glowstone no longer available");
            return;
        }
        glowstoneSlot = slot;
        if (!SafeAnchorActionExecutor.insertGlowstone(client, slot, anchorPos)) {
            cancelSequence("could not insert glowstone");
            return;
        }
        advanceTo(SafeAnchorState.PLACE_PROTECTION_BLOCK, config);
    }

    private void executePlaceProtection(MinecraftClient client, SafeAnchorConfig config) {
        ClientWorld world = client.world;
        if (!SafeAnchorActionExecutor.isAnchorAt(world, anchorPos)) {
            cancelSequence("anchor lost");
            return;
        }
        // Recompute: the player may have moved since the sequence started.
        protectionPos = SafeAnchorProtectionHelper
                .findProtectionPosition(anchorPos, client.player.getBlockPos())
                .orElse(protectionPos);
        if (protectionPos != null && SafeAnchorProtectionHelper
                .isValidProtectionBlock(world.getBlockState(protectionPos), protectionBlock)) {
            SafeAnchorLogger.debug("Protection already present, skipping placement.");
            advanceTo(SafeAnchorState.SWITCH_TO_DETONATION, config);
            return;
        }
        if (!SafeAnchorInventory.slotHasItem(protectionSlot, protectionBlock.asItem())) {
            protectionSlot = SafeAnchorInventory.findItemInHotbar(protectionBlock.asItem());
        }
        if (protectionSlot < 0) {
            if (config.isRequireProtection()) {
                cancelSequence("protection block missing");
                return;
            }
            SafeAnchorLogger.debug("No protection item, continuing (requireProtection=false).");
            advanceTo(SafeAnchorState.SWITCH_TO_DETONATION, config);
            return;
        }
        if (protectionPos == null || !SafeAnchorActionExecutor
                .placeProtectionBlock(client, protectionSlot, anchorPos, protectionPos)) {
            if (config.isRequireProtection()) {
                cancelSequence("could not place protection");
                return;
            }
            SafeAnchorLogger.debug("Protection placement failed, continuing (requireProtection=false).");
        }
        advanceTo(SafeAnchorState.SWITCH_TO_DETONATION, config);
    }

    private void executeSwitchDetonation(MinecraftClient client, SafeAnchorConfig config) {
        if (!SafeAnchorActionExecutor.isAnchorAt(client.world, anchorPos)) {
            cancelSequence("anchor lost");
            return;
        }
        detonationSlot = SafeAnchorActionExecutor.resolveDetonationSlot(anchorSlot);
        if (!SafeAnchorInventory.switchToSlot(detonationSlot)) {
            cancelSequence("could not switch to detonation slot");
            return;
        }
        advanceTo(SafeAnchorState.DETONATE, config);
    }

    private void executeDetonate(MinecraftClient client, SafeAnchorConfig config) {
        ClientWorld world = client.world;
        if (!SafeAnchorActionExecutor.isAnchorAt(world, anchorPos)) {
            cancelSequence("anchor lost");
            return;
        }
        if (!SafeAnchorActionExecutor.isAnchorCharged(world, anchorPos)) {
            // Server confirm pending: wait one more delay instead of cancelling.
            waitAndRetry(config, "Waiting for anchor charge confirm");
            return;
        }
        if (!SafeAnchorActionExecutor.anchorWouldExplode(world, anchorPos)) {
            cancelSequence("dimension changed");
            return;
        }
        protectionPos = SafeAnchorProtectionHelper
                .findProtectionPosition(anchorPos, client.player.getBlockPos())
                .orElse(protectionPos);
        if (config.isRequireProtection() && (protectionPos == null || !SafeAnchorProtectionHelper
                .isValidProtectionBlock(world.getBlockState(protectionPos), protectionBlock))) {
            cancelSequence("not protected");
            return;
        }
        if (!safetyAvailable()) {
            cancelSequence("safety item missing");
            return;
        }
        if (!SafeAnchorActionExecutor.detonate(client, detonationSlot, anchorPos)) {
            cancelSequence("could not detonate");
            return;
        }
        advanceTo(SafeAnchorState.SWITCH_TO_SAFETY, config);
    }

    private void executeSwitchSafety(MinecraftClient client) {
        if (client.player == null) {
            cancelSequence("player gone");
            return;
        }
        if (!SafeAnchorInventory.isInOffhand(safetyItem)) {
            if (!safetyAvailable() || !SafeAnchorInventory.switchToSlot(safetySlot)) {
                cancelSequence("could not switch to safety item");
                return;
            }
        }
        finishSequence();
    }

    private boolean safetyAvailable() {
        if (SafeAnchorInventory.isInOffhand(safetyItem)) {
            return true;
        }
        if (safetySlot >= 0 && SafeAnchorInventory.slotHasItem(safetySlot, safetyItem)) {
            return true;
        }
        int found = SafeAnchorInventory.findItemInHotbar(safetyItem);
        if (found >= 0) {
            safetySlot = found;
            return true;
        }
        return false;
    }

    private void advanceTo(SafeAnchorState next, SafeAnchorConfig config) {
        setState(next);
        delayManager.scheduleNextAction(config.getMinDelayMs(), config.getMaxDelayMs());
        SafeAnchorLogger.debug("State: " + next + ", Delay: " + delayManager.getCurrentDelayMs() + "ms");
    }

    private void waitAndRetry(SafeAnchorConfig config, String what) {
        delayManager.scheduleNextAction(config.getMinDelayMs(), config.getMaxDelayMs());
        SafeAnchorLogger.debug(what + ", retry in " + delayManager.getCurrentDelayMs() + "ms");
    }

    private void finishSequence() {
        setState(SafeAnchorState.FINISHED);
        SafeAnchorLogger.info("Sequence finished");
        setResultMessage("Done");
        cleanup();
        setState(SafeAnchorState.IDLE);
    }

    private void cleanup() {
        active = false;
        delayManager.reset();
        aimTarget = null;
        anchorPos = null;
        protectionPos = null;
        sequenceWorld = null;
        sequencePlayer = null;
        anchorSlot = -1;
        glowstoneSlot = -1;
        protectionSlot = -1;
        safetySlot = -1;
        detonationSlot = -1;
        safetyInOffhand = false;
    }

    private void setState(SafeAnchorState next) {
        this.state = next;
    }

    private void setResultMessage(String message) {
        this.lastResultMessage = message;
        this.lastResultTimeNanos = System.nanoTime();
    }

    private static String formatPosition(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    private static int resolveSlot(int configuredSlot, Item item) {
        if (configuredSlot == SafeAnchorConfig.AUTO_SLOT) {
            return SafeAnchorInventory.findItemInHotbar(item);
        }
        return SafeAnchorInventory.slotHasItem(configuredSlot, item) ? configuredSlot : -1;
    }

    private void abortStart(String message) {
        sendFeedback(message);
        SafeAnchorLogger.debug("Start aborted: " + message);
    }

    private static void sendFeedback(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§6[SafeAnchor]§r " + message), false);
        }
    }
}
