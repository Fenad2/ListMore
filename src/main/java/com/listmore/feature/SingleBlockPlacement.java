package com.listmore.feature;

import com.listmore.config.ListMoreConfigs;

public final class SingleBlockPlacement {
	private static boolean useKeyDown;
	private static boolean useConsumed;
	private static int blockInteractionDepth;

	private SingleBlockPlacement() {
	}

	public static void updateUseKeyState(boolean isUseKeyDown) {
		if (isUseKeyDown && !useKeyDown) {
			useConsumed = false;
		}
		useKeyDown = isUseKeyDown;
	}

	public static boolean isUseConsumed() {
		return ListMoreConfigs.Generic.SINGLE_BLOCK_PLACEMENT.getBooleanValue() && useConsumed;
	}

	public static void markBlockPlaced() {
		if (isBlockInteractionInProgress()
				&& ListMoreConfigs.Generic.SINGLE_BLOCK_PLACEMENT.getBooleanValue()) {
			useConsumed = true;
		}
	}

	public static void beginBlockInteraction() {
		blockInteractionDepth++;
	}

	public static void endBlockInteraction() {
		if (blockInteractionDepth > 0) {
			blockInteractionDepth--;
		}
	}

	private static boolean isBlockInteractionInProgress() {
		return blockInteractionDepth > 0;
	}
}
