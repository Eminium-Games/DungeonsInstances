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
import org.bukkit.entity.Player;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class PartyManager {

    private final Map<String, Party> parties = new HashMap<>();
    private final File partyDataFile = new File("plugins/DungeonInstances/partyData.json");
    private final Gson gson = new Gson();

    public PartyManager() {
        loadParties();
    }

    public static final String PREFIX = ChatColor.GOLD + "[Party] " + ChatColor.RESET;

    public boolean createParty(String partyName, Player leader) {
        if (parties.containsKey(partyName)) {
            return false; // Party already exists
        }
        Party party = new Party(partyName, leader);
        parties.put(partyName, party);
        saveParties();
        return true;
    }

    public boolean joinParty(String partyName, Player player) {
        Party party = parties.get(partyName);
        if (party == null) {
            return false; // Party does not exist
        }
        boolean added = party.addMember(player);
        if (added) {
            broadcastToParty(party, PREFIX + ChatColor.AQUA + player.getName() + ChatColor.GREEN + " a rejoint le groupe !");
            saveParties();
        }
        return added;
    }

    public boolean leaveParty(Player player) {
        Party party = getPartyByPlayer(player);
        if (party == null) {
            return false; // Player is not in a party
        }
        party.removeMember(player);
        broadcastToParty(party, PREFIX + ChatColor.AQUA + player.getName() + ChatColor.RED + " a quitté le groupe.");
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
            partyList.add(ChatColor.LIGHT_PURPLE + party.getName() + ChatColor.GRAY + " (" + party.getMemberCount() + " joueurs)");
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

    private void saveParties() {
        try {
            // Ensure the directory exists
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
            Map<String, Party> loadedParties = gson.fromJson(reader, new TypeToken<Map<String, Party>>() {}.getType());
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