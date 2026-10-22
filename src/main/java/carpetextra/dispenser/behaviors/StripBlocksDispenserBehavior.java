package carpetextra.dispenser.behaviors;

import carpetextra.dispenser.BlockTransformerHelper;
import carpetextra.dispenser.DispenserItemUsageContext;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.FallibleItemDispenserBehavior;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPointer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class StripBlocksDispenserBehavior extends FallibleItemDispenserBehavior {
    @Override
    protected ItemStack dispenseSilently(BlockPointer pointer, ItemStack stack) {
        this.setSuccess(true);
        ServerWorld world = pointer.world();
        Direction dispenserFacing = pointer.state().get(DispenserBlock.FACING);
        BlockPos frontBlockPos = pointer.pos().offset(dispenserFacing);

        // check if axe can be used on block
        if(BlockTransformerHelper.canTransform(stack, world, frontBlockPos)) {
            BlockHitResult hitResult = new BlockHitResult(Vec3d.ofCenter(frontBlockPos), dispenserFacing.getOpposite(), frontBlockPos, false);
            ItemUsageContext context = new DispenserItemUsageContext(world, stack, hitResult);

            // use on block, test if sucessful
            if(stack.getItem().useOnBlock(context).isAccepted()) {
                // damage axe, remove if broken
                stack.damage(1, world, null, (_) -> stack.setCount(0));
                return stack;
            }
        }

        // fail to dispense
        this.setSuccess(false);
        return stack;
    }

}
