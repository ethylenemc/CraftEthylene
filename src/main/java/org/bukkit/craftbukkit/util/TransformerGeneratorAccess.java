package org.bukkit.craftbukkit.util;

import org.bukkit.craftbukkit.block.CraftBlockEntityState;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.block.CraftBlockStates;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

public class TransformerGeneratorAccess extends DelegatedGeneratorAccess {

    private CraftStructureTransformer structureTransformer;

    public void setStructureTransformer(CraftStructureTransformer structureTransformer) {
        this.structureTransformer = structureTransformer;
    }

    public CraftStructureTransformer getStructureTransformer() {
        return structureTransformer;
    }

    @Override
    public boolean addFreshEntity(net.minecraft.world.entity.Entity arg0) {
        if (structureTransformer != null && !structureTransformer.transformEntity(arg0)) {
            return false;
        }
        return super.addFreshEntity(arg0);
    }

    @Override
    public boolean addFreshEntity(net.minecraft.world.entity.Entity arg0, SpawnReason arg1) {
        if (structureTransformer != null && !structureTransformer.transformEntity(arg0)) {
            return false;
        }
        return super.addFreshEntity(arg0, arg1);
    }

    @Override
    public void addFreshEntityWithPassengers(net.minecraft.world.entity.Entity arg0) {
        if (structureTransformer != null && !structureTransformer.transformEntity(arg0)) {
            return;
        }
        super.addFreshEntityWithPassengers(arg0);
    }

    @Override
    public void addFreshEntityWithPassengers(net.minecraft.world.entity.Entity arg0, SpawnReason arg1) {
        if (structureTransformer != null && !structureTransformer.transformEntity(arg0)) {
            return;
        }
        super.addFreshEntityWithPassengers(arg0, arg1);
    }

    public boolean setCraftBlock(net.minecraft.core.BlockPos position, CraftBlockState craftBlockState, int i, int j) {
        if (structureTransformer != null) {
            craftBlockState = structureTransformer.transformCraftState(craftBlockState);
        }
        // This code is based on the method 'net.minecraft.world.level.levelgen.structure.StructurePiece#placeBlock'
        // It ensures that any kind of block is updated correctly upon placing it
        net.minecraft.world.level.block.state.BlockState iblockdata = craftBlockState.getHandle();
        boolean result = super.setBlock(position, iblockdata, i, j);
        net.minecraft.world.level.material.FluidState fluid = getFluidState(position);
        if (!fluid.isEmpty()) {
            scheduleTick(position, fluid.getType(), 0);
        }
        if (net.minecraft.world.level.levelgen.structure.StructurePiece.SHAPE_CHECK_BLOCKS.contains(iblockdata.getBlock())) {
            getChunk(position).markPosForPostprocessing(position);
        }
        net.minecraft.world.level.block.entity.BlockEntity tileEntity = getBlockEntity(position);
        if (tileEntity != null && craftBlockState instanceof CraftBlockEntityState<?> craftEntityState) {
            tileEntity.loadWithComponents(craftEntityState.getSnapshotNBT(), this.registryAccess());
        }
        return result;
    }

    public boolean setCraftBlock(net.minecraft.core.BlockPos position, CraftBlockState craftBlockState, int i) {
        return setCraftBlock(position, craftBlockState, i, 512);
    }

    @Override
    public boolean setBlock(net.minecraft.core.BlockPos position, net.minecraft.world.level.block.state.BlockState iblockdata, int i, int j) {
        if (structureTransformer == null || !structureTransformer.canTransformBlocks()) {
            return super.setBlock(position, iblockdata, i, j);
        }
        return setCraftBlock(position, (CraftBlockState) CraftBlockStates.getBlockState(this, position, iblockdata, null), i, j);
    }

    @Override
    public boolean setBlock(net.minecraft.core.BlockPos position, net.minecraft.world.level.block.state.BlockState iblockdata, int i) {
        return setBlock(position, iblockdata, i, 512);
    }
}
