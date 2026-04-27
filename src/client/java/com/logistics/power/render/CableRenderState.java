package com.logistics.power.render;

import com.logistics.core.lib.resource.ResourceId;
import com.logistics.power.cable.CableRenderModelInfo;
import java.util.List;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class CableRenderState extends BlockEntityRenderState {
    public BlockState blockState;
    public @Nullable ResourceId modelId;
    public @Nullable CableRenderModelInfo.ModelRotation rotation;
    public @Nullable BlockStateModel model;
    public List<RenderedModel> plugModels = List.of();

    public record RenderedModel(
            ResourceId modelId,
            BlockStateModel model,
            @Nullable CableRenderModelInfo.ModelRotation rotation) {}
}
