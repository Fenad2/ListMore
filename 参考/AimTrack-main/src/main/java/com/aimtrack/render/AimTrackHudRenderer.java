package com.aimtrack.render;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * AimTrack HUD 渲染器
 * 在屏幕右上角显示自瞄开关状态，替代聊天栏提示
 */
public class AimTrackHudRenderer {

    // 显示状态文字
    private static String displayText = "";
    // 文字颜色
    private static int displayColor = 0xFFFFFF;
    // 显示剩余tick数（1秒 = 20 tick）
    private static int displayTicks = 0;
    // 显示持续时间
    private static final int DISPLAY_DURATION = 30; // 1.5秒

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (displayTicks <= 0) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            // 每帧减少显示时间
            displayTicks--;

            // 计算透明度（淡入淡出效果）
            int alpha = 255;
            if (displayTicks < 8) {
                // 最后8 tick淡出
                alpha = (int) (255 * (displayTicks / 8.0f));
            }

            // 组合颜色（ARGB格式）
            int color = (alpha << 24) | (displayColor & 0xFFFFFF);

            // 在屏幕右上角显示，留一点边距
            int screenWidth = client.getWindow().getScaledWidth();
            int x = screenWidth - 5;
            int y = 5;

            // 右对齐文字，小字体
            drawContext.drawTextWithShadow(
                    client.textRenderer,
                    Text.literal(displayText),
                    x - client.textRenderer.getWidth(displayText),
                    y,
                    color
            );
        });
    }

    /**
     * 显示自瞄状态提示
     * @param enabled 是否开启
     */
    public static void showAimStatus(boolean enabled) {
        if (enabled) {
            displayText = "§a自瞄 ON";
            displayColor = 0x55FF55; // 绿色
        } else {
            displayText = "§c自瞄 OFF";
            displayColor = 0xFF5555; // 红色
        }
        displayTicks = DISPLAY_DURATION;
    }
}
