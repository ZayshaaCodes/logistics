package com.logistics.power.render;

import com.logistics.LogisticsPower;
import com.logistics.LogisticsPowerClient;
import com.logistics.core.lib.resource.ResourceId;
import com.logistics.power.cable.CableBlock;
import com.logistics.power.cable.CableBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders energy cables with core + directional arm models,
 * following the same pattern as pipes.
 */
public class CableBlockEntityRenderer implements BlockEntityRenderer<CableBlockEntity, CableRenderState> {

    private final FabricBakedModelManager modelManager;
    private final Map<ResourceId, BlockStateModel> modelsCache = new HashMap<>();

    private static final ResourceId CORE_MODEL = LogisticsPower.model("cable_core");
    private static final ResourceId ARM_MODEL = LogisticsPower.model("cable_arm");
    private static final ResourceId ARM_EXTENDED_MODEL = LogisticsPower.model("cable_arm_extended");

    public CableBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.modelManager = (FabricBakedModelManager) Minecraft.getInstance().getModelManager();
    }

    private BlockStateModel getModel(ResourceId modelId) {
        ExtraModelKey<BlockStateModel> key = LogisticsPowerClient.MODEL.getKey(modelId);
        if (key == null) return null;
        BlockStateModel model = modelManager.getModel(key);
        if (model == null || model == Minecraft.getInstance().getModelManager().getMissingBlockStateModel()) {
            return null;
        }
        return model;
    }

    @Override
    public CableRenderState createRenderState() {
        return new CableRenderState();
    }

    @Override
    public void extractRenderState(
            CableBlockEntity entity, CableRenderState state, float tickDelta,
            Vec3 cameraPos,
            net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {

        BlockEntityRenderState.extractBase(entity, state, crumblingOverlay);

        BlockState blockState = entity.getBlockState();
        state.blockState = blockState;
        state.models.clear();

        // Core model
        state.models.add(new CableRenderState.ModelRenderInfo(CORE_MODEL));

        // Arm models per direction
        for (Direction direction : Direction.values()) {
            CableBlock.ConnectionType type = entity.getCachedConnectionType(direction);
            if (type == CableBlock.ConnectionType.NONE) continue;

            ResourceId armModel = type == CableBlock.ConnectionType.DEVICE ? ARM_EXTENDED_MODEL : ARM_MODEL;
            state.models.add(new CableRenderState.ModelRenderInfo(armModel, direction));
        }

        // Populate model references from cache
        for (CableRenderState.ModelRenderInfo modelInfo : state.models) {
            BlockStateModel cached = modelsCache.get(modelInfo.modelId);
            if (cached == null) {
                cached = getModel(modelInfo.modelId);
                if (cached == null) {
                    modelInfo.model = null;
                    continue;
                }
                modelsCache.put(modelInfo.modelId, cached);
            }
            modelInfo.model = cached;
        }
    }

    @Override
    public void submit(
            CableRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {

        if (state.models.isEmpty()) return;

        RenderType renderLayer = state.blockState == null
                ? RenderTypes.cutoutMovingBlock()
                : ItemBlockRenderTypes.getRenderType(state.blockState);

        for (CableRenderState.ModelRenderInfo modelInfo : state.models) {
            if (modelInfo.model == null) continue;

            if (modelInfo.armDirection != null) {
                matrices.pushPose();
                matrices.translate(0.5, 0.5, 0.5);
                applyDirectionRotation(matrices, modelInfo.armDirection);
                matrices.translate(-0.5, -0.5, -0.5);
            }

            queue.submitBlockModel(
                    matrices, renderLayer, modelInfo.model,
                    1.0f, 1.0f, 1.0f,
                    state.lightCoords, OverlayTexture.NO_OVERLAY, 0);

            if (modelInfo.armDirection != null) {
                matrices.popPose();
            }
        }
    }

    private static void applyDirectionRotation(PoseStack matrices, Direction direction) {
        switch (direction) {
            case SOUTH -> matrices.mulPose(Axis.YP.rotationDegrees(180));
            case EAST -> matrices.mulPose(Axis.YP.rotationDegrees(-90));
            case WEST -> matrices.mulPose(Axis.YP.rotationDegrees(90));
            case UP -> matrices.mulPose(Axis.XP.rotationDegrees(90));
            case DOWN -> matrices.mulPose(Axis.XP.rotationDegrees(-90));
            default -> {}
        }
    }
}
