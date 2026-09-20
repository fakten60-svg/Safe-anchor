package com.safeanchor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/**
 * Client entrypoint: loads the config, registers hotkeys, the tick handler
 * and the HUD.
 */
public class SafeAnchorClient implements ClientModInitializer {

    private static SafeAnchorClient instance;
    private SafeAnchorController controller;

    @Override
    public void onInitializeClient() {
        instance = this;
        SafeAnchorConfigManager.load();
        SafeAnchorKeybinds.register();
        this.controller = new SafeAnchorController();
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        SafeAnchorHud.register();
        SafeAnchorLogger.info("initialized for Minecraft 1.21.11 (activate: V, emergency cancel: X).");
    }

    private void onEndTick(MinecraftClient client) {
        while (SafeAnchorKeybinds.activationKey.wasPressed()) {
            controller.startSequence();
        }
        while (SafeAnchorKeybinds.cancelKey.wasPressed()) {
            if (controller.isRunning()) {
                controller.cancelSequence("emergency hotkey");
            }
        }
        controller.tick();
    }

    public static SafeAnchorClient getInstance() {
        return instance;
    }

    public static SafeAnchorController getController() {
        return instance == null ? null : instance.controller;
    }
}
