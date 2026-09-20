package com.safeanchor;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.KeyBinding.Category;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Registers the activation and emergency-cancel hotkeys. They show up in the
 * Minecraft controls screen (in their own "SafeAnchor" category) and stay
 * rebindable like vanilla keys.
 */
public final class SafeAnchorKeybinds {

    private static final Category CATEGORY = Category.create(Identifier.of("safeanchor", "main"));

    public static KeyBinding activationKey;
    public static KeyBinding cancelKey;

    private SafeAnchorKeybinds() {
    }

    public static void register() {
        activationKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.safeanchor.activate",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                CATEGORY));
        cancelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.safeanchor.cancel",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                CATEGORY));
    }
}
