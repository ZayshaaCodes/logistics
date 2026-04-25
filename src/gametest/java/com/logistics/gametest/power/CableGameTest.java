package com.logistics.gametest.power;

import com.logistics.LogisticsCore;
import com.logistics.LogisticsPower;
import com.logistics.core.macerator.MaceratorBlockEntity;
import com.logistics.core.lib.power.AbstractEngineBlock;
import com.logistics.power.cable.CableBlockEntity;
import com.logistics.power.engine.block.entity.RedstoneEngineBlockEntity;
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
            if (!storage.supportsExtraction()) {
                context.fail("Energy cable should support extraction from " + direction);
                return;
            }
            if (storage.getCapacity() != 640L) {
                context.fail("Energy cable capacity should be 640 RF, got: " + storage.getCapacity());
                return;
            }
        }

        context.succeed();
    }

    @GameTest(maxTicks = 30)
    public void testStoredCableEnergyReachesMachine(GameTestHelper context) {
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

        sourceCable.setStoredEnergy(640L);

        context.runAfterDelay(10, () -> {
            long machineEnergy = machine.energyStorage(Direction.WEST).getAmount();
            if (machineEnergy <= 0) {
                context.fail("Cable network should move stored cable energy into machine, got: " + machineEnergy);
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

        context.setBlock(firstCablePos, LogisticsPower.BLOCK.CABLE);
        context.setBlock(secondCablePos, LogisticsPower.BLOCK.CABLE);
        context.setBlock(enginePos, LogisticsPower.BLOCK.CREATIVE_ENGINE
                .defaultBlockState()
                .setValue(AbstractEngineBlock.FACING, Direction.EAST)
                .setValue(AbstractEngineBlock.POWERED, true));

        CableBlockEntity firstCable = context.getBlockEntity(firstCablePos, CableBlockEntity.class);
        CableBlockEntity secondCable = context.getBlockEntity(secondCablePos, CableBlockEntity.class);
        if (firstCable == null || secondCable == null) {
            context.fail("Expected cable block entities");
            return;
        }

        context.runAfterDelay(20, () -> {
            long totalCableEnergy = firstCable.getStoredEnergy() + secondCable.getStoredEnergy();
            if (totalCableEnergy <= 0) {
                context.fail("Creative engine should push energy into cable network");
                return;
            }
            if (secondCable.getStoredEnergy() <= 0) {
                context.fail("Cable network should spread engine energy to connected cables");
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

            sourceCable.setStoredEnergy(640L);
            downstreamCable.setStoredEnergy(0L);

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
            if (cable.getStoredEnergy() != 0) {
                context.fail("Cable should wait for engine-side push timing, got: " + cable.getStoredEnergy());
                return;
            }
            context.succeed();
        });
    }
}
