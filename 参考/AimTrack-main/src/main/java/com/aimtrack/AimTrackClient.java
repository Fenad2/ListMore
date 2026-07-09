package com.aimtrack;

import com.aimtrack.config.AimTrackConfig;
import com.aimtrack.input.KeyBindings;
import com.aimtrack.render.AimTrackHudRenderer;
import com.aimtrack.render.ArrowTrajectoryRenderer;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;

public class AimTrackClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        AutoConfig.register(AimTrackConfig.class, GsonConfigSerializer::new);

        AimTrackMod.LOGGER.info("[AimTrack] 客户端初始化，注册轨迹渲染器");
        ArrowTrajectoryRenderer.register();

        AimTrackMod.LOGGER.info("[AimTrack] 注册HUD渲染器");
        AimTrackHudRenderer.register();

        AimTrackMod.LOGGER.info("[AimTrack] 注册按键绑定");
        KeyBindings.register();
    }
}
