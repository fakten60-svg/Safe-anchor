package com.safeanchor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Small overlay showing the sequence status while running
 * ("SafeAnchor: Ready" while idle).
 */
public final class SafeAnchorHud {

    private static final Identifier ELEMENT_ID = Identifier.of("safeanchor", "status");
    private static final int X = 5;
    private static final int Y = 5;
    private static final int LINE_HEIGHT = 10;

    private SafeAnchorHud() {
    }

    public static void register() {
        HudElementRegistry.addLast(ELEMENT_ID, (context, tickCounter) -> render(context));
    }

    private static void render(DrawContext context) {
        if (!SafeAnchorConfigManager.get().isShowHud()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) {
            return;
        }
        SafeAnchorController controller = SafeAnchorClient.getController();
        if (controller == null) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        if (!controller.isRunning() && controller.getCurrentState() == SafeAnchorState.IDLE) {
            context.drawText(textRenderer, Text.literal("SafeAnchor: " + controller.getIdleLine()),
                    X, Y, 0x7CFC00, true);
            return;
        }

        int y = Y;
        context.drawText(textRenderer, Text.literal("SafeAnchor"), X, y, 0xFFD166, true);
        y += LINE_HEIGHT;
        context.drawText(textRenderer, Text.literal("Status: " + controller.getCurrentState()),
                X, y, 0xFFFFFF, true);
        y += LINE_HEIGHT;
        context.drawText(textRenderer,
                Text.literal("Delay: " + controller.getDelayManager().getCurrentDelayMs() + " ms"),
                X, y, 0xFFFFFF, true);
        y += LINE_HEIGHT;
        context.drawText(textRenderer,
                Text.literal("Left: " + controller.getDelayManager().getRemainingMs() + " ms"),
                X, y, 0xFFFFFF, true);
        y += LINE_HEIGHT;
        context.drawText(textRenderer, Text.literal("Next: " + controller.getNextState()),
                X, y, 0xFFFFFF, true);
    }
}
