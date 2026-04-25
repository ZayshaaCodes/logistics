package com.logistics.gametest.power;

import com.logistics.LogisticsCore;
import com.logistics.LogisticsPower;
import com.logistics.core.macerator.MaceratorBlockEntity;
import com.logistics.core.lib.power.AbstractEngineBlock;
import com.logistics.power.cable.CableBlock;
import com.logistics.power.cable.CableBlockEntity;
import com.logistics.power.engine.block.entity.RedstoneEngineBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import team.reborn.energy.api.EnergyStorage;

/**
 * Game tests for power cables.
 */
public class CableGameTest {
    @GameTest
    public void testCablePlacementExposesEnergyStorage(GameTestHelper context) {
        BlockPos cablePos = new BlockPos(1, 1, 1);

        context.setBlock(cablePos, LogisticsPower.BLOCK.CABLE);

        CableBlockEntity cable = context.getBlockEntity(cablePos, CableBlockEntity.class);
        if (cable == null) {
            context.fail("Energy cable should have block entity");
            return;
        }

        for (Direction direction : Direction.values()) {
            EnergyStorage storage = cable.energyStorage(direction);
            if (storage == null) {
                context.fail("Energy cable should expose storage from " + direction);
                return;
            }
            if (!storage.supportsInsertion()) {
                context.fail("Energy cable should support insertion from " + direction);
                return;
            }
            if (storage.supportsExtraction()) {
                context.fail("Energy cable should not expose extractable battery storage from " + direction);
                return;
            }
            if (storage.getAmount() != 0L || storage.getCapacity() != 0L) {
                context.fail("Energy cable should report zero stored energy and capacity");
                return;
            }
        }

        context.succeed();
    }

    @GameTest(maxTicks = 30)
    public void testInsertedCableEnergyPassesThroughToMachine(GameTestHelper context) {
        BlockPos sourceCablePos = new BlockPos(1, 1, 1);
        BlockPos relayCablePos = new BlockPos(2, 1, 1);
        BlockPos machinePos = new BlockPos(3, 1, 1);

        context.setBlock(sourceCablePos, LogisticsPower.BLOCK.CABLE);
        context.setBlock(relayCablePos, LogisticsPower.BLOCK.CABLE);
        context.setBlock(machinePos, LogisticsCore.BLOCK.MACERATOR);

        CableBlockEntity sourceCable = context.getBlockEntity(sourceCablePos, CableBlockEntity.class);
        MaceratorBlockEntity machine = context.getBlockEntity(machinePos, MaceratorBlockEntity.class);
        if (sourceCable == null || machine == null) {
            context.fail("Expected cable and machine block entities");
            return;
        }

        EnergyStorage cableStorage = sourceCable.energyStorage(Direction.WEST);
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = cableStorage.insert(640L, transaction);
            if (inserted <= 0) {
                context.fail("Cable network should pass inserted energy through to the machine");
                return;
            }
            transaction.commit();
        }

        context.runAfterDelay(1, () -> {
            long machineEnergy = machine.energyStorage(Direction.WEST).getAmount();
            if (machineEnergy <= 0) {
                context.fail("Cable network should deliver inserted energy to machine, got: " + machineEnergy);
                return;
            }
            if (sourceCable.energyStorage(Direction.WEST).getAmount() != 0) {
                context.fail("Cable should not retain delivered energy");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void testCreativeEnginePowersCableNetwork(GameTestHelper context) {
        BlockPos enginePos = new BlockPos(0, 1, 1);
        BlockPos firstCablePos = new BlockPos(1, 1, 1);
        BlockPos secondCablePos = new BlockPos(2, 1, 1);
        BlockPos machinePos = new BlockPos(3, 1, 1);

        context.setBlock(firstCablePos, LogisticsPower.BLOCK.CABLE);
        context.setBlock(secondCablePos, LogisticsPower.BLOCK.CABLE);
        context.setBlock(machinePos, LogisticsCore.BLOCK.MACERATOR);
        context.setBlock(enginePos, LogisticsPower.BLOCK.CREATIVE_ENGINE
                .defaultBlockState()
                .setValue(AbstractEngineBlock.FACING, Direction.EAST)
                .setValue(AbstractEngineBlock.POWERED, true));

        CableBlockEntity firstCable = context.getBlockEntity(firstCablePos, CableBlockEntity.class);
        CableBlockEntity secondCable = context.getBlockEntity(secondCablePos, CableBlockEntity.class);
        MaceratorBlockEntity machine = context.getBlockEntity(machinePos, MaceratorBlockEntity.class);
        if (firstCable == null || secondCable == null || machine == null) {
            context.fail("Expected cable and machine block entities");
            return;
        }

        context.runAfterDelay(20, () -> {
            long machineEnergy = machine.energyStorage(Direction.WEST).getAmount();
            if (machineEnergy <= 0) {
                context.fail("Creative engine should power machine through cable network");
                return;
            }
            if (firstCable.energyStorage(Direction.WEST).getAmount() != 0
                    || secondCable.energyStorage(Direction.WEST).getAmount() != 0) {
                context.fail("Cables should not retain creative engine energy");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void testCableNetworkStopsAtRemovedCable(GameTestHelper context) {
        BlockPos sourceCablePos = new BlockPos(1, 1, 1);
        BlockPos removedCablePos = new BlockPos(2, 1, 1);
        BlockPos downstreamCablePos = new BlockPos(3, 1, 1);
        BlockPos machinePos = new BlockPos(4, 1, 1);

        context.setBlock(sourceCablePos, LogisticsPower.BLOCK.CABLE);
        context.setBlock(removedCablePos, LogisticsPower.BLOCK.CABLE);
        context.setBlock(downstreamCablePos, LogisticsPower.BLOCK.CABLE);
        context.setBlock(machinePos, LogisticsCore.BLOCK.MACERATOR);

        context.runAfterDelay(2, () -> {
            context.setBlock(removedCablePos, Blocks.AIR);

            CableBlockEntity sourceCable = context.getBlockEntity(sourceCablePos, CableBlockEntity.class);
            CableBlockEntity downstreamCable = context.getBlockEntity(downstreamCablePos, CableBlockEntity.class);
            MaceratorBlockEntity machine = context.getBlockEntity(machinePos, MaceratorBlockEntity.class);
            if (sourceCable == null || downstreamCable == null || machine == null) {
                context.fail("Expected source cable, downstream cable, and machine block entities");
                return;
            }

            EnergyStorage sourceStorage = sourceCable.energyStorage(Direction.WEST);
            try (Transaction transaction = Transaction.openOuter()) {
                long inserted = sourceStorage.insert(640L, transaction);
                if (inserted != 0) {
                    context.fail("Split cable network should reject energy without connected consumers, got: " + inserted);
                    return;
                }
                transaction.commit();
            }

            context.runAfterDelay(10, () -> {
                long machineEnergy = machine.energyStorage(Direction.WEST).getAmount();
                if (machineEnergy != 0) {
                    context.fail("Removed cable should split network before energy reaches machine, got: " + machineEnergy);
                    return;
                }
                context.succeed();
            });
        });
    }

    @GameTest
    public void testCableConnectionUpdatesWhenNeighborOutputRotates(GameTestHelper context) {
        BlockPos enginePos = new BlockPos(1, 1, 1);
        BlockPos cablePos = new BlockPos(2, 1, 1);

        context.setBlock(cablePos, LogisticsPower.BLOCK.CABLE);
        context.setBlock(enginePos, LogisticsPower.BLOCK.CREATIVE_ENGINE
                .defaultBlockState()
                .setValue(AbstractEngineBlock.FACING, Direction.EAST)
                .setValue(AbstractEngineBlock.POWERED, true));

        CableBlockEntity cable = context.getBlockEntity(cablePos, CableBlockEntity.class);
        if (cable == null) {
            context.fail("Expected cable block entity");
            return;
        }

        if (cable.getCachedConnectionType(Direction.WEST) != CableBlock.ConnectionType.DEVICE) {
            context.fail("Cable should initially connect to engine output");
            return;
        }

        context.setBlock(enginePos, LogisticsPower.BLOCK.CREATIVE_ENGINE
                .defaultBlockState()
                .setValue(AbstractEngineBlock.FACING, Direction.NORTH)
                .setValue(AbstractEngineBlock.POWERED, true));

        if (cable.getCachedConnectionType(Direction.WEST) != CableBlock.ConnectionType.NONE) {
            context.fail("Cable connection cache should update when neighboring output rotates away");
            return;
        }

        context.succeed();
    }

    @GameTest(maxTicks = 30)
    public void testRedstoneEngineIsNotPulledByCableNetwork(GameTestHelper context) {
        BlockPos enginePos = new BlockPos(1, 1, 1);
        BlockPos cablePos = new BlockPos(2, 1, 1);

        context.setBlock(cablePos, LogisticsPower.BLOCK.CABLE);
        context.setBlock(enginePos, LogisticsPower.BLOCK.REDSTONE_ENGINE
                .defaultBlockState()
                .setValue(AbstractEngineBlock.FACING, Direction.EAST)
                .setValue(AbstractEngineBlock.POWERED, true));

        context.runAfterDelay(20, () -> {
            RedstoneEngineBlockEntity engine = context.getBlockEntity(enginePos, RedstoneEngineBlockEntity.class);
            CableBlockEntity cable = context.getBlockEntity(cablePos, CableBlockEntity.class);
            if (engine == null || cable == null) {
                context.fail("Expected redstone engine and cable block entities");
                return;
            }
            if (engine.getEnergy() <= 0) {
                context.fail("Cable network should not pull directly from redstone engine buffer");
                return;
            }
            if (cable.energyStorage(Direction.WEST).getAmount() != 0) {
                context.fail("Cable should not buffer redstone engine energy");
                return;
            }
            context.succeed();
        });
    }
}
