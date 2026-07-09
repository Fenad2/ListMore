package com.aimtrack;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AimTrackMod implements ModInitializer {
	public static final String MOD_ID = "aimtrack-mod";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[AimTrack] Mod启动啦");
	}
}
