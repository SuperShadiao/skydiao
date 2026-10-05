package pers.XiaoShadiao.skydiao.server.mixin.fun;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChorusFlowerBlock.class)
public class MixinChorusFlower extends Block {

    @Shadow
    public static final IntegerProperty AGE = BlockStateProperties.AGE_5;

    public MixinChorusFlower(Properties properties) {
        super(properties);
    }

    @WrapMethod(method = "placeDeadFlower")
    private void placeDeadFlower(Level level, BlockPos pos, Operation<Void> original) {
        level.setBlock(pos, this.defaultBlockState().setValue(AGE, 0), 2);

    }

}
