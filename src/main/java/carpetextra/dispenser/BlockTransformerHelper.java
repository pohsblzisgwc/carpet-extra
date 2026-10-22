package carpetextra.dispenser;

import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockTransformerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.stateprovider.BlockStateProvider;
import net.minecraft.world.gen.stateprovider.CopyPropertiesProvider;
import net.minecraft.world.gen.stateprovider.RuleBasedStateProvider;
import net.minecraft.world.gen.stateprovider.SimpleBlockStateProvider;

public final class BlockTransformerHelper {
    private BlockTransformerHelper() {
    }

    public static boolean hasTransformer(ItemStack stack, RegistryKey<BlockTransformerComponent> key) {
        var transformer = stack.get(DataComponentTypes.BLOCK_TRANSFORMER);
        return transformer != null && transformer.matchesKey(key);
    }

    public static boolean canTransform(ItemStack stack, ServerWorld world, BlockPos pos) {
        var transformer = stack.get(DataComponentTypes.BLOCK_TRANSFORMER);
        return transformer != null && transformer.value().transforms().stream()
                .anyMatch(transform -> transform.blockStateProvider().value().getOrNull(world, world.getRandom(), pos) != null);
    }

    // Keep tools in the dispenser when the block has already been transformed.
    // Read the current component instead of caching tables across data pack reloads.
    public static boolean isTransformResult(ItemStack stack, Block block) {
        var transformer = stack.get(DataComponentTypes.BLOCK_TRANSFORMER);
        return transformer != null && transformer.value().transforms().stream()
                .anyMatch(transform -> providesBlock(transform.blockStateProvider().value(), block));
    }

    private static boolean providesBlock(BlockStateProvider provider, Block block) {
        return switch (provider) {
            case SimpleBlockStateProvider simple -> simple.state().is(block);
            case CopyPropertiesProvider copy -> providesBlock(copy.sourceBlockStateProvider().value(), block);
            case RuleBasedStateProvider rules ->
                    (rules.fallback() != null && providesBlock(rules.fallback().value(), block))
                    || rules.rules().stream().anyMatch(rule -> providesBlock(rule.then().value(), block));
            default -> false;
        };
    }
}
