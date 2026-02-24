package fr.eminiumgames.dungeonsinstances.managers;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class PartyManager {

    private final Map<String, Party> parties = new HashMap<>();
    private final Map<UUID, String> pendingInvites = new HashMap<>(); // invitedPlayer -> partyName
    // track player's previous world before teleporting them into an instance
    private final Map<UUID, String> previousWorlds = new HashMap<>();
    private final File partyDataFile = new File("plugins/DungeonInstances/partyData.json");
    private final Gson gson = new Gson();

    public PartyManager() {
        loadParties();
    }

    public static final String PREFIX = ChatColor.GOLD + "[Party] " + ChatColor.RESET;

    public boolean createParty(String partyName, Player leader) {
        if (parties.containsKey(partyName)) {
            return false;
        }
        Party party = new Party(partyName, leader);
        parties.put(partyName, party);
        saveParties();
        return true;
    }

    @SuppressWarnings("deprecation")
    public boolean invitePlayer(Player inviter, Player target) {
        Party party = getPartyByPlayer(inviter);
        if (party == null) {
            return false;
        }
        if (!party.getLeader().equals(inviter.getUniqueId())) {
            return false;
        }
        if (party.hasMember(target)) {
            inviter.sendMessage(PREFIX + ChatColor.RED + target.getName() + " is already in the party.");
            return false;
        }
        if (pendingInvites.containsKey(target.getUniqueId())) {
            inviter.sendMessage(PREFIX + ChatColor.RED + target.getName() + " already has a pending invitation.");
            return false;
        }

        pendingInvites.put(target.getUniqueId(), party.getName());

        // Send clickable invite message to target
        TextComponent message = new TextComponent(
                PREFIX + ChatColor.AQUA + inviter.getName() + ChatColor.GREEN + " invites you to join the party "
                        + ChatColor.LIGHT_PURPLE + party.getName() + ChatColor.GREEN + "!");
        target.spigot().sendMessage(message);

        TextComponent acceptBtn = new TextComponent(ChatColor.GREEN + "" + ChatColor.BOLD + "[Accept]");
        acceptBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dungeon party accept"));
        acceptBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.GREEN + "Click to accept the invitation").create()));

        TextComponent space = new TextComponent("  ");

        TextComponent declineBtn = new TextComponent(ChatColor.RED + "" + ChatColor.BOLD + "[Decline]");
        declineBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dungeon party decline"));
        declineBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.RED + "Click to decline the invitation").create()));

        TextComponent buttons = new TextComponent("");
        buttons.addExtra(acceptBtn);
        buttons.addExtra(space);
        buttons.addExtra(declineBtn);
        target.spigot().sendMessage(buttons);

        // Play a notification sound for the target and inviter
        try {
            target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            inviter.playSound(inviter.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        } catch (NoSuchFieldError | IllegalArgumentException ignored) {
            // Sound enum may differ across versions; fail silently
        }
        // Notify the party
        broadcastToParty(party, PREFIX + ChatColor.AQUA + inviter.getName() + ChatColor.YELLOW + " has invited "
                + ChatColor.AQUA + target.getName() + ChatColor.YELLOW + " to the party.");

        return true;
    }

    public boolean acceptInvite(Player player) {
        String partyName = pendingInvites.remove(player.getUniqueId());
        if (partyName == null) {
            return false;
        }
        Party party = parties.get(partyName);
        if (party == null) {
            return false;
        }
        // Ensure player leaves any existing party before joining (do not teleport back
        // when switching)
        leaveParty(player, false);
        party.addMember(player);
        broadcastToParty(party,
                PREFIX + ChatColor.AQUA + player.getName() + ChatColor.GREEN + " has joined the party!");
        saveParties();
        return true;
    }

    public boolean declineInvite(Player player) {
        String partyName = pendingInvites.remove(player.getUniqueId());
        if (partyName == null) {
            return false;
        }
        Party party = parties.get(partyName);
        if (party != null) {
            broadcastToParty(party,
                    PREFIX + ChatColor.AQUA + player.getName() + ChatColor.RED + " has declined the invitation.");
        }
        return true;
    }

    public boolean hasPendingInvite(Player player) {
        return pendingInvites.containsKey(player.getUniqueId());
    }

    public boolean leaveParty(Player player) {
        return leaveParty(player, true);
    }

    /**
     * Leave a party. If teleportBack is true and the player is currently in an
     * instance world,
     * they will be teleported back to their previous world (or main spawn if
     * unknown).
     */
    public boolean leaveParty(Player player, boolean teleportBack) {
        Party party = getPartyByPlayer(player);
        if (party == null) {
            return false;
        }
        party.removeMember(player);
        broadcastToParty(party, PREFIX + ChatColor.AQUA + player.getName() + ChatColor.RED + " has left the party.");

        // If requested, teleport the player back when leaving while in an instance
        if (teleportBack) {
            if (player.getWorld() != null && player.getWorld().getName().startsWith("instance_")) {
                String prev = previousWorlds.remove(player.getUniqueId());
                if (prev != null && Bukkit.getWorld(prev) != null) {
                    player.teleport(Bukkit.getWorld(prev).getSpawnLocation());
                } else {
                    player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                }
                player.sendMessage(PREFIX + ChatColor.GREEN + "Téléporté vers votre monde précédent.");
            }
        }

        if (party.isEmpty()) {
            parties.remove(party.getName());
        }
        saveParties();
        return true;
    }

    public Party getPartyByPlayer(Player player) {
        for (Party party : parties.values()) {
            if (party.hasMember(player)) {
                return party;
            }
        }
        return null;
    }

    public List<String> listParties() {
        List<String> partyList = new ArrayList<>();
        for (Party party : parties.values()) {
            partyList.add(ChatColor.LIGHT_PURPLE + party.getName() + ChatColor.GRAY + " (" + party.getMemberCount()
                    + " players)");
        }
        return partyList;
    }

    public List<String> listPartyNames() {
        return new ArrayList<>(parties.keySet());
    }

    public void broadcastToParty(Party party, String message) {
        for (UUID memberId : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }

    /**
     * Kick a member from a specific party.
     * Returns true if member was removed.
     */
    public boolean kickMember(Party party, UUID memberId) {
        if (party == null || !party.getMembers().contains(memberId))
            return false;
        party.getMembers().remove(memberId);

        // Notify the kicked player if online
        Player kicked = Bukkit.getPlayer(memberId);
        String kickedName = kicked != null ? kicked.getName() : Bukkit.getOfflinePlayer(memberId).getName();

        // If the kicked player is in an instance, teleport them back to previous world
        if (kicked != null && kicked.isOnline()) {
            if (kicked.getWorld() != null && kicked.getWorld().getName().startsWith("instance_")) {
                String prev = previousWorlds.remove(memberId);
                if (prev != null && Bukkit.getWorld(prev) != null) {
                    kicked.teleport(Bukkit.getWorld(prev).getSpawnLocation());
                } else {
                    kicked.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                }
                kicked.sendMessage(PREFIX + ChatColor.RED
                        + "You have been kicked from the party and teleported to your previous world.");
            } else {
                kicked.sendMessage(PREFIX + ChatColor.RED + "You have been kicked from the party " + ChatColor.LIGHT_PURPLE
                        + party.getName());
            }
        }

        broadcastToParty(party, PREFIX + ChatColor.AQUA + kickedName + ChatColor.YELLOW + " has been kicked from the party.");

        if (party.isEmpty()) {
            parties.remove(party.getName());
        }
        saveParties();
        return true;
    }

    /**
     * Disband the given party: notify members, teleport back if needed and remove
     * all invites.
     */
    public boolean disbandParty(Party party) {
        if (party == null)
            return false;

        // Notify and handle members
        for (UUID memberId : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            // String memberName = member != null ? member.getName() :
            // Bukkit.getOfflinePlayer(memberId).getName();
            if (member != null && member.isOnline()) {
                // if in instance, teleport back
                if (member.getWorld() != null && member.getWorld().getName().startsWith("instance_")) {
                    String prev = previousWorlds.remove(memberId);
                    if (prev != null && Bukkit.getWorld(prev) != null) {
                        member.teleport(Bukkit.getWorld(prev).getSpawnLocation());
                    } else {
                        member.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                    }
                    member.sendMessage(PREFIX + ChatColor.RED
                            + "The party has been disbanded and you have been teleported to your previous world.");
                } else {
                    member.sendMessage(PREFIX + ChatColor.RED + "The party " + ChatColor.LIGHT_PURPLE + party.getName()
                            + ChatColor.RED + " has been disbanded.");
                }
            }
        }

        // Remove any pending invites for this party
        pendingInvites.entrySet().removeIf(e -> e.getValue().equals(party.getName()));

        // Remove party
        parties.remove(party.getName());
        saveParties();
        return true;
    }

    public void setPreviousWorld(UUID playerId, String worldName) {
        if (playerId == null || worldName == null)
            return;
        previousWorlds.put(playerId, worldName);
    }

    public String popPreviousWorld(UUID playerId) {
        return previousWorlds.remove(playerId);
    }

    private void saveParties() {
        try {
            if (!partyDataFile.getParentFile().exists()) {
                partyDataFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(partyDataFile)) {
                gson.toJson(parties, writer);
            }
        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to save party data: " + e.getMessage());
        }
    }

    private void loadParties() {
        if (!partyDataFile.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(partyDataFile)) {
            Map<String, Party> loadedParties = gson.fromJson(reader, new TypeToken<Map<String, Party>>() {
            }.getType());
            if (loadedParties != null) {
                parties.putAll(loadedParties);
            }
        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to load party data: " + e.getMessage());
        }
    }

    public static class Party {
        private final String name;
        private final UUID leader;
        private final Set<UUID> members = new HashSet<>();

        public Party(String name, Player leader) {
            this.name = name;
            this.leader = leader.getUniqueId();
            this.members.add(leader.getUniqueId());
        }

        public String getName() {
            return name;
        }

        public boolean addMember(Player player) {
            return members.add(player.getUniqueId());
        }

        public void removeMember(Player player) {
            members.remove(player.getUniqueId());
        }

        public boolean hasMember(Player player) {
            return members.contains(player.getUniqueId());
        }

        public int getMemberCount() {
            return members.size();
        }

        public boolean isEmpty() {
            return members.isEmpty();
        }

        public UUID getLeader() {
            return leader;
        }

        public Set<UUID> getMembers() {
            return members;
        }
    }
}