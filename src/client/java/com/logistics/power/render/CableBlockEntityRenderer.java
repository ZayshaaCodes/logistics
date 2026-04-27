package com.logistics.power.render;

import com.logistics.LogisticsPowerClient;
import com.logistics.core.lib.resource.ResourceId;
import com.logistics.power.cable.CableBlockEntity;
import com.logistics.power.cable.CableRenderModelInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class CableBlockEntityRenderer implements BlockEntityRenderer<CableBlockEntity, CableRenderState> {

    private final FabricBakedModelManager modelManager;
    private final Map<ResourceId, BlockStateModel> modelsCache = new HashMap<>();

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

    private BlockStateModel getCachedModel(ResourceId modelId) {
        BlockStateModel cached = modelsCache.get(modelId);
        if (cached == null) {
            cached = getModel(modelId);
            if (cached != null) {
                modelsCache.put(modelId, cached);
            }
        }
        return cached;
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
        CableRenderModelInfo.ModelInfo modelInfo = CableRenderModelInfo.resolve(entity.getLevel(), entity.getBlockPos(), blockState);
        BlockStateModel cached = getCachedModel(modelInfo.modelId());

        List<CableRenderState.RenderedModel> plugModels = new ArrayList<>();
        for (CableRenderModelInfo.ModelInfo plugInfo : CableRenderModelInfo.plugModels(
                entity.getLevel(), entity.getBlockPos(), blockState)) {
            BlockStateModel plugModel = getCachedModel(plugInfo.modelId());
            if (plugModel != null) {
                plugModels.add(new CableRenderState.RenderedModel(
                        plugInfo.modelId(), plugModel, plugInfo.rotation()));
            }
        }

        state.blockState = blockState;
        state.modelId = modelInfo.modelId();
        state.rotation = modelInfo.rotation();
        state.model = cached;
        state.plugModels = List.copyOf(plugModels);
    }

    @Override
    public void submit(
            CableRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {

        if (state.model == null) return;

        RenderType renderLayer = state.blockState == null
                ? RenderTypes.cutoutMovingBlock()
                : ItemBlockRenderTypes.getRenderType(state.blockState);

        submitModel(state, matrices, queue, renderLayer, state.model, state.rotation);

        for (CableRenderState.RenderedModel plugModel : state.plugModels) {
            submitModel(state, matrices, queue, renderLayer, plugModel.model(), plugModel.rotation());
        }
    }

    private static void submitModel(
            CableRenderState state, PoseStack matrices, SubmitNodeCollector queue, RenderType renderLayer,
            BlockStateModel model, CableRenderModelInfo.ModelRotation rotation) {

        if (rotation != null) {
            matrices.pushPose();
            matrices.translate(0.5, 0.5, 0.5);
            applyModelRotation(matrices, rotation);
            matrices.translate(-0.5, -0.5, -0.5);
        }

        queue.submitBlockModel(
                matrices, renderLayer, model,
                1.0f, 1.0f, 1.0f,
                state.lightCoords, OverlayTexture.NO_OVERLAY, 0);

        if (rotation != null) {
            matrices.popPose();
        }
    }

    private static void applyModelRotation(PoseStack matrices, CableRenderModelInfo.ModelRotation rotation) {
        for (CableRenderModelInfo.ModelRotation.RotationStep step : rotation.steps()) {
            if (step.degrees() == 0) continue;

            switch (step.axis()) {
                case X -> matrices.mulPose(Axis.XP.rotationDegrees(step.degrees()));
                case Y -> matrices.mulPose(Axis.YP.rotationDegrees(step.degrees()));
                case Z -> matrices.mulPose(Axis.ZP.rotationDegrees(step.degrees()));
            }
        }
    }
}
