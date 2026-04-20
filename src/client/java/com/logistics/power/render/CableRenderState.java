package com.logistics.power.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import com.logistics.core.lib.resource.ResourceId;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CableRenderState extends BlockEntityRenderState {
    public final List<ModelRenderInfo> models = new ArrayList<>();
    public BlockState blockState;

    public static final class ModelRenderInfo {
        public final ResourceId modelId;
        public final @Nullable Direction armDirection;
        @Nullable public BlockStateModel model = null;

        public ModelRenderInfo(ResourceId modelId) {
            this(modelId, null);
        }

        public ModelRenderInfo(ResourceId modelId, @Nullable Direction armDirection) {
            this.modelId = modelId;
            this.armDirection = armDirection;
        }
    }
}
