package fr.eminiumgames.dungeonsinstances.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import fr.eminiumgames.dungeonsinstances.DungeonInstances;

public class DungeonScoreboardManager {

    private final Map<UUID, Scoreboard> activeScoreboards = new HashMap<>();
    private int taskId = -1;

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    public void start() {
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(DungeonInstances.getInstance(), this::updateAll, 0L, 10L); // every 0.5s
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        for (UUID playerId : activeScoreboards.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        activeScoreboards.clear();
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getName().startsWith("instance_")) {
                updateScoreboard(player);
            } else {
                removeScoreboard(player);
            }
        }
    }

    private void updateScoreboard(Player player) {
        PartyManager partyManager = DungeonInstances.getInstance().getPartyManager();
        PartyManager.Party party = partyManager.getPartyByPlayer(player);

        if (party == null) {
            removeScoreboard(player);
            return;
        }

        Scoreboard board = activeScoreboards.get(player.getUniqueId());
        if (board == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            activeScoreboards.put(player.getUniqueId(), board);
        }

        // Clear old objective
        Objective oldObj = board.getObjective("dungeon");
        if (oldObj != null) {
            oldObj.unregister();
        }

        // Determine dungeon name from world name (instance_<dungeon>_<uuid>)
        String worldName = player.getWorld().getName();
        String dungeonName = "Donjon";
        if (worldName.startsWith("instance_")) {
            int start = "instance_".length();
            int lastUnderscore = worldName.lastIndexOf('_');
            if (lastUnderscore > start) {
                dungeonName = worldName.substring(start, lastUnderscore);
            } else {
                dungeonName = worldName.substring(start);
            }
        }

        @SuppressWarnings("deprecation")
        Objective objective = board.registerNewObjective("dungeon", "dummy",
                ChatColor.DARK_PURPLE + "✦ " + ChatColor.BOLD + "Donjon" + ChatColor.RESET + ChatColor.DARK_PURPLE + " ✦");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);


        // objective.getScore(ChatColor.DARK_GRAY + "───────────").setScore(score--);

        Set<UUID> members = party.getMembers();
        int score = members.size() + 2;

        objective.getScore(ChatColor.GRAY + "Donjon: " + ChatColor.WHITE + dungeonName).setScore(score--);
        // Header separator (uniform color)
        objective.getScore(ChatColor.DARK_GRAY + "────────────").setScore(score--);

        for (UUID memberId : members) {
            Player member = Bukkit.getPlayer(memberId);

            if (member == null || !member.isOnline()) {
                // Offline member
                String offlineName = Bukkit.getOfflinePlayer(memberId).getName();
                if (offlineName == null) offlineName = "???";
                String line = ChatColor.DARK_GRAY + "✘ " + ChatColor.GRAY + ChatColor.STRIKETHROUGH + offlineName;
                objective.getScore(line).setScore(score--);
                continue;
            }

            // Health bar
            int health = (int) Math.ceil(member.getHealth());
            @SuppressWarnings("deprecation")
            int maxHealth = (int) Math.ceil(member.getMaxHealth());
            ChatColor healthColor;
            if (health > maxHealth * 0.6) {
                healthColor = ChatColor.GREEN;
            } else if (health > maxHealth * 0.3) {
                healthColor = ChatColor.YELLOW;
            } else {
                healthColor = ChatColor.RED;
            }

            // Direction arrow
            String arrow;
            if (memberId.equals(player.getUniqueId())) {
                arrow = ChatColor.AQUA + "★";
            } else if (member.getWorld().equals(player.getWorld())) {
                System.err.println("Calculating direction from " + player.getLocation() + " to " + member.getLocation());
                arrow = ChatColor.WHITE + getDirectionArrow(player.getLocation(), member.getLocation());
            } else {
                arrow = ChatColor.GRAY + "?";
            }

            // Leader tag
            String leaderTag = memberId.equals(party.getLeader()) ? ChatColor.GOLD + "♛ " : "  ";

            String memberName = member.getName();
            // Truncate name if too long
            if (memberName.length() > 10) {
                memberName = memberName.substring(0, 10);
            }

            String line = arrow + " " + leaderTag + ChatColor.AQUA + memberName + " " + healthColor + health + "❤";
            objective.getScore(line).setScore(score--);
        }

        // Footer separator (same color as header)
        objective.getScore(ChatColor.DARK_GRAY + "───────────").setScore(score--);

        player.setScoreboard(board);
    }

    public void removeScoreboard(Player player) {
        if (activeScoreboards.containsKey(player.getUniqueId())) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            activeScoreboards.remove(player.getUniqueId());
        }
    }

    private String getDirectionArrow(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        // If very close, show a dot
        if (distance < 3) {
            return "●";
        }

        // Calculate angle relative to player's yaw
        double targetAngle = Math.toDegrees(Math.atan2(-dx, dz));
        double playerYaw = from.getYaw();
        double relativeAngle = targetAngle - playerYaw;

        // Normalize to 0-360
        relativeAngle = ((relativeAngle % 360) + 360) % 360;

        // Map to one of 8 directions
        int index = (int) Math.round(relativeAngle / 45.0) % 8;
        return ARROWS[index];
    }
}
