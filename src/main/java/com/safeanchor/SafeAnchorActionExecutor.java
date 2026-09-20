package com.safeanchor;

import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.attribute.EnvironmentAttributes;

/**
 * Executes the actual Minecraft interactions (block placement / block use).
 * Every method performs exactly one interaction and reports success; nothing
 * here loops, sleeps or retries.
 */
public final class SafeAnchorActionExecutor {

    private SafeAnchorActionExecutor() {
    }

    /**
     * Anchor placement target derived from the crosshair.
     *
     * @param hitResult the aimed block face (used for the place interaction)
     * @param placePos  the cell the anchor will occupy
     */
    public record AimTarget(BlockHitResult hitResult, BlockPos placePos) {
    }

    /**
     * Builds the anchor target from the block the player is currently aiming at.
     *
     * @return the target, or null when the player aims at no suitable block.
     */
    public static AimTarget findAnchorAimTarget(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return null;
        }
        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof BlockHitResult blockHit)) {
            return null;
        }
        BlockPos placePos = blockHit.getBlockPos().offset(blockHit.getSide());
        AimTarget target = new AimTarget(blockHit, placePos);
        return isAimTargetStillValid(client, target) ? target : null;
    }

    /**
     * Re-checks a saved target immediately before an interaction. The crosshair
     * target is captured at activation time, but the world can change while a
     * delay is pending. This prevents an old click from being sent at a stale
     * or out-of-range position.
     */
    public static boolean isAimTargetStillValid(MinecraftClient client, AimTarget target) {
        if (client == null || client.player == null || client.world == null || target == null) {
            return false;
        }
        BlockPos supportPos = target.hitResult().getBlockPos();
        BlockPos placePos = target.placePos();
        return client.world.isInBuildLimit(placePos)
                && client.world.isPosLoaded(placePos)
                && !client.world.getBlockState(supportPos).isAir()
                && client.world.getBlockState(placePos).isReplaceable()
                && isWithinInteractionRange(client, placePos);
    }

    /**
     * Uses a conservative client-side range check. The server remains the
     * authority and may still reject an interaction because of its own reach
     * or protection rules.
     */
    public static boolean isWithinInteractionRange(MinecraftClient client, BlockPos pos) {
        if (client == null || client.player == null || pos == null) {
            return false;
        }
        // Entity#getPos() was renamed to getEntityPos() in Yarn for 1.21.9+.
        return client.player.getEntityPos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= 36.0D;
    }

    /**
     * Places the anchor at the previously computed aim target.
     */
    public static boolean placeAnchor(MinecraftClient client, int anchorSlot, AimTarget target) {
        if (target == null) {
            return false;
        }
        if (!SafeAnchorInventory.switchToSlot(anchorSlot)) {
            return false;
        }
        return interactBlock(client, target.hitResult());
    }

    /**
     * Inserts glowstone into the anchor to charge it.
     */
    public static boolean insertGlowstone(MinecraftClient client, int glowstoneSlot, BlockPos anchorPos) {
        if (anchorPos == null || !isWithinInteractionRange(client, anchorPos)) {
            return false;
        }
        if (!SafeAnchorInventory.switchToSlot(glowstoneSlot)) {
            return false;
        }
        return interactBlock(client, anchorFaceHit(anchorPos, Direction.UP));
    }

    /**
     * Places the protection block at {@code protectionPos} by clicking a
     * neighboring support block. The (already charged) anchor itself is never
     * clicked here, so this step cannot detonate it early.
     */
    public static boolean placeProtectionBlock(MinecraftClient client, int protectionSlot,
                                               BlockPos anchorPos, BlockPos protectionPos) {
        if (client == null || client.world == null || protectionPos == null
                || !isWithinInteractionRange(client, protectionPos)) {
            return false;
        }
        if (!client.world.getBlockState(protectionPos).isReplaceable()) {
            return false;
        }
        BlockHitResult supportHit = findSupportHit(client.world, protectionPos, anchorPos);
        if (supportHit == null) {
            return false;
        }
        if (!SafeAnchorInventory.switchToSlot(protectionSlot)) {
            return false;
        }
        return interactBlock(client, supportHit);
    }

    /**
     * Triggers the charged anchor. The caller must ensure the detonation slot
     * holds no glowstone (empty hand preferred).
     */
    public static boolean detonate(MinecraftClient client, int detonationSlot, BlockPos anchorPos) {
        if (anchorPos == null || !isWithinInteractionRange(client, anchorPos)) {
            return false;
        }
        if (!SafeAnchorInventory.switchToSlot(detonationSlot)) {
            return false;
        }
        return interactBlock(client, anchorFaceHit(anchorPos, Direction.UP));
    }

    /**
     * Picks a safe detonation slot: an empty hotbar slot (bare hand) is
     * preferred; otherwise the anchor slot is reused because an anchor item
     * can never charge the anchor again.
     */
    public static int resolveDetonationSlot(int anchorSlot) {
        int empty = SafeAnchorInventory.findEmptyHotbarSlot();
        return empty >= 0 ? empty : anchorSlot;
    }

    /**
     * @return true when a Respawn Anchor block is present at {@code pos}.
     */
    public static boolean isAnchorAt(ClientWorld world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        return world.getBlockState(pos).getBlock() instanceof RespawnAnchorBlock;
    }

    /**
     * @return true when the anchor at {@code pos} holds at least one charge.
     */
    public static boolean isAnchorCharged(ClientWorld world, BlockPos pos) {
        if (!isAnchorAt(world, pos)) {
            return false;
        }
        for (var entry : world.getBlockState(pos).getEntries().entrySet()) {
            if ("charges".equals(entry.getKey().getName())
                    && entry.getValue() instanceof Integer charges) {
                return charges > 0;
            }
        }
        return false;
    }

    /**
     * @return true when triggering an anchor at {@code pos} causes an
     *         explosion (i.e. anywhere the anchor does not work as a spawn
     *         point). Uses the same environment attribute vanilla itself
     *         queries, with a dimension-key fallback.
     */
    public static boolean anchorWouldExplode(ClientWorld world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        try {
            Boolean works = world.getEnvironmentAttributes()
                    .getAttributeValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS_GAMEPLAY, pos);
            if (works != null) {
                return !works;
            }
        } catch (Exception ignored) {
            // Fall through to the dimension-key fallback below.
        }
        return world.getRegistryKey() != World.NETHER;
    }

    private static BlockHitResult anchorFaceHit(BlockPos anchorPos, Direction face) {
        return new BlockHitResult(Vec3d.ofCenter(anchorPos), face, anchorPos, false);
    }

    private static boolean interactBlock(MinecraftClient client, BlockHitResult hit) {
        if (client == null || client.player == null || client.interactionManager == null || hit == null) {
            return false;
        }
        ActionResult result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
        return result.isAccepted();
    }

    /**
     * Finds a solid neighbor of {@code placePos} (never the anchor itself)
     * whose face can be clicked to place into {@code placePos}.
     */
    private static BlockHitResult findSupportHit(ClientWorld world, BlockPos placePos, BlockPos excludePos) {
        BlockHitResult below = supportHitIfSolid(world, placePos.down(), Direction.UP, excludePos);
        if (below != null) {
            return below;
        }
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockHitResult hit = supportHitIfSolid(world, placePos.offset(dir), dir.getOpposite(), excludePos);
            if (hit != null) {
                return hit;
            }
        }
        return supportHitIfSolid(world, placePos.up(), Direction.DOWN, excludePos);
    }

    private static BlockHitResult supportHitIfSolid(ClientWorld world, BlockPos supportPos,
                                                     Direction face, BlockPos excludePos) {
        if (supportPos.equals(excludePos)) {
            return null;
        }
        if (!world.getBlockState(supportPos).isSolidBlock(world, supportPos)) {
            return null;
        }
        return new BlockHitResult(Vec3d.ofCenter(supportPos), face, supportPos, false);
    }
}
