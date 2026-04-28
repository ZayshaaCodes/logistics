package com.logistics.power.cable;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class CableRenderModelInfoTest {
    private static final String[] BASE_MODEL_NAMES = {
        "cable_topology_blank",
        "cable_topology_n",
        "cable_topology_ne",
        "cable_topology_ns",
        "cable_topology_nes",
        "cable_topology_nesw",
        "cable_topology_neu",
        "cable_topology_nesu",
        "cable_topology_neswu",
        "cable_topology_neswud",
        "cable_plug_n"
    };

    @Test
    void modelIdsLoadsTieredRenderModels() {
        assertThat(CableRenderModelInfo.modelIds())
                .extracting(Object::toString)
                .containsExactlyElementsOf(expectedTieredModelIds());
    }

    @Test
    void plugModelUsesExpectedDirectionRotations() {
        assertThat(CableRenderModelInfo.plugModel(Direction.NORTH).rotation()).isNull();
        assertSingleRotation(Direction.EAST, CableRenderModelInfo.ModelRotation.RotationAxis.Y, 3);
        assertSingleRotation(Direction.SOUTH, CableRenderModelInfo.ModelRotation.RotationAxis.Y, 2);
        assertSingleRotation(Direction.WEST, CableRenderModelInfo.ModelRotation.RotationAxis.Y, 1);
        assertSingleRotation(Direction.UP, CableRenderModelInfo.ModelRotation.RotationAxis.X, 1);
        assertSingleRotation(Direction.DOWN, CableRenderModelInfo.ModelRotation.RotationAxis.X, 3);
    }

    @Test
    void deviceConnectionsStillRenderCableArms() {
        assertThat(CableRenderModelInfo.rendersCableArm(CableBlock.ConnectionType.CABLE)).isTrue();
        assertThat(CableRenderModelInfo.rendersCableArm(CableBlock.ConnectionType.DEVICE)).isTrue();
        assertThat(CableRenderModelInfo.rendersCableArm(CableBlock.ConnectionType.NONE)).isFalse();
    }

    @Test
    void cableTiersUseExpectedTransferRates() {
        assertThat(CableTier.COPPER.transferRate()).isEqualTo(30);
        assertThat(CableTier.GOLD.transferRate()).isEqualTo(60);
        assertThat(CableTier.ENDER.transferRate()).isEqualTo(120);
    }

    @Test
    void cableTopologyResourcesAreOnClasspath() {
        for (String modelName : BASE_MODEL_NAMES) {
            assertThat(resourceExists("/assets/logistics/models/block/power/" + modelName + ".json"))
                    .as("base " + modelName)
                    .isTrue();
        }
        for (CableTier tier : CableTier.values()) {
            for (String modelName : BASE_MODEL_NAMES) {
                String tieredModel = tier.modelName(modelName);
                assertThat(resourceExists("/assets/logistics/models/block/power/" + tieredModel + ".json"))
                        .as(tieredModel)
                        .isTrue();
            }
            assertThat(resourceExists("/assets/logistics/textures/block/power/" + tier.id() + ".png"))
                    .as(tier.id())
                    .isTrue();
        }
    }

    private static List<String> expectedTieredModelIds() {
        List<String> modelIds = new ArrayList<>();
        for (CableTier tier : CableTier.values()) {
            for (String modelName : BASE_MODEL_NAMES) {
                modelIds.add("logistics:block/power/" + tier.modelName(modelName));
            }
        }
        return modelIds;
    }

    private static boolean resourceExists(String path) {
        return CableRenderModelInfoTest.class.getResource(path) != null;
    }

    private static void assertSingleRotation(
            Direction direction,
            CableRenderModelInfo.ModelRotation.RotationAxis axis,
            int quarterTurns) {
        assertThat(CableRenderModelInfo.plugModel(direction).rotation().steps())
                .containsExactly(new CableRenderModelInfo.ModelRotation.RotationStep(axis, quarterTurns));
    }
}
