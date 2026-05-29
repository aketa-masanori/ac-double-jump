package com.yamabuki.acdoublejump;

import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AcDoubleJumpPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final long COMBAT_WINDOW_MILLIS = 10_000L;

    private final Map<String, WorldJumpSettings> worldSettings = new HashMap<>();
    private final Map<UUID, Long> lastJumpMillis = new HashMap<>();
    private final Map<UUID, Long> lastCombatMillis = new HashMap<>();
    private final Set<UUID> noFallDamagePlayers = new HashSet<>();

    private WorldJumpSettings defaultWorldSetting = WorldJumpSettings.SAFE_DEFAULT;

    private String normalizeWorldKey(String worldName) {
        return worldName.toLowerCase(Locale.ROOT);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();

        if (getCommand("acdoublejump") != null) {
            getCommand("acdoublejump").setExecutor(this);
            getCommand("acdoublejump").setTabCompleter(this);
        }

        getServer().getPluginManager().registerEvents(this, this);

        for (Player player : getServer().getOnlinePlayers()) {
            updatePlayerFlightState(player);
        }
    }

    @Override
    public void onDisable() {
        worldSettings.clear();
        lastJumpMillis.clear();
        lastCombatMillis.clear();
        noFallDamagePlayers.clear();
    }

    private void reloadSettings() {
        worldSettings.clear();

        ConfigurationSection defaultSection = getConfig().getConfigurationSection("default-world-setting");
        defaultWorldSetting = WorldJumpSettings.fromSection(
                defaultSection,
                WorldJumpSettings.SAFE_DEFAULT,
                getLogger(),
                "default-world-setting"
        );

        ConfigurationSection worldsSection = getConfig().getConfigurationSection("worlds");
        if (worldsSection == null) {
            return;
        }

        for (String worldName : worldsSection.getKeys(false)) {
            ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldName);
            WorldJumpSettings settings = WorldJumpSettings.fromSection(
                    worldSection,
                    defaultWorldSetting,
                    getLogger(),
                    "worlds." + worldName
            );
            worldSettings.put(normalizeWorldKey(worldName), settings);
        }
    }

    private WorldJumpSettings getSettings(Player player) {
        return worldSettings.getOrDefault(normalizeWorldKey(player.getWorld().getName()), defaultWorldSetting);
    }

    private boolean isGameplayMode(Player player) {
        GameMode gameMode = player.getGameMode();
        return gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE;
    }

    private boolean isOnGround(Player player) {
        return ((Entity) player).isOnGround();
    }

    private boolean canUseByPermission(Player player) {
        if (!player.hasPermission("acdoublejump.use")) {
            return false;
        }

        String worldPermissionNode = "acdoublejump.use." + normalizeWorldKey(player.getWorld().getName());
        Boolean worldOverride = null;
        for (var info : player.getEffectivePermissions()) {
            if (info.getPermission().toLowerCase(Locale.ROOT).equals(worldPermissionNode)) {
                worldOverride = info.getValue();
            }
        }
        if (worldOverride != null) {
            return worldOverride;
        }
        return true;
    }

    private boolean isInCombat(Player player) {
        Long lastCombat = lastCombatMillis.get(player.getUniqueId());
        if (lastCombat == null) {
            return false;
        }
        return System.currentTimeMillis() - lastCombat <= COMBAT_WINDOW_MILLIS;
    }

    private boolean canUseDoubleJump(Player player) {
        if (!isGameplayMode(player)) {
            return false;
        }

        WorldJumpSettings settings = getSettings(player);
        if (!settings.enabled()) {
            return false;
        }
        if (!canUseByPermission(player)) {
            return false;
        }
        if (!settings.allowInCombat() && isInCombat(player)) {
            return false;
        }
        return true;
    }

    private boolean isOnCooldown(Player player, WorldJumpSettings settings) {
        if (settings.cooldownMillis() <= 0L) {
            return false;
        }
        Long lastJump = lastJumpMillis.get(player.getUniqueId());
        if (lastJump == null) {
            return false;
        }
        return System.currentTimeMillis() - lastJump < settings.cooldownMillis();
    }

    private void updatePlayerFlightState(Player player) {
        GameMode gameMode = player.getGameMode();
        if (gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR) {
            return;
        }

        if (!canUseDoubleJump(player)) {
            player.setAllowFlight(false);
            player.setFlying(false);
            return;
        }

        if (isOnGround(player)) {
            player.setAllowFlight(true);
        }
    }

    private void markCombat(Player player) {
        lastCombatMillis.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        getServer().getScheduler().runTask(this, () -> updatePlayerFlightState(player));
    }

    @EventHandler
    public void onPlayerWorldChange(PlayerChangedWorldEvent event) {
        updatePlayerFlightState(event.getPlayer());
    }

    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        getServer().getScheduler().runTask(this, () -> updatePlayerFlightState(player));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isOnGround(player)) {
            return;
        }
        if (!isGameplayMode(player)) {
            return;
        }
        if (canUseDoubleJump(player) && !player.getAllowFlight()) {
            player.setAllowFlight(true);
        }
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!isGameplayMode(player)) {
            return;
        }

        event.setCancelled(true);
        player.setFlying(false);

        if (!canUseDoubleJump(player)) {
            player.setAllowFlight(false);
            return;
        }

        WorldJumpSettings settings = getSettings(player);
        if (isOnCooldown(player, settings)) {
            player.setAllowFlight(false);
            return;
        }

        player.setAllowFlight(false);

        Vector direction = player.getLocation().getDirection();
        Vector horizontal = new Vector(direction.getX(), 0.0D, direction.getZ());
        if (horizontal.lengthSquared() > 0.0D) {
            horizontal.normalize().multiply(settings.horizontal());
        }

        Vector velocity = horizontal.setY(settings.vertical());
        player.setVelocity(velocity);
        player.setFallDistance(0.0F);

        if (settings.cooldownMillis() > 0L) {
            lastJumpMillis.put(player.getUniqueId(), System.currentTimeMillis());
        } else {
            lastJumpMillis.remove(player.getUniqueId());
        }

        if (settings.preventFallDamage()) {
            noFallDamagePlayers.add(player.getUniqueId());
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (noFallDamagePlayers.remove(uuid)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim) {
            markCombat(victim);
        }

        Player attacker = resolveAttackingPlayer(event.getDamager());
        if (attacker != null) {
            markCombat(attacker);
        }
    }

    private Player resolveAttackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanupPlayerState(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        cleanupPlayerState(event.getPlayer().getUniqueId());
    }

    private void cleanupPlayerState(UUID uuid) {
        lastJumpMillis.remove(uuid);
        lastCombatMillis.remove(uuid);
        noFallDamagePlayers.remove(uuid);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("Usage: /" + label + " reload");
            return true;
        }

        if (!sender.hasPermission("acdoublejump.reload")) {
            sender.sendMessage("You do not have permission.");
            return true;
        }

        reloadConfig();
        reloadSettings();
        for (Player player : getServer().getOnlinePlayers()) {
            updatePlayerFlightState(player);
        }
        sender.sendMessage("AcDoubleJump config reloaded.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("reload");
        }
        return new ArrayList<>();
    }
}
