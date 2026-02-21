package fr.eminiumgames.dungeonsinstances.commands;

import java.io.File;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import fr.eminiumgames.dungeonsinstances.DungeonInstances;
import fr.eminiumgames.dungeonsinstances.managers.PartyManager;

public class DungeonCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage("Available subcommands:");
            player.sendMessage("/dungeon admin <dungeon-name> - Admin commands for dungeons");
            player.sendMessage("/dungeon instance <dungeon-name> - Create a dungeon instance");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("admin")) {
            if (!player.hasPermission("dungeon.admin")) {
                player.sendMessage("You do not have permission to use this command.");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage("Usage: /dungeon admin <dungeon-name>");
                return true;
            }

            String dungeonName = args[1];
            File templatesFolder = new File("templates-dungeons");
            File dungeonFolder = new File(templatesFolder, dungeonName);

            if (!dungeonFolder.exists() || !dungeonFolder.isDirectory()) {
                player.sendMessage("Dungeon template '" + dungeonName + "' does not exist.");
                return true;
            }

            player.sendMessage("Admin command executed for dungeon: " + dungeonName);
            // Add additional admin logic here

            return true;
        }

        if (subCommand.equals("instance")) {
            if (args.length < 2) {
                player.sendMessage("Usage: /dungeon instance <dungeon-name>");
                return true;
            }

            String dungeonName = args[1];
            PartyManager partyManager = DungeonInstances.getInstance().getPartyManager();
            PartyManager.Party party = partyManager.getPartyByPlayer(player);

            if (party == null) {
                player.sendMessage("You are not in a party.");
                return true;
            }

            if (!party.getLeader().equals(player.getUniqueId())) {
                player.sendMessage("Only the party leader can start a dungeon instance.");
                return true;
            }

            World instance = DungeonInstances.getInstance().getDungeonManager().createDungeonInstance(dungeonName, "instance_" + dungeonName + "_" + UUID.randomUUID());
            if (instance != null) {
                for (UUID memberId : party.getMembers()) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null && member.isOnline()) {
                        member.teleport(instance.getSpawnLocation());
                        member.sendMessage("You have been teleported to the dungeon instance: " + dungeonName);
                    }
                }
                player.sendMessage("Dungeon instance created and party members teleported.");
            } else {
                player.sendMessage("Failed to create dungeon instance.");
            }

            return true;
        }

        // Handle unknown subcommands
        if (!subCommand.equals("admin") && !subCommand.equals("instance")) {
            player.sendMessage("Unknown subcommand. Available subcommands:");
            player.sendMessage("/dungeon admin <dungeon-name> - Admin commands for dungeons");
            player.sendMessage("/dungeon instance <dungeon-name> - Create a dungeon instance");
            return true;
        }

        // Existing logic for other subcommands
        if (args.length < 2) {
            player.sendMessage("Usage: /dungeon <templateName> <instanceName>");
            return true;
        }

        String templateName = args[0];
        String instanceName = args[1];

        World instance = DungeonInstances.getInstance().getDungeonManager().createDungeonInstance(templateName, instanceName);
        if (instance != null) {
            player.teleport(instance.getSpawnLocation());
            player.sendMessage("Teleported to dungeon instance: " + instanceName);
        } else {
            player.sendMessage("Failed to create dungeon instance.");
        }

        return true;
    }
}