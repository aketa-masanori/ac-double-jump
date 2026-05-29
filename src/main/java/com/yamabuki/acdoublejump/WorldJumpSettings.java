package com.yamabuki.acdoublejump;

import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

public record WorldJumpSettings(
        boolean enabled,
        long cooldownMillis,
        double horizontal,
        double vertical,
        boolean allowInCombat,
        boolean preventFallDamage
) {
    public static final WorldJumpSettings SAFE_DEFAULT = new WorldJumpSettings(
            false,
            10_000L,
            0.3D,
            0.7D,
            false,
            false
    );

    public static WorldJumpSettings fromSection(
            ConfigurationSection section,
            WorldJumpSettings fallback,
            Logger logger,
            String pathForLog
    ) {
        if (section == null) {
            return fallback;
        }

        boolean enabled = section.getBoolean("enabled", fallback.enabled());
        long cooldownMillis = TimeParser.parseMillis(
                section.getString("cooldown"),
                fallback.cooldownMillis(),
                logger,
                pathForLog + ".cooldown"
        );
        double horizontal = section.getDouble("horizontal", fallback.horizontal());
        double vertical = section.getDouble("vertical", fallback.vertical());
        boolean allowInCombat = section.getBoolean("allow-in-combat", fallback.allowInCombat());
        boolean preventFallDamage = section.getBoolean("prevent-fall-damage", fallback.preventFallDamage());

        return new WorldJumpSettings(
                enabled,
                Math.max(cooldownMillis, 0L),
                horizontal,
                vertical,
                allowInCombat,
                preventFallDamage
        );
    }
}
