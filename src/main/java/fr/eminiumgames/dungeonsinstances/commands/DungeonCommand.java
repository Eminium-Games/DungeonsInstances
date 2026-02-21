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
                player.sendMessage("Usage: /dungeon admin <subcommand>");
                player.sendMessage("Available subcommands: edit, save, purge");
                return true;
            }

            String adminSubCommand = args[1].toLowerCase();

            switch (adminSubCommand) {
                case "edit":
                    if (args.length < 3) {
                        player.sendMessage("Usage: /dungeon admin edit <world-name>");
                        return true;
                    }

                    String templateName = args[2];

                    // Create a specialized editing instance with a name starting with 'editmode_'
                    String editWorldName = "editmode_" + templateName;
                    World editWorld = Bukkit.getWorld(editWorldName);

                    if (editWorld != null) {
                        player.sendMessage("The dungeon template '" + templateName + "' is already in edit mode.");
                        player.teleport(editWorld.getSpawnLocation());
                        return true;
                    }

                    editWorld = DungeonInstances.getInstance().getDungeonManager().createDungeonInstance(templateName, editWorldName);
                    if (editWorld != null) {
                        // Pass only the template name to setEditMode
                        // DungeonInstances.getInstance().getDungeonManager().setEditMode(templateName, true);

                        // Teleport the player to the editing instance
                        player.teleport(editWorld.getSpawnLocation());
                        player.sendMessage("You are now editing the dungeon template: " + templateName);
                    } else {
                        player.sendMessage("Failed to create an editing instance for the dungeon template: " + templateName);
                    }

                    break;

                case "save":
                    String worldNameToSave;
                    if (args.length < 3) {
                        World currentWorld = player.getWorld();
                        if (!currentWorld.getName().startsWith("editmode_")) {
                            player.sendMessage("You must specify a world to save or be in an edit mode world.");
                            return true;
                        }
                        worldNameToSave = currentWorld.getName();
                    } else {
                        worldNameToSave = args[2];
                    }

                    Bukkit.getLogger().info("[Save Command] World name to save: " + worldNameToSave);

                    if (!worldNameToSave.startsWith("editmode_")) {
                        player.sendMessage("The specified world is not an edit mode world.");
                        return true;
                    }

                    if (!DungeonInstances.getInstance().getDungeonManager().isEditMode(worldNameToSave)) {
                        player.sendMessage("The world '" + worldNameToSave + "' is not in edit mode.");
                        Bukkit.getLogger().info("[Save Command] World '" + worldNameToSave + "' is not in edit mode.");
                        return true;
                    }

                    // Save changes from the editing instance back to the template
                    File templatesFolder = new File(DungeonInstances.getInstance().getDataFolder().getParentFile().getParentFile(), "templates-dungeons");
                    File templateFolder = new File(templatesFolder, worldNameToSave.replace("editmode_", ""));
                    File editWorldFolder = new File(Bukkit.getWorldContainer(), worldNameToSave);

                    if (!editWorldFolder.exists() || !editWorldFolder.isDirectory()) {
                        player.sendMessage("The editing instance for '" + worldNameToSave + "' does not exist.");
                        return true;
                    }

                    // Copy the edited world back to the template folder
                    DungeonInstances.getInstance().getDungeonManager().copyWorld(editWorldFolder, templateFolder);

                    // Unload the world and teleport the player back to the main world
                    DungeonInstances.getInstance().getDungeonManager().unloadDungeonInstance(worldNameToSave);
                    player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());

                    player.sendMessage("The dungeon template '" + worldNameToSave.replace("editmode_", "") + "' has been updated with the changes from the editing instance.");
                    break;

                case "purge":
                    File worldContainer = Bukkit.getWorldContainer();
                    File[] instanceFolders = worldContainer.listFiles((file) -> file.isDirectory() && file.getName().startsWith("instance_"));

                    if (instanceFolders != null) {
                        for (File instanceFolder : instanceFolders) {
                            DungeonInstances.getInstance().getDungeonManager().unloadDungeonInstance(instanceFolder.getName());
                            deleteFolder(instanceFolder);
                        }
                        player.sendMessage("All dungeon instances have been purged.");
                    } else {
                        player.sendMessage("No dungeon instances found to purge.");
                    }
                    break;

                default:
                    player.sendMessage("Unknown admin subcommand. Available subcommands: edit, save, purge");
                    break;
            }
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

        if (subCommand.equals("list")) {
            File templatesFolder = new File(DungeonInstances.getInstance().getDataFolder().getParentFile().getParentFile(), "templates-dungeons");

            if (!templatesFolder.exists() || !templatesFolder.isDirectory()) {
                player.sendMessage("The templates-dungeons folder does not exist or is not a directory.");
                return true;
            }

            File[] dungeonFiles = templatesFolder.listFiles(File::isDirectory);
            if (dungeonFiles == null || dungeonFiles.length == 0) {
                player.sendMessage("No dungeon templates found in the templates-dungeons folder.");
                return true;
            }

            player.sendMessage("Available dungeon templates:");
            for (File dungeon : dungeonFiles) {
                player.sendMessage("- " + dungeon.getName());
            }

            return true;
        }

        if (subCommand.equals("leave")) {
            World playerWorld = player.getWorld();
            if (!playerWorld.getName().startsWith("instance_")) {
                player.sendMessage("You are not in a dungeon instance.");
                return true;
            }

            // Teleport player to spawn or a safe location
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            player.sendMessage("You have left the dungeon instance.");

            // Check if the instance is empty and unload it
            if (playerWorld.getPlayers().isEmpty()) {
                DungeonInstances.getInstance().getDungeonManager().unloadDungeonInstance(playerWorld.getName());
            }

            return true;
        }

        if (subCommand.equals("admin") && args.length > 1 && args[1].equalsIgnoreCase("purge")) {
            File worldContainer = Bukkit.getWorldContainer();
            File[] instanceFolders = worldContainer.listFiles((file) -> file.isDirectory() && file.getName().startsWith("instance_"));

            if (instanceFolders != null) {
                for (File instanceFolder : instanceFolders) {
                    DungeonInstances.getInstance().getDungeonManager().unloadDungeonInstance(instanceFolder.getName());
                    deleteFolder(instanceFolder);
                }
                player.sendMessage("All dungeon instances have been purged.");
            } else {
                player.sendMessage("No dungeon instances found to purge.");
            }

            return true;
        }

        if (subCommand.equals("admin") && args.length > 2 && args[1].equalsIgnoreCase("save")) {
            String templateName = args[2];
            String editWorldName = "edit_" + templateName;

            // Check if the world is in edit mode
            if (!DungeonInstances.getInstance().getDungeonManager().isEditMode(editWorldName)) {
                player.sendMessage("The world '" + editWorldName + "' is not in edit mode.");
                return true;
            }

            // Save changes from the editing instance back to the template
            File templatesFolder = new File(DungeonInstances.getInstance().getDataFolder().getParentFile().getParentFile(), "templates-dungeons");
            File templateFolder = new File(templatesFolder, templateName);
            File editWorldFolder = new File(Bukkit.getWorldContainer(), editWorldName);

            if (!editWorldFolder.exists() || !editWorldFolder.isDirectory()) {
                player.sendMessage("The editing instance for '" + templateName + "' does not exist.");
                return true;
            }

            // Copy the edited world back to the template folder
            DungeonInstances.getInstance().getDungeonManager().copyWorld(editWorldFolder, templateFolder);

            // Unload and delete the editing instance
            DungeonInstances.getInstance().getDungeonManager().unloadDungeonInstance(editWorldName);

            player.sendMessage("The dungeon template '" + templateName + "' has been updated with the changes from the editing instance.");
            return true;
        }

        if (subCommand.equals("admin") && args.length > 2 && args[1].equalsIgnoreCase("edit")) {
            String templateName = args[2];

            // Check if the template exists
            File templatesFolder = new File(DungeonInstances.getInstance().getDataFolder().getParentFile().getParentFile(), "templates-dungeons");
            File templateFolder = new File(templatesFolder, templateName);

            if (!templateFolder.exists() || !templateFolder.isDirectory()) {
                player.sendMessage("Dungeon template '" + templateName + "' does not exist.");
                return true;
            }

            // Create a specialized editing instance with a name starting with 'editmode_'
            String editWorldName = "editmode_" + templateName;
            World editWorld = Bukkit.getWorld(editWorldName);

            if (editWorld != null) {
                player.sendMessage("The dungeon template '" + templateName + "' is already in edit mode.");
                player.teleport(editWorld.getSpawnLocation());
                return true;
            }

            editWorld = DungeonInstances.getInstance().getDungeonManager().createDungeonInstance(templateName, editWorldName);
            if (editWorld != null) {
                // Pass only the template name to setEditMode
                // DungeonInstances.getInstance().getDungeonManager().setEditMode(templateName, true);

                // Teleport the player to the editing instance
                player.teleport(editWorld.getSpawnLocation());
                player.sendMessage("You are now editing the dungeon template: " + templateName);
            } else {
                player.sendMessage("Failed to create an editing instance for the dungeon template: " + templateName);
            }
        }

        // Handle unknown subcommands
        if (!subCommand.equals("admin") && !subCommand.equals("instance") && !subCommand.equals("list") && !subCommand.equals("leave")) {
            player.sendMessage("Unknown subcommand. Available subcommands:");
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

    private static void deleteFolder(File folder) {
        if (folder != null && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteFolder(file);
                }
            }
            folder.delete();
        }
    }
}