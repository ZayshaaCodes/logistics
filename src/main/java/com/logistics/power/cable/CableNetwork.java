package com.logistics.power.cable;

import com.logistics.core.lib.power.AbstractEngineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import team.reborn.energy.api.EnergyStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Represents a connected group of energy cables that share energy as a single pool.
 *
 * <p>Each tick, the network:
 * <ol>
 *   <li>Pools all energy from member cables</li>
 *   <li>Distributes energy evenly to all connected devices</li>
 *   <li>Spreads remaining energy evenly back across cables</li>
 * </ol>
 *
 * <p>This ensures all devices connected anywhere on the cable network receive
 * equal power, regardless of their distance from the energy source.
 */
public class CableNetwork {
    private static final long MAX_TRANSFER_PER_DEVICE = 640;

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
     * Distributes energy across the network following the standard push-based convention.
     *
     * <p>Following TechReborn's reference implementation:
     * <ol>
     *   <li>Pool all cable energy into a network total</li>
     *   <li>Pull energy from adjacent sources (generators) into the pool</li>
     *   <li>Push energy from the pool to adjacent consumers (machines)</li>
     *   <li>Spread remaining energy evenly across cables</li>
     * </ol>
     *
     * <p>Uses the Transaction API for safe energy transfers. Each device
     * receives a fair share limited by the cable transfer rate.
     */
    public void tick(Level level) {
        // 1. Collect all cable entities and pool their energy
        List<CableBlockEntity> cables = new ArrayList<>();
        long networkCapacity = 0;
        long networkAmount = 0;

        for (BlockPos pos : cablePositions) {
            if (level.getBlockEntity(pos) instanceof CableBlockEntity cable) {
                cables.add(cable);
                networkAmount += cable.getStoredEnergy();
                networkCapacity += cable.getCapacity();
                cable.setStoredEnergy(0);
            }
        }

        if (cables.isEmpty()) return;
        if (networkAmount > networkCapacity) {
            networkAmount = networkCapacity;
        }

        // 2. Find all unique device connections at the network boundary
        List<EnergyStorage> sources = new ArrayList<>();
        List<EnergyStorage> targets = new ArrayList<>();
        Set<BlockPos> seenDevices = new HashSet<>();

        for (BlockPos cablePos : cablePositions) {
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = cablePos.relative(dir);
                if (cablePositions.contains(neighborPos)) continue;
                if (!seenDevices.add(neighborPos)) continue;

                EnergyStorage storage = EnergyStorage.SIDED.find(level, neighborPos, dir.getOpposite());
                if (storage != null) {
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

        // 3. Pull energy from sources into the network
        networkAmount += transferEnergy(sources, networkCapacity - networkAmount, true);

        // 4. Push energy from the network to consumers
        networkAmount -= transferEnergy(targets, networkAmount, false);

        // 5. Spread remaining energy evenly across cables
        int cableCount = cables.size();
        for (CableBlockEntity cable : cables) {
            long share = networkAmount / cableCount;
            cable.setStoredEnergy(share);
            networkAmount -= share;
            cableCount--;
        }
    }

    /**
     * Transfers energy to/from a list of targets, distributing fairly.
     * Shuffles targets to avoid bias from iteration order.
     *
     * @param targets   adjacent energy storages
     * @param maxAmount maximum total energy to transfer
     * @param extract   true to extract from targets, false to insert into targets
     * @return total amount actually transferred
     */
    private long transferEnergy(List<EnergyStorage> targets, long maxAmount, boolean extract) {
        if (targets.isEmpty() || maxAmount <= 0) return 0;

        // Filter to only targets that support the operation
        List<EnergyStorage> validTargets = new ArrayList<>();
        for (EnergyStorage target : targets) {
            if (extract ? target.supportsExtraction() : target.supportsInsertion()) {
                validTargets.add(target);
            }
        }
        if (validTargets.isEmpty()) return 0;

        // Shuffle to avoid directional bias
        Collections.shuffle(validTargets);

        try (Transaction transaction = Transaction.openOuter()) {
            long totalTransferred = 0;

            for (int i = 0; i < validTargets.size(); i++) {
                EnergyStorage target = validTargets.get(i);
                int remainingTargets = validTargets.size() - i;
                long remaining = maxAmount - totalTransferred;

                // Fair share: divide remaining evenly, capped at cable transfer rate
                long targetMax = Math.min(remaining / remainingTargets, MAX_TRANSFER_PER_DEVICE);

                long transferred = extract
                        ? target.extract(targetMax, transaction)
                        : target.insert(targetMax, transaction);

                totalTransferred += transferred;
            }

            transaction.commit();
            return totalTransferred;
        }
    }

    private boolean isManagedPushSource(BlockEntity blockEntity) {
        return blockEntity instanceof AbstractEngineBlockEntity;
    }
}
