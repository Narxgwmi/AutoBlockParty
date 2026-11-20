package com.autoblockparty;

import com.autoblockparty.compat.ModMenuIntegration;
import com.autoblockparty.config.AutoBlockPartyConfig;
import com.autoblockparty.movement.BlockPartyMover;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class AutoBlockPartyClient implements ClientModInitializer {
    public static final String MOD_ID = "autoblockparty";

    private static AutoBlockPartyConfig config;
    private static BlockPartyMover mover;

    private static KeyBinding toggleKey;
    private static KeyBinding settingsKey;

    @Override
    public void onInitializeClient() {
        config = AutoBlockPartyConfig.load();
        mover = new BlockPartyMover(config);

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autoblockparty.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.autoblockparty.main"
        ));

        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autoblockparty.settings",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "category.autoblockparty.main"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                mover.toggle(client);
            }

            while (settingsKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(ModMenuIntegration.createConfigScreenSafe(null));
                }
            }

            mover.tick(client);
        });
    }

    public static AutoBlockPartyConfig getConfig() {
        return config;
    }

    public static BlockPartyMover getMover() {
        return mover;
    }
}