package com.aimtrack.input;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 按键绑定管理
 * 提供快捷键开关自瞄模式
 */
public class KeyBindings {

    // 自瞄开关快捷键，默认绑定到 R 键
    public static KeyBinding toggleAimKey;

    // 自瞄开关状态
    private static boolean aimEnabled = true;

    public static void register() {
        // 注册按键绑定
        toggleAimKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimtrack.toggle", // 翻译键
                InputUtil.Type.KEYSYM, // 键盘按键
                GLFW.GLFW_KEY_R,       // 默认 R 键
                "category.aimtrack"    // 按键分类
        ));

        // 监听按键事件
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleAimKey.wasPressed()) {
                aimEnabled = !aimEnabled;
                // 切换时清除锁定目标，避免重新开启后跳回旧目标
                com.aimtrack.render.AutoAimHelper.clearTarget();
                com.aimtrack.lbng.LbngSystem.clearAll();
                // 在屏幕HUD显示状态
                com.aimtrack.render.AimTrackHudRenderer.showAimStatus(aimEnabled);
            }
        });
    }

    /**
     * 获取自瞄是否开启
     */
    public static boolean isAimEnabled() {
        return aimEnabled;
    }


}
