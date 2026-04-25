package com.logistics.power.cable;

import com.logistics.LogisticsPower;
import com.logistics.core.lib.BaseBlockEntity;
import com.logistics.core.lib.block.capability.HasEnergyStorage;
import com.logistics.core.lib.energy.EnergyComponent;
import com.logistics.core.lib.power.AcceptsLowTierEnergy;
import com.logistics.core.lib.support.ProbeResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;

/**
 * Block entity for energy cables. Stores a small energy buffer
 * that participates in network-level energy distribution.
 *
 * <p>Individual cables do not push or pull energy. Instead, all cables
 * in a connected group form a {@link CableNetwork} which pools energy
 * and distributes it evenly to all connected devices each tick.
 */
public class CableBlockEntity extends BaseBlockEntity
        implements HasEnergyStorage, AcceptsLowTierEnergy {

    private static final long CAPACITY = 640;
    private static final long MAX_TRANSFER = 640;

    private final EnergyComponent energy = new EnergyComponent(
            CAPACITY, MAX_TRANSFER, MAX_TRANSFER, this::markDirtyAndSync);

    private final CableBlock.ConnectionType[] connectionCache = new CableBlock.ConnectionType[6];
    private boolean connectionCacheDirty = true;
    private int lastConnectionMask = -1;

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(LogisticsPower.ENTITY.CABLE_BLOCK_ENTITY, pos, state);
        for (int i = 0; i < 6; i++) {
            connectionCache[i] = CableBlock.ConnectionType.NONE;
        }
    }

    // ==================== HasEnergyStorage ====================

    @Override
    public EnergyStorage energyStorage(@Nullable Direction side) {
        return energy;
    }

    // ==================== Lifecycle ====================

    /**
     * Called from CableBlock.onRemove when the cable is broken or replaced.
     */
    public void onCableRemoved() {
        if (level != null && !level.isClientSide()) {
            CableNetworkManager.get(level).removeCable(worldPosition);
        }
    }

    // ==================== Connection Cache ====================

    public void invalidateConnectionCache() {
        connectionCacheDirty = true;
    }

    public CableBlock.ConnectionType getCachedConnectionType(Direction direction) {
        if (connectionCacheDirty) {
            rebuildConnectionCache();
        }
        return connectionCache[direction.get3DDataValue()];
    }

    private void rebuildConnectionCache() {
        if (level == null) return;
        if (!(getBlockState().getBlock() instanceof CableBlock cableBlock)) return;

        for (Direction dir : Direction.values()) {
            connectionCache[dir.get3DDataValue()] = cableBlock.getDynamicConnectionType(level, worldPosition, dir);
        }
        connectionCacheDirty = false;
    }

    private int computeConnectionMask() {
        int mask = 0;
        for (Direction dir : Direction.values()) {
            CableBlock.ConnectionType type = connectionCache[dir.get3DDataValue()];
            if (type != CableBlock.ConnectionType.NONE) {
                mask |= (type.ordinal() << (dir.get3DDataValue() * 2));
            }
        }
        return mask;
    }

    private void updateConnections() {
        if (!connectionCacheDirty) return;
        rebuildConnectionCache();

        int mask = computeConnectionMask();
        if (mask != lastConnectionMask) {
            lastConnectionMask = mask;
            if (level != null && !level.isClientSide()) {
                markDirtyAndSync();
            }
        }
    }

    // ==================== Energy Access (for CableNetwork) ====================

    public long getStoredEnergy() {
        return energy.getAmount();
    }

    public long getCapacity() {
        return CAPACITY;
    }

    public void setStoredEnergy(long amount) {
        energy.setAmount(amount);
        markDirtyAndSync();
    }

    // ==================== Tick ====================

    public static void tick(Level world, BlockPos pos, BlockState state, CableBlockEntity cable) {
        cable.updateConnections();
        CableNetworkManager.get(world).addCable(pos);
    }

    // ==================== Probe ====================

    public ProbeResult getProbeResult() {
        return ProbeResult.builder("Energy Cable")
                .entry("Energy", String.format("%d / %d RF", energy.getAmount(), CAPACITY), ChatFormatting.AQUA)
                .build();
    }

    // ==================== NBT ====================

    @Override
    protected void saveLogisticsData(CompoundTag tag, HolderLookup.Provider registries) {
        energy.writeNbt(tag, "Energy");
    }

    @Override
    protected void loadLogisticsData(CompoundTag tag, HolderLookup.Provider registries) {
        energy.readNbt(tag, "Energy");
        invalidateConnectionCache();
    }
}
