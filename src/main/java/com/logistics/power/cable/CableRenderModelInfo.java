package com.logistics.power.cable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.logistics.LogisticsPower;
import com.logistics.core.lib.resource.ResourceId;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class CableRenderModelInfo {
    private static final String LOOKUP_RESOURCE = "/assets/logistics/power/cable_render_lookup.json";
    private static final Gson GSON = new GsonBuilder().create();

    private static final int NORTH_MASK = 1;
    private static final int EAST_MASK = 1 << 1;
    private static final int SOUTH_MASK = 1 << 2;
    private static final int WEST_MASK = 1 << 3;
    private static final int UP_MASK = 1 << 4;
    private static final int DOWN_MASK = 1 << 5;
    private static final int ALL_CONNECTIONS_MASK = NORTH_MASK | EAST_MASK | SOUTH_MASK | WEST_MASK | UP_MASK | DOWN_MASK;
    private static final ResourceId PLUG_MODEL = model("cable_plug_n");

    private static final RenderLookup LOOKUP = loadLookup();

    private CableRenderModelInfo() {}

    public static ModelInfo resolve(Level level, BlockPos pos, BlockState state) {
        return LOOKUP.modelFor(connectionMask(level, pos, state));
    }

    public static List<ModelInfo> plugModels(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof CableBlock cableBlock)) return List.of();

        List<ModelInfo> plugs = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            if (cableBlock.getConnectionType(level, pos, direction) == CableBlock.ConnectionType.DEVICE) {
                plugs.add(plugModel(direction));
            }
        }
        return List.copyOf(plugs);
    }

    public static ModelInfo plugModel(Direction direction) {
        return new ModelInfo(PLUG_MODEL, plugRotation(direction));
    }

    public static List<ResourceId> modelIds() {
        return LOOKUP.modelIds();
    }

    private static int connectionMask(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof CableBlock cableBlock)) return 0;

        int mask = 0;
        for (Direction direction : Direction.values()) {
            if (rendersCableArm(cableBlock.getConnectionType(level, pos, direction))) {
                mask |= maskFor(direction);
            }
        }
        return mask;
    }

    static boolean rendersCableArm(CableBlock.ConnectionType connectionType) {
        return connectionType != CableBlock.ConnectionType.NONE;
    }

    private static RenderLookup loadLookup() {
        try (InputStream stream = CableRenderModelInfo.class.getResourceAsStream(LOOKUP_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing cable render lookup: " + LOOKUP_RESOURCE);
            }

            JsonObject root = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
            ResourceId isolatedCable = model(root.get("isolated").getAsString());
            Map<String, ResourceId> models = parseModels(root.getAsJsonObject("models"));
            ModelInfo[] modelsByMask = parseMasks(root.getAsJsonObject("masks"), models);

            validateLookup(modelsByMask);
            return new RenderLookup(new ModelInfo(isolatedCable, null), modelsByMask, modelIds(isolatedCable, modelsByMask));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load cable render lookup", e);
        }
    }

    private static Map<String, ResourceId> parseModels(JsonObject json) {
        Map<String, ResourceId> models = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            models.put(entry.getKey(), model(entry.getValue().getAsString()));
        }
        return models;
    }

    private static ModelInfo[] parseMasks(JsonObject json, Map<String, ResourceId> models) {
        ModelInfo[] lookup = new ModelInfo[ALL_CONNECTIONS_MASK + 1];
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            int mask = Integer.parseInt(entry.getKey(), 16);
            JsonArray spec = entry.getValue().getAsJsonArray();
            ResourceId modelId = models.get(spec.get(0).getAsString());
            if (modelId == null) {
                throw new IllegalArgumentException("Unknown cable topology key: " + spec.get(0).getAsString());
            }
            lookup[mask] = new ModelInfo(modelId, rotation(spec));
        }
        return lookup;
    }

    private static @Nullable ModelRotation rotation(JsonArray spec) {
        if (spec.size() == 1) return null;

        List<ModelRotation.RotationStep> steps = new ArrayList<>();
        for (int i = 1; i < spec.size(); i++) {
            steps.add(rotationStep(spec.get(i).getAsString()));
        }
        return ModelRotation.ordered(steps);
    }

    private static ModelRotation.RotationStep rotationStep(String token) {
        if (token.length() < 2) {
            throw new IllegalArgumentException("Invalid cable rotation token: " + token);
        }

        ModelRotation.RotationAxis axis = switch (token.charAt(0)) {
            case 'x' -> ModelRotation.RotationAxis.X;
            case 'y' -> ModelRotation.RotationAxis.Y;
            case 'z' -> ModelRotation.RotationAxis.Z;
            default -> throw new IllegalArgumentException("Invalid cable rotation axis: " + token);
        };
        return ModelRotation.step(axis, Integer.parseInt(token.substring(1)));
    }

    private static void validateLookup(ModelInfo[] modelsByMask) {
        for (int mask = 1; mask <= ALL_CONNECTIONS_MASK; mask++) {
            if (modelsByMask[mask] == null) {
                throw new IllegalStateException("Missing cable render entry for mask 0x" + Integer.toHexString(mask));
            }
        }
    }

    private static List<ResourceId> modelIds(ResourceId isolatedCable, ModelInfo[] modelsByMask) {
        Set<ResourceId> ids = new LinkedHashSet<>();
        ids.add(isolatedCable);
        for (int mask = 1; mask <= ALL_CONNECTIONS_MASK; mask++) {
            ids.add(modelsByMask[mask].modelId);
        }
        ids.add(PLUG_MODEL);
        return List.copyOf(ids);
    }

    private static ResourceId model(String name) {
        return LogisticsPower.model(name);
    }

    private static @Nullable ModelRotation plugRotation(Direction direction) {
        return switch (direction) {
            case NORTH -> null;
            case EAST -> ModelRotation.ordered(List.of(ModelRotation.step(ModelRotation.RotationAxis.Y, 3)));
            case SOUTH -> ModelRotation.ordered(List.of(ModelRotation.step(ModelRotation.RotationAxis.Y, 2)));
            case WEST -> ModelRotation.ordered(List.of(ModelRotation.step(ModelRotation.RotationAxis.Y, 1)));
            case UP -> ModelRotation.ordered(List.of(ModelRotation.step(ModelRotation.RotationAxis.X, 1)));
            case DOWN -> ModelRotation.ordered(List.of(ModelRotation.step(ModelRotation.RotationAxis.X, 3)));
        };
    }

    private static int maskFor(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH_MASK;
            case EAST -> EAST_MASK;
            case SOUTH -> SOUTH_MASK;
            case WEST -> WEST_MASK;
            case UP -> UP_MASK;
            case DOWN -> DOWN_MASK;
        };
    }

    private record RenderLookup(ModelInfo isolatedCable, ModelInfo[] modelsByMask, List<ResourceId> modelIds) {
        private ModelInfo modelFor(int mask) {
            if (mask == 0 || mask >= modelsByMask.length) return isolatedCable;
            return modelsByMask[mask];
        }
    }

    public record ModelInfo(ResourceId modelId, @Nullable ModelRotation rotation) {}

    public record ModelRotation(List<RotationStep> steps) {
        private static RotationStep step(RotationAxis axis, int quarterTurns) {
            return new RotationStep(axis, Math.floorMod(quarterTurns, 4));
        }

        private static @Nullable ModelRotation ordered(List<RotationStep> steps) {
            List<RotationStep> normalizedSteps = new ArrayList<>();
            for (RotationStep step : steps) {
                if (!step.isNone()) {
                    normalizedSteps.add(step);
                }
            }
            return normalizedSteps.isEmpty() ? null : new ModelRotation(List.copyOf(normalizedSteps));
        }

        public static int degrees(int quarterTurns) {
            int normalized = Math.floorMod(quarterTurns, 4);
            return normalized == 3 ? -90 : normalized * 90;
        }

        public enum RotationAxis {
            X,
            Y,
            Z
        }

        public record RotationStep(RotationAxis axis, int quarterTurns) {
            public boolean isNone() {
                return quarterTurns == 0;
            }

            public int degrees() {
                return ModelRotation.degrees(quarterTurns);
            }
        }
    }
}
