package com.safeanchor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Small logging facade so every message carries the mod prefix.
 */
public final class SafeAnchorLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger("SafeAnchor");

    private SafeAnchorLogger() {
    }

    public static void info(String message) {
        LOGGER.info("[SafeAnchor] {}", message);
    }

    public static void warn(String message) {
        LOGGER.warn("[SafeAnchor] {}", message);
    }

    public static void error(String message) {
        LOGGER.error("[SafeAnchor] {}", message);
    }

    /**
     * Logs only when {@code debugLogging} is enabled in the config.
     */
    public static void debug(String message) {
        boolean enabled = false;
        try {
            enabled = SafeAnchorConfigManager.get().isDebugLogging();
        } catch (Exception ignored) {
            // Config not available (very early startup): stay silent.
        }
        if (enabled) {
            LOGGER.info("[SafeAnchor] {}", message);
        }
    }
}
