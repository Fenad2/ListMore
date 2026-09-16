package com.listmore.feature;

import com.listmore.config.ListMoreConfigs;

public final class SingleBlockMining {
	private static boolean attackCycleConsumed;

	private SingleBlockMining() {
	}

	public static void resetAttackCycle() {
		attackCycleConsumed = false;
	}

	public static boolean isAttackCycleConsumed() {
		return ListMoreConfigs.Generic.SINGLE_BLOCK_MINING.getBooleanValue() && attackCycleConsumed;
	}

	public static void markBlockDestroyed() {
		if (ListMoreConfigs.Generic.SINGLE_BLOCK_MINING.getBooleanValue()) {
			attackCycleConsumed = true;
		}
	}
}
