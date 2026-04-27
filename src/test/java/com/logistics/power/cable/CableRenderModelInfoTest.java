package com.logistics.power.cable;

import static org.assertj.core.api.Assertions.assertThat;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class CableRenderModelInfoTest {
    private static final String[] MODEL_NAMES = {
        "cable_topology_blank",
        "cable_topology_n",
        "cable_topology_ne",
        "cable_topology_ns",
        "cable_topology_nes",
        "cable_topology_neu",
        "cable_topology_nesw",
        "cable_topology_nesu",
        "cable_topology_neswu",
        "cable_topology_neswud",
        "cable_plug_n"
    };

    @Test
    void modelIdsLoadsRenderLookupJson() {
        assertThat(CableRenderModelInfo.modelIds())
                .extracting(Object::toString)
                .containsExactly(
                    "logistics:block/power/cable_topology_blank",
                        "logistics:block/power/cable_topology_n",
                        "logistics:block/power/cable_topology_ne",
                        "logistics:block/power/cable_topology_ns",
                        "logistics:block/power/cable_topology_nes",
                        "logistics:block/power/cable_topology_nesw",
                        "logistics:block/power/cable_topology_neu",
                        "logistics:block/power/cable_topology_nesu",
                        "logistics:block/power/cable_topology_neswu",
                        "logistics:block/power/cable_topology_neswud",
                        "logistics:block/power/cable_plug_n");
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
    void cableTopologyResourcesAreOnClasspath() {
        for (String modelName : MODEL_NAMES) {
            assertThat(resourceExists("/assets/logistics/models/block/power/" + modelName + ".json"))
                    .as(modelName)
                    .isTrue();
        }
        assertThat(resourceExists("/assets/logistics/textures/block/power/cable_copper_insulated.png")).isTrue();
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
