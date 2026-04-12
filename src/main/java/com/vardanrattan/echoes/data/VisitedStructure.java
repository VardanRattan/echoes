package com.vardanrattan.echoes.data;

import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;

/**
 * Approximate record of a visited structure for discovery tracking.
 */
public record VisitedStructure(Identifier structureId, BlockPos approximatePos) {
}

