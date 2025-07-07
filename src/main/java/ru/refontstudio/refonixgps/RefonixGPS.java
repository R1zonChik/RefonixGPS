package ru.refontstudio.refonixgps;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RefonixGPS extends JavaPlugin {

    private Map<UUID, UUID> pendingRequests = new HashMap<>();
    private Map<UUID, UUID> tracking = new HashMap<>();
    private Map<UUID, UUID> activeTracking = new HashMap<>(); // кого сейчас отслеживаем в actionbar
    private Map<UUID, Boolean> navigationEnabled = new HashMap<>(); // включена ли навигация
    private File dataFile;
    private FileConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("RefonixGPS запущен!");
        loadData();
        startActionBarTask();
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private void loadData() {
        dataFile = new File(getDataFolder(), "tracking.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        for (String key : data.getKeys(false)) {
            try {
                UUID tracker = UUID.fromString(key);
                UUID target = UUID.fromString(data.getString(key));
                tracking.put(tracker, target);
            } catch (Exception e) {
                // игнорируем поврежденные записи
            }
        }
    }

    private void saveData() {
        try {
            data = new YamlConfiguration();
            for (Map.Entry<UUID, UUID> entry : tracking.entrySet()) {
                data.set(entry.getKey().toString(), entry.getValue().toString());
            }
            data.save(dataFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getMessage(String path) {
        return getConfig().getString("messages." + path, "§cСообщение не найдено: " + path);
    }

    private List<String> getMessageList(String path) {
        return getConfig().getStringList("messages." + path);
    }

    private String formatMessage(String message, String playerName) {
        return message.replace("{player}", playerName);
    }

    private String formatCoords(Location loc) {
        return String.format("X:%d Y:%d Z:%d", (int)loc.getX(), (int)loc.getY(), (int)loc.getZ());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("only-players"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            for (String line : getMessageList("usage")) {
                player.sendMessage(line);
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add":
                if (args.length < 2) {
                    player.sendMessage(getMessage("errors.specify-player"));
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(getMessage("errors.player-not-found"));
                    return true;
                }

                if (target.equals(player)) {
                    player.sendMessage(getMessage("errors.cannot-track-self"));
                    return true;
                }

                pendingRequests.put(target.getUniqueId(), player.getUniqueId());

                player.sendMessage(formatMessage(getMessage("success.request-sent"), target.getName()));

                target.sendMessage(getMessage("request.title") + " " + formatMessage(getMessage("request.message"), player.getName()));
                target.sendMessage(getMessage("request.accept"));
                target.sendMessage(getMessage("request.deny"));
                break;

            case "accept":
                UUID requester = pendingRequests.get(player.getUniqueId());
                if (requester == null) {
                    player.sendMessage(getMessage("errors.no-requests"));
                    return true;
                }

                Player requesterPlayer = Bukkit.getPlayer(requester);
                tracking.put(requester, player.getUniqueId());
                pendingRequests.remove(player.getUniqueId());
                saveData();

                String requesterName = requesterPlayer != null ? requesterPlayer.getName() : "игрока";
                player.sendMessage(formatMessage(getMessage("success.tracking-allowed"), requesterName));
                if (requesterPlayer != null) {
                    requesterPlayer.sendMessage(formatMessage(getMessage("success.tracking-accepted"), player.getName()));
                    // автоматически начинаем отслеживать первого добавленного
                    if (activeTracking.get(requester) == null) {
                        activeTracking.put(requester, player.getUniqueId());
                        navigationEnabled.put(requester, true);
                    }
                }
                break;

            case "deny":
                UUID denier = pendingRequests.get(player.getUniqueId());
                if (denier == null) {
                    player.sendMessage(getMessage("errors.no-requests"));
                    return true;
                }

                Player denierPlayer = Bukkit.getPlayer(denier);
                pendingRequests.remove(player.getUniqueId());

                player.sendMessage(getMessage("success.tracking-denied"));
                if (denierPlayer != null) {
                    denierPlayer.sendMessage(formatMessage(getMessage("success.request-denied"), player.getName()));
                }
                break;

            case "remove":
                if (args.length < 2) {
                    player.sendMessage(getMessage("errors.specify-player"));
                    return true;
                }

                Player removeTarget = Bukkit.getPlayer(args[1]);
                if (removeTarget != null) {
                    tracking.remove(player.getUniqueId());
                    activeTracking.remove(player.getUniqueId());
                    saveData();
                    player.sendMessage(formatMessage(getMessage("success.tracking-removed"), removeTarget.getName()));
                }
                break;

            case "list":
                if (tracking.isEmpty()) {
                    player.sendMessage(getMessage("errors.no-tracking"));
                    return true;
                }

                player.sendMessage(getMessage("success.tracking-list"));
                for (UUID tracked : tracking.values()) {
                    Player trackedPlayer = Bukkit.getPlayer(tracked);
                    if (trackedPlayer != null) {
                        String coords = formatCoords(trackedPlayer.getLocation());
                        String message = getMessage("success.tracking-item")
                                .replace("{player}", trackedPlayer.getName())
                                .replace("{coords}", coords);
                        player.sendMessage(message);
                    }
                }
                break;

            case "track":
                if (args.length < 2) {
                    player.sendMessage(getMessage("errors.specify-player"));
                    return true;
                }

                Player trackTarget = Bukkit.getPlayer(args[1]);
                if (trackTarget == null) {
                    player.sendMessage(getMessage("errors.player-not-found"));
                    return true;
                }

                if (!tracking.containsValue(trackTarget.getUniqueId())) {
                    player.sendMessage(getMessage("errors.not-tracking-player"));
                    return true;
                }

                activeTracking.put(player.getUniqueId(), trackTarget.getUniqueId());
                navigationEnabled.put(player.getUniqueId(), true);
                player.sendMessage(formatMessage(getMessage("success.now-tracking"), trackTarget.getName()));
                break;

            case "toggle":
                boolean currentState = navigationEnabled.getOrDefault(player.getUniqueId(), false);
                navigationEnabled.put(player.getUniqueId(), !currentState);

                if (!currentState) {
                    player.sendMessage(getMessage("success.navigation-enabled"));
                } else {
                    player.sendMessage(getMessage("success.navigation-disabled"));
                }
                break;
        }
        return true;
    }

    private void startActionBarTask() {
        int interval = getConfig().getInt("settings.update-interval", 10);
        int maxDistance = getConfig().getInt("settings.max-distance", 1000);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, UUID> entry : activeTracking.entrySet()) {
                    Player tracker = Bukkit.getPlayer(entry.getKey());
                    Player target = Bukkit.getPlayer(entry.getValue());

                    if (tracker != null && target != null) {
                        boolean navEnabled = navigationEnabled.getOrDefault(tracker.getUniqueId(), false);
                        if (!navEnabled) continue;

                        Location trackerLoc = tracker.getLocation();
                        Location targetLoc = target.getLocation();

                        double distance = trackerLoc.distance(targetLoc);

                        if (distance <= maxDistance) {
                            String arrow = getDirectionArrow(trackerLoc, targetLoc);
                            String actionBarFormat = getMessage("actionbar.format");
                            String actionBar = actionBarFormat
                                    .replace("{player}", target.getName())
                                    .replace("{arrow}", arrow)
                                    .replace("{distance}", String.valueOf(Math.round(distance)));

                            tracker.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBar));
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0, interval);
    }

    private String getDirectionArrow(Location from, Location to) {
        double deltaX = to.getX() - from.getX();
        double deltaZ = to.getZ() - from.getZ();

        double targetYaw = Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        double playerYaw = from.getYaw();

        double angle = targetYaw - playerYaw;

        while (angle < -180) angle += 360;
        while (angle > 180) angle -= 360;

        if (angle >= -22.5 && angle < 22.5) return "↑";
        else if (angle >= 22.5 && angle < 67.5) return "↗";
        else if (angle >= 67.5 && angle < 112.5) return "→";
        else if (angle >= 112.5 && angle < 157.5) return "↘";
        else if (angle >= 157.5 || angle < -157.5) return "↓";
        else if (angle >= -157.5 && angle < -112.5) return "↙";
        else if (angle >= -112.5 && angle < -67.5) return "←";
        else return "↖";
    }
}