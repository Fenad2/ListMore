package com.listmore.compat.litematica.schematic.preview.render;

import com.listmore.compat.litematica.schematic.preview.model.SchematicPreviewModel;
import fi.dy.masa.litematica.world.FakeLightingProvider;

//#if MC >= 26.1
//$$ import net.minecraft.client.renderer.block.BlockAndTintGetter;
//#else
import net.minecraft.world.level.BlockAndTintGetter;
//#endif
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
//#if MC >= 26.1
//$$ import net.minecraft.world.level.CardinalLighting;
//#else
import net.minecraft.core.Direction;
//#endif
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

// 构建预览方块网格时使用的只读世界视图
final class SchematicPreviewWorld implements BlockAndTintGetter {
	private final Minecraft minecraft;
	private final LevelLightEngine lightEngine;
	private SchematicPreviewModel model;

	SchematicPreviewWorld(Minecraft minecraft) {
		this.minecraft = minecraft;
		this.lightEngine = new FakeLightingProvider(minecraft.level.getChunkSource());
	}

	public void setModel(SchematicPreviewModel model) {
		this.model = model;
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		if (this.model == null) {
			return Blocks.AIR.defaultBlockState();
		}
		return this.model.blockStateAt(pos.getX(), pos.getY(), pos.getZ());
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return this.getBlockState(pos).getFluidState();
	}

	@Nullable
	@Override
	public BlockEntity getBlockEntity(BlockPos pos) {
		return null;
	}

	@Override
	public int getHeight() {
		return this.model != null ? Math.max(1, this.model.size().getY()) : 1;
	}

	@Override
	public int getMinY() {
		return 0;
	}

	@Override
	public LevelLightEngine getLightEngine() {
		return this.lightEngine;
	}

	//#if MC >= 26.1
	//$$ @Override
	//$$ public CardinalLighting cardinalLighting() {
	//$$ 	return CardinalLighting.DEFAULT;
	//$$ }
	//#else
	@Override
	public float getShade(Direction direction, boolean shaded) {
		return this.minecraft.level.getShade(direction, shaded);
	}
	//#endif

	@Override
	public int getBlockTint(BlockPos pos, ColorResolver resolver) {
		return resolver.getColor(this.minecraft.level.getBiome(pos).value(), pos.getX(), pos.getZ());
	}
}
