package com.vardanrattan.echoes.data;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Approximate record of a visited structure for discovery tracking.
 */
public record VisitedStructure(Identifier structureId, BlockPos approximatePos) {
}

