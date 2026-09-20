package com.safeanchor;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Optional;
import java.util.Set;

/**
 * Computes and validates the protection-block position between the player
 * and the anchor. Positions are never assumed blindly: every candidate is
 * derived from live player/anchor positions and re-checked before use.
 */
public final class SafeAnchorProtectionHelper {

    /**
     * Blast-resistant blocks that always count as valid protection,
     * regardless of the configured protection block.
     */
    private static final Set<Block> ALWAYS_ACCEPTED = Set.of(
            Blocks.OBSIDIAN,
            Blocks.CRYING_OBSIDIAN,
            Blocks.ANCIENT_DEBRIS,
            Blocks.NETHERITE_BLOCK,
            Blocks.REINFORCED_DEEPSLATE
    );

    private SafeAnchorProtectionHelper() {
    }

    /**
     * Computes the protection position: the cell directly adjacent to the
     * anchor on the player's side (dominant horizontal axis). When the player
     * is standing in the anchor's column, the cell above the anchor is used.
     */
    public static Optional<BlockPos> findProtectionPosition(BlockPos anchorPos, BlockPos playerPos) {
        if (anchorPos == null || playerPos == null) {
            return Optional.empty();
        }
        int dx = playerPos.getX() - anchorPos.getX();
        int dz = playerPos.getZ() - anchorPos.getZ();
        if (dx == 0 && dz == 0) {
            return Optional.of(anchorPos.up());
        }
        Direction direction = Math.abs(dx) >= Math.abs(dz)
                ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        return Optional.of(anchorPos.offset(direction));
    }

    /**
     * @return true when the state is the configured protection block (or an
     *         equivalently blast-resistant block).
     */
    public static boolean isValidProtectionBlock(BlockState state, Block configuredBlock) {
        if (state == null || state.isAir()) {
            return false;
        }
        if (configuredBlock != null && state.isOf(configuredBlock)) {
            return true;
        }
        return ALWAYS_ACCEPTED.contains(state.getBlock());
    }

    /**
     * @return true when the computed protection position already holds a
     *         valid protection block.
     */
    public static boolean isProtected(ClientWorld world, BlockPos anchorPos, BlockPos playerPos,
                                      Block configuredBlock) {
        if (world == null) {
            return false;
        }
        Optional<BlockPos> position = findProtectionPosition(anchorPos, playerPos);
        if (position.isEmpty()) {
            return false;
        }
        return isValidProtectionBlock(world.getBlockState(position.get()), configuredBlock);
    }
}
