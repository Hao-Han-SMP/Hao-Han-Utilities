package vn.haohansmp.utilities.serializer;

import org.bukkit.block.BlockState;

import java.util.List;

public final class BlockSerializerRegistry {
    private final List<BlockSerializer> serializers;

    public BlockSerializerRegistry(List<BlockSerializer> serializers) {
        this.serializers = List.copyOf(serializers);
    }

    public BlockSerializer serializerFor(BlockState state) {
        return serializers.stream()
                .filter(serializer -> serializer.supports(state))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported block state: " + state.getType()));
    }
}
