package com.logistics.power.cable;

import com.logistics.core.lib.power.AbstractEngineBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.EnergyStorageUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Represents a connected group of cables that transfers energy without storage.
 *
 * <p>Each tick, the network:
 * <ol>
 *   <li>Finds source devices that expose extractable energy</li>
 *   <li>Finds target devices that can accept energy</li>
 *   <li>Transfers only energy that can be accepted by a target</li>
 * </ol>
 *
 * <p>Push-based sources, such as engines, insert into a cable endpoint directly.
 * That insertion is forwarded to connected targets in the same transaction;
 * any energy that cannot be delivered is rejected rather than stored.
 */
public class CableNetwork {
    private final Set<BlockPos> cablePositions = new HashSet<>();

    public Set<BlockPos> getCablePositions() {
        return cablePositions;
    }

    public boolean contains(BlockPos pos) {
        return cablePositions.contains(pos);
    }

    public boolean isEmpty() {
        return cablePositions.isEmpty();
    }

    /**
     * Builds a cable network by flood-filling from the given starting position.
     */
    public static CableNetwork buildFrom(Level level, BlockPos start) {
        CableNetwork network = new CableNetwork();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start);
        network.cablePositions.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (!network.cablePositions.contains(neighbor)
                        && level.getBlockEntity(neighbor) instanceof CableBlockEntity) {
                    network.cablePositions.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return network;
    }

    /**
     * Moves energy across the network without buffering it in cables.
     *
     * <p>Managed push sources, such as engines, are skipped here because they
     * actively push into cable endpoints on their own output cadence.
     */
    public void tick(Level level) {
        DeviceConnections connections = collectDeviceConnections(level, null);
        transferBetween(connections.sources(), connections.targets(), getNetworkTransferLimit(level));
    }

    public long insert(
            Level level, BlockPos entryCablePos, @Nullable Direction sourceSide,
            long maxAmount, TransactionContext transaction) {
        if (maxAmount <= 0 || !contains(entryCablePos)) {
            return 0;
        }

        BlockPos sourcePos = sourceSide == null ? null : entryCablePos.relative(sourceSide);
        DeviceConnections connections = collectDeviceConnections(level, sourcePos);
        long transferLimit = Math.min(
            maxAmount,
            Math.min(cableTransferRate(level, entryCablePos), getNetworkTransferLimit(level)));
        return insertIntoTargets(connections.targets(), transferLimit, transaction);
    }

    private DeviceConnections collectDeviceConnections(Level level, @Nullable BlockPos excludedPos) {
        List<EnergyStorage> sources = new ArrayList<>();
        List<EnergyStorage> targets = new ArrayList<>();
        Set<DeviceConnectionKey> seenConnections = new HashSet<>();

        for (BlockPos cablePos : cablePositions) {
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = cablePos.relative(dir);
                if (cablePositions.contains(neighborPos)) continue;
                if (neighborPos.equals(excludedPos)) continue;

                Direction side = dir.getOpposite();
                EnergyStorage storage = EnergyStorage.SIDED.find(level, neighborPos, side);
                if (storage != null) {
                    if (!seenConnections.add(new DeviceConnectionKey(neighborPos, side))) continue;

                    BlockEntity blockEntity = level.getBlockEntity(neighborPos);
                    if (storage.supportsInsertion()) {
                        targets.add(storage);
                    }
                    if (storage.supportsExtraction() && !isManagedPushSource(blockEntity)) {
                        sources.add(storage);
                    }
                }
            }
        }

        return new DeviceConnections(sources, targets);
    }

    private long getNetworkTransferLimit(Level level) {
        long limit = 0;
        for (BlockPos cablePos : cablePositions) {
            limit += cableTransferRate(level, cablePos);
        }
        return limit;
    }

    private long cableTransferRate(Level level, BlockPos cablePos) {
        return level.getBlockEntity(cablePos) instanceof CableBlockEntity cable ? cable.getTransferRate() : 0;
    }

    private long transferBetween(List<EnergyStorage> sources, List<EnergyStorage> targets, long maxAmount) {
        if (sources.isEmpty() || targets.isEmpty() || maxAmount <= 0) return 0;

        List<EnergyStorage> validSources = filterSources(sources);
        List<EnergyStorage> validTargets = filterTargets(targets);
        if (validSources.isEmpty() || validTargets.isEmpty()) return 0;

        Collections.shuffle(validSources);
        Collections.shuffle(validTargets);

        try (Transaction transaction = Transaction.openOuter()) {
            long totalTransferred = 0;

            for (int targetIndex = 0; targetIndex < validTargets.size(); targetIndex++) {
                EnergyStorage target = validTargets.get(targetIndex);
                int remainingTargets = validTargets.size() - targetIndex;
                long targetRemaining = fairShare(maxAmount - totalTransferred, remainingTargets);

                for (EnergyStorage source : validSources) {
                    if (source == target || targetRemaining <= 0 || totalTransferred >= maxAmount) continue;

                    long moved = EnergyStorageUtil.move(
                            source,
                            target,
                            Math.min(targetRemaining, maxAmount - totalTransferred),
                            transaction);
                    totalTransferred += moved;
                    targetRemaining -= moved;
                }
            }

            transaction.commit();
            return totalTransferred;
        }
    }

    /**
     * Inserts energy into connected targets, distributing fairly.
     * Shuffles targets to avoid bias from iteration order.
     */
    private long insertIntoTargets(List<EnergyStorage> targets, long maxAmount, TransactionContext transaction) {
        if (targets.isEmpty() || maxAmount <= 0) return 0;

        if (transaction == null) {
            try (Transaction outer = Transaction.openOuter()) {
                long inserted = insertIntoTargets(targets, maxAmount, outer);
                outer.commit();
                return inserted;
            }
        }

        List<EnergyStorage> validTargets = filterTargets(targets);
        if (validTargets.isEmpty()) return 0;

        Collections.shuffle(validTargets);

        long totalInserted = 0;
        for (int i = 0; i < validTargets.size(); i++) {
            EnergyStorage target = validTargets.get(i);
            int remainingTargets = validTargets.size() - i;
            long remaining = maxAmount - totalInserted;
            long targetMax = fairShare(remaining, remainingTargets);

            totalInserted += target.insert(targetMax, transaction);
        }

        return totalInserted;
    }

    private List<EnergyStorage> filterSources(List<EnergyStorage> sources) {
        List<EnergyStorage> validSources = new ArrayList<>();
        for (EnergyStorage source : sources) {
            if (source.supportsExtraction()) {
                validSources.add(source);
            }
        }
        return validSources;
    }

    private List<EnergyStorage> filterTargets(List<EnergyStorage> targets) {
        List<EnergyStorage> validTargets = new ArrayList<>();
        for (EnergyStorage target : targets) {
            if (target.supportsInsertion()) {
                validTargets.add(target);
            }
        }
        return validTargets;
    }

    private boolean isManagedPushSource(BlockEntity blockEntity) {
        return blockEntity instanceof AbstractEngineBlockEntity;
    }

    private long fairShare(long remaining, int remainingTargets) {
        if (remaining <= 0 || remainingTargets <= 0) return 0;
        return Math.max(1, remaining / remainingTargets);
    }

    private record DeviceConnections(List<EnergyStorage> sources, List<EnergyStorage> targets) {}

    private record DeviceConnectionKey(BlockPos pos, Direction side) {}
}
