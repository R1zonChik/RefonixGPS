package ru.refontstudio.refonixgps;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;

public final class RefonixGPS extends JavaPlugin implements TabCompleter, Listener {

    private Map<UUID, UUID> pendingRequests = new HashMap<>();
    private Map<UUID, UUID> tracking = new HashMap<>();
    private Map<UUID, UUID> activeTracking = new HashMap<>();
    private Map<UUID, Boolean> navigationEnabled = new HashMap<>();
    private Map<UUID, Boolean> proximityNotified = new HashMap<>();
    private Map<UUID, Boolean> joinNotifications = new HashMap<>();
    private Map<UUID, List<String>> locationHistory = new HashMap<>();
    private File dataFile;
    private FileConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("RefonixGPS запущен!");
        this.getCommand("gps").setTabCompleter(this);
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

    private boolean canTrackPlayer(UUID tracker, UUID target) {
        return tracking.containsKey(tracker) && tracking.get(tracker).equals(target) ||
                tracking.containsKey(target) && tracking.get(target).equals(tracker);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!getConfig().getBoolean("settings.join-notifications", true)) return;

        for (Map.Entry<UUID, UUID> entry : tracking.entrySet()) {
            Player tracker = Bukkit.getPlayer(entry.getKey());
            if (tracker != null && entry.getValue().equals(player.getUniqueId())) {
                if (joinNotifications.getOrDefault(tracker.getUniqueId(), true)) {
                    tracker.sendMessage(formatMessage(getMessage("success.friend-online"), player.getName()));
                    if (getConfig().getBoolean("settings.join-notifications", true)) {
                        try {
                            tracker.playSound(tracker.getLocation(), Sound.valueOf(getConfig().getString("sounds.friend-join")), 1.0f, 1.0f);
                        } catch (Exception e) {
                            // игнорируем ошибки звука
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!getConfig().getBoolean("settings.join-notifications", true)) return;

        for (Map.Entry<UUID, UUID> entry : tracking.entrySet()) {
            Player tracker = Bukkit.getPlayer(entry.getKey());
            if (tracker != null && entry.getValue().equals(player.getUniqueId())) {
                if (joinNotifications.getOrDefault(tracker.getUniqueId(), true)) {
                    tracker.sendMessage(formatMessage(getMessage("success.friend-offline"), player.getName()));
                    if (getConfig().getBoolean("settings.join-notifications", true)) {
                        try {
                            tracker.playSound(tracker.getLocation(), Sound.valueOf(getConfig().getString("sounds.friend-leave")), 1.0f, 1.0f);
                        } catch (Exception e) {
                            // игнорируем ошибки звука
                        }
                    }
                }
            }
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

    private void addToHistory(UUID playerUUID, String location) {
        if (!getConfig().getBoolean("settings.history-enabled", true)) return;

        List<String> history = locationHistory.getOrDefault(playerUUID, new ArrayList<>());
        history.add(new Date().toString() + " - " + location);

        int maxEntries = getConfig().getInt("settings.history-max-entries", 50);
        if (history.size() > maxEntries) {
            history.remove(0);
        }

        locationHistory.put(playerUUID, history);
    }

    private void checkProximity(Player tracker, Player target, double distance) {
        int proximityDistance = getConfig().getInt("settings.proximity-distance", 10);
        boolean soundEnabled = getConfig().getBoolean("settings.proximity-sound", true);

        if (distance <= proximityDistance) {
            if (!proximityNotified.getOrDefault(tracker.getUniqueId(), false)) {
                tracker.sendMessage(formatMessage(getMessage("success.friend-nearby"), target.getName()));
                if (soundEnabled) {
                    try {
                        tracker.playSound(tracker.getLocation(), Sound.valueOf(getConfig().getString("sounds.proximity")), 1.0f, 1.0f);
                    } catch (Exception e) {
                        // игнорируем ошибки звука
                    }
                }
                proximityNotified.put(tracker.getUniqueId(), true);
            }
        } else {
            proximityNotified.put(tracker.getUniqueId(), false);
        }
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

                boolean alreadyMutual = tracking.containsKey(player.getUniqueId()) && tracking.get(player.getUniqueId()).equals(requester);
                if (!alreadyMutual) {
                    tracking.put(player.getUniqueId(), requester);
                }

                pendingRequests.remove(player.getUniqueId());
                saveData();

                String requesterName = requesterPlayer != null ? requesterPlayer.getName() : "игрока";
                player.sendMessage(formatMessage(getMessage("success.tracking-allowed"), requesterName));
                if (requesterPlayer != null) {
                    requesterPlayer.sendMessage(formatMessage(getMessage("success.tracking-accepted"), player.getName()));
                    if (!alreadyMutual) {
                        requesterPlayer.sendMessage(getMessage("success.mutual-tracking"));
                        player.sendMessage(getMessage("success.mutual-tracking"));
                    }
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
                if (removeTarget == null) {
                    player.sendMessage(getMessage("errors.player-not-found"));
                    return true;
                }

                if (tracking.get(player.getUniqueId()) != null && tracking.get(player.getUniqueId()).equals(removeTarget.getUniqueId())) {
                    tracking.remove(player.getUniqueId());
                    activeTracking.remove(player.getUniqueId());
                    saveData();
                    player.sendMessage(formatMessage(getMessage("success.tracking-removed"), removeTarget.getName()));
                } else {
                    player.sendMessage(getMessage("errors.not-tracking-player"));
                }
                break;

            case "delete":
                if (args.length < 2) {
                    player.sendMessage(getMessage("errors.specify-player"));
                    return true;
                }

                Player deleteTarget = Bukkit.getPlayer(args[1]);
                if (deleteTarget == null) {
                    player.sendMessage(getMessage("errors.player-not-found"));
                    return true;
                }

                boolean found = false;
                for (Map.Entry<UUID, UUID> entry : tracking.entrySet()) {
                    if (entry.getKey().equals(deleteTarget.getUniqueId()) && entry.getValue().equals(player.getUniqueId())) {
                        tracking.remove(entry.getKey());
                        activeTracking.remove(entry.getKey());
                        tracking.remove(player.getUniqueId());
                        activeTracking.remove(player.getUniqueId());
                        saveData();
                        player.sendMessage(formatMessage(getMessage("success.self-removed"), deleteTarget.getName()));
                        deleteTarget.sendMessage(formatMessage(getMessage("success.removed-by-target"), player.getName()));
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    player.sendMessage(getMessage("errors.not-tracked-by-player"));
                }
                break;

            case "list":
                boolean hasFriends = false;
                player.sendMessage(getMessage("success.tracking-list"));

                Set<UUID> processedPlayers = new HashSet<>();

                for (Map.Entry<UUID, UUID> entry : tracking.entrySet()) {
                    if (entry.getKey().equals(player.getUniqueId()) && !processedPlayers.contains(entry.getValue())) {
                        Player trackedPlayer = Bukkit.getPlayer(entry.getValue());
                        if (trackedPlayer != null) {
                            String coords = formatCoords(trackedPlayer.getLocation());
                            String message = getMessage("success.tracking-item")
                                    .replace("{player}", trackedPlayer.getName())
                                    .replace("{coords}", coords);
                            player.sendMessage(message);
                            hasFriends = true;
                            processedPlayers.add(entry.getValue());
                        }
                    }

                    if (entry.getValue().equals(player.getUniqueId()) && !processedPlayers.contains(entry.getKey())) {
                        Player follower = Bukkit.getPlayer(entry.getKey());
                        if (follower != null) {
                            String coords = formatCoords(follower.getLocation());
                            String message = getMessage("success.follower-item")
                                    .replace("{player}", follower.getName())
                                    .replace("{coords}", coords);
                            player.sendMessage(message);
                            hasFriends = true;
                            processedPlayers.add(entry.getKey());
                        }
                    }
                }

                if (!hasFriends) {
                    player.sendMessage(getMessage("errors.no-friends"));
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

                if (!canTrackPlayer(player.getUniqueId(), trackTarget.getUniqueId())) {
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

            case "notifications":
                boolean currentNotif = joinNotifications.getOrDefault(player.getUniqueId(), true);
                joinNotifications.put(player.getUniqueId(), !currentNotif);

                if (!currentNotif) {
                    player.sendMessage(getMessage("success.notifications-enabled"));
                } else {
                    player.sendMessage(getMessage("success.notifications-disabled"));
                }
                break;

            case "history":
                if (args.length < 2) {
                    player.sendMessage(getMessage("errors.specify-player"));
                    return true;
                }

                Player historyTarget = Bukkit.getPlayer(args[1]);
                if (historyTarget == null) {
                    player.sendMessage(getMessage("errors.player-not-found"));
                    return true;
                }

                List<String> history = locationHistory.get(historyTarget.getUniqueId());
                if (history == null || history.isEmpty()) {
                    player.sendMessage(getMessage("errors.no-history"));
                    return true;
                }

                player.sendMessage(formatMessage(getMessage("success.history-title"), historyTarget.getName()));
                for (String entry : history) {
                    player.sendMessage("§7" + entry);
                }
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("gps") && args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();

            List<String> commands = Arrays.asList("add", "remove", "delete", "list", "track", "toggle", "accept", "deny", "notifications", "history");
            for (String cmd : commands) {
                if (cmd.startsWith(input)) {
                    completions.add(cmd);
                }
            }
            return completions;
        }
        return null;
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
                            checkProximity(tracker, target, distance);
                            addToHistory(target.getUniqueId(), formatCoords(targetLoc));

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