package com.listmore.feature.soundSourceHighlight;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.listmore.config.ListMoreConfigs;
import com.listmore.mixin.accessor.EntityBoundSoundInstanceAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.AABB;

public final class SoundSourceHighlightManager implements SoundEventListener {
	private static final SoundSourceHighlightManager INSTANCE = new SoundSourceHighlightManager();
	private final Map<Integer, Long> highlightedEntityIds = new HashMap<>();
	private final Map<BlockPos, Long> highlightedBlockPositions = new HashMap<>();
	private ClientLevel trackedLevel;
	private boolean soundListenerRegistered;
	private SoundSourceHighlightManager() {
	}

	public static SoundSourceHighlightManager getInstance() {
		return INSTANCE;
	}

	public void registerSoundListener() {
		if (this.soundListenerRegistered) {
			return;
		}

		Minecraft.getInstance().getSoundManager().addListener(this);
		this.soundListenerRegistered = true;
	}

	@Override
	public void onPlaySound(SoundInstance sound, WeighedSoundEvents soundEvent, float range) {
		// 实体绑定的音源-> 直接高亮实体
		if (sound instanceof EntityBoundSoundInstance entityBoundSound) {
			Entity entity = ((EntityBoundSoundInstanceAccessor) entityBoundSound).listmore$getEntity();
			if (entity != null) {
				onEntitySound(entity);
			}
			return;
		}
		// 方块破坏/门的开关/音符盒/矿车等 -> 按声音自带的世界坐标定位
		onPositionalSound(sound);
	}

	// 非实体则方块
	private void onPositionalSound(SoundInstance sound) {
		SoundSource source = sound.getSource();
		if (!isHighlightableSource(source)) {
			return;
		}
		if (sound.isRelative() || sound.getVolume() <= 0.0F) {
			return;
		}

		Minecraft client = Minecraft.getInstance();

		// 玩家自己脚下的脚步声/动作音，高亮出来只会一直闪自己脚底
		if (source == SoundSource.PLAYERS && client.player != null
			&& client.player.position().distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) < 4.0D) {
			return;
		}
		if (!prepare(client)) {
			return;
		}
		if (source != SoundSource.BLOCKS) {
			Entity soundEntity = findSoundEntity(client, sound);

			if (soundEntity != null) {
				this.highlightedEntityIds.put(soundEntity.getId(), expiration(client.level));
				return;
			}
		}

		BlockPos position = BlockPos.containing(sound.getX(), sound.getY(), sound.getZ());
		// 方块不存在则return
		if (client.level.getBlockState(position).isAir()) {
			return;
		}
		this.highlightedBlockPositions.put(position, expiration(client.level));
	}

	// 用音源坐标匹配附近的实体
	private static Entity findSoundEntity(Minecraft client, SoundInstance sound) {
		if (client.level == null || client.player == null) {
			return null;
		}
		double x = sound.getX();
		double y = sound.getY();
		double z = sound.getZ();
		AABB searchArea = new AABB(x - 1.0D, y - 1.0D, z - 1.0D, x + 1.0D, y + 1.0D, z + 1.0D);
		Entity nearest = null;
		double nearestDistance = Double.MAX_VALUE;

		for (Entity candidate : client.level.getEntitiesOfClass(Entity.class, searchArea,
			entity -> entity != client.player && !entity.isRemoved()
				&& entity.getBoundingBox().inflate(0.3D).contains(x, y, z))) {
			double distance = candidate.position().distanceToSqr(x, y, z);

			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = candidate;
			}
		}
		return nearest;
	}

	// 过滤音乐/界面/环境循环音
	private static boolean isHighlightableSource(SoundSource source) {
		return source == SoundSource.BLOCKS || source == SoundSource.HOSTILE
			|| source == SoundSource.NEUTRAL || source == SoundSource.PLAYERS;
	}

	// 由 ClientboundSoundEntityPacket 触发
	public void onSoundEntityPacket(int entityId, float volume) {
		Minecraft client = Minecraft.getInstance();

		if (client.level == null || volume <= 0.0F) {
			return;
		}

		Entity entity = client.level.getEntity(entityId);

		if (entity != null) {
			onEntitySound(entity);
		}
	}

	public void onEntitySound(Entity entity) {
		if (entity == null || entity.isRemoved()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();

		if (!prepare(client)) {
			return;
		}
		this.highlightedEntityIds.put(entity.getId(), expiration(client.level));
	}

	public void onBlockEvent(BlockPos pos, Block block, int eventId) {
		Minecraft client = Minecraft.getInstance();

		if (!prepare(client)) {
			return;
		}
		boolean isSoundEvent = block instanceof NoteBlock && eventId == 0
			|| block instanceof PistonBaseBlock && eventId >= PistonBaseBlock.TRIGGER_EXTEND
				&& eventId <= 2;

		if (isSoundEvent) {
			this.highlightedBlockPositions.put(pos.immutable(), expiration(client.level));
		}
	}

	public void onLevelEvent(int eventId, BlockPos pos) {
		Minecraft client = Minecraft.getInstance();

		if (!prepare(client)) {
			return;
		}
		boolean isDispenserSound = eventId >= 1000 && eventId <= 1002;
		BlockPos immutablePos = pos == null ? null : pos.immutable();

		if (isDispenserSound && immutablePos != null
			&& client.level.getBlockState(immutablePos).getBlock() instanceof DispenserBlock) {
			this.highlightedBlockPositions.put(immutablePos, expiration(client.level));
		}
	}

	public boolean isEntityHighlighted(Entity entity) {
		if (entity == null) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();

		if (!prepare(client)) {
			return false;
		}
		Long expiration = this.highlightedEntityIds.get(entity.getId());

		if (expiration == null) {
			return false;
		}
		if (expiration <= client.level.getGameTime()) {
			this.highlightedEntityIds.remove(entity.getId());
			return false;
		}
		return true;
	}

	public Set<BlockPos> getBlockPositions() {
		Minecraft client = Minecraft.getInstance();

		if (!prepare(client)) {
			return Set.of();
		}

		return collectBlockPositions(client.level);
	}

	// 方块被破坏/替换后取消高亮
	private Set<BlockPos> collectBlockPositions(ClientLevel level) {
		if (this.highlightedBlockPositions.isEmpty()) {
			return Set.of();
		}

		Set<BlockPos> positions = new HashSet<>();

		for (Iterator<Map.Entry<BlockPos, Long>> iterator = this.highlightedBlockPositions.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry<BlockPos, Long> entry = iterator.next();

			if (level.getBlockState(entry.getKey()).isAir()) {
				iterator.remove();
				continue;
			}
			positions.add(entry.getKey());
		}
		return Set.copyOf(positions);
	}

	public void tick() {
		prepare(Minecraft.getInstance());
	}
	private boolean prepare(Minecraft client) {
		if (!ListMoreConfigs.Generic.SOUND_SOURCE_HIGHLIGHT_ENABLED.getBooleanValue()
			|| client.player == null || client.level == null) {
			clear();
			return false;
		}
		if (this.trackedLevel != client.level) {
			clear();
			this.trackedLevel = client.level;
		}
		long gameTime = client.level.getGameTime();
		this.highlightedEntityIds.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
		this.highlightedBlockPositions.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
		return true;
	}

	private long expiration(ClientLevel level) {
		return level.getGameTime() + ListMoreConfigs.Generic.SOUND_SOURCE_HIGHLIGHT_DURATION_TICKS.getIntegerValue();
	}

	private void clear() {
		this.highlightedEntityIds.clear();
		this.highlightedBlockPositions.clear();
		this.trackedLevel = null;
	}
}
