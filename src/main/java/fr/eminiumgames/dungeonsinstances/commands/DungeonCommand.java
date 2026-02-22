package fr.eminiumgames.dungeonsinstances.commands;

import java.io.File;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import fr.eminiumgames.dungeonsinstances.DungeonInstances;
import fr.eminiumgames.dungeonsinstances.managers.PartyManager;

public class DungeonCommand implements CommandExecutor {

    private static final String PREFIX = ChatColor.DARK_PURPLE + "[Donjon] " + ChatColor.RESET;
    private static final String PARTY_PREFIX = PartyManager.PREFIX;

    @SuppressWarnings("deprecation")
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
                        DungeonInstances.getInstance().getDungeonManager().setAIForWorld(editWorld, false);
                        // clear and respawn mobs from saved JSON so their NBT is applied
                        DungeonInstances.getInstance().getDungeonManager().clearMobs(editWorld);
                        DungeonInstances.getInstance().getDungeonManager().spawnSavedMobs(templateName, editWorld);
                        // teleport to configured spawn, not default world spawn
                        player.teleport(DungeonInstances.getInstance().getDungeonManager()
                                .getSpawnLocation(templateName, editWorld));
                        return true;
                    }

                    // remember gamemode so we can restore it after teleport
                    org.bukkit.GameMode previousGM = player.getGameMode();

                    editWorld = DungeonInstances.getInstance().getDungeonManager().createDungeonInstance(templateName,
                            editWorldName);
                    if (editWorld != null) {
                        // immediately disable AI for any mobs already present
                        DungeonInstances.getInstance().getDungeonManager().setAIForWorld(editWorld, false);

                        // Teleport the player to the editing instance spawn (use configured spawn if set)
                        player.teleport(DungeonInstances.getInstance().getDungeonManager()
                                .getSpawnLocation(templateName, editWorld));
                        player.sendMessage("You are now editing the dungeon template: " + templateName);

                        // restore original gamemode rather than inheriting world default
                        player.setGameMode(previousGM);
                    } else {
                        player.sendMessage(
                                "Failed to create an editing instance for the dungeon template: " + templateName);
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
                    File templatesFolder = new File(
                            DungeonInstances.getInstance().getDataFolder().getParentFile().getParentFile(),
                            "templates-dungeons");
                    File templateFolder = new File(templatesFolder, worldNameToSave.replace("editmode_", ""));
                    File editWorldFolder = new File(Bukkit.getWorldContainer(), worldNameToSave);

                    if (!editWorldFolder.exists() || !editWorldFolder.isDirectory()) {
                        player.sendMessage("The editing instance for '" + worldNameToSave + "' does not exist.");
                        return true;
                    }

                    // ensure the world is saved so entities (animals etc.) are written to disk
                    World w = Bukkit.getWorld(worldNameToSave);
                    if (w != null) {
                        w.save();
                        // also persist mob placements
                        DungeonInstances.getInstance().getDungeonManager().saveEditMobs(
                                worldNameToSave.replace("editmode_", ""), w);
                    }
                    // Copy the edited world back to the template folder
                    DungeonInstances.getInstance().getDungeonManager().copyWorld(editWorldFolder, templateFolder);

                    // Unload the world and teleport the player back to the main world
                    DungeonInstances.getInstance().getDungeonManager().unloadDungeonInstance(worldNameToSave);
                    player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());

                    player.sendMessage("The dungeon template '" + worldNameToSave.replace("editmode_", "")
                            + "' has been updated with the changes from the editing instance.");
                    break;

                case "purge":
                    // first unload any instances that are currently loaded
                    for (World loaded : Bukkit.getWorlds()) {
                        if (loaded.getName().startsWith("instance_")) {
                            DungeonInstances.getInstance().getDungeonManager()
                                    .unloadDungeonInstance(loaded.getName());
                        }
                    }

                    // then delete any leftover folders on disk
                    File worldContainer = Bukkit.getWorldContainer();
                    File[] instanceFolders = worldContainer
                            .listFiles((file) -> file.isDirectory() && file.getName().startsWith("instance_"));

                    if (instanceFolders != null && instanceFolders.length > 0) {
                        for (File instanceFolder : instanceFolders) {
                            DungeonInstances.getInstance().getDungeonManager()
                                    .unloadDungeonInstance(instanceFolder.getName());
                            // unloadDungeonInstance already removes the folder, so this
                            // second call is harmless
                        }
                        player.sendMessage("All dungeon instances have been purged.");
                    } else {
                        player.sendMessage("No dungeon instances found to purge.");
                    }
                    break;

                case "setspawn":
                    World currentWorld = player.getWorld();
                    String currentWorldName = currentWorld.getName();

                    if (!currentWorldName.startsWith("editmode_")) {
                        player.sendMessage(PREFIX + ChatColor.RED
                                + "Vous devez être dans un monde en mode édition (editmode_) pour définir le spawn.");
                        return true;
                    }

                    String spawnTemplateName = currentWorldName.replace("editmode_", "");
                    Location spawnLoc = player.getLocation();
                    DungeonInstances.getInstance().getDungeonManager().setSpawnPoint(spawnTemplateName, spawnLoc);
                    player.sendMessage(PREFIX + ChatColor.GREEN + "Spawn du donjon " + ChatColor.LIGHT_PURPLE
                            + spawnTemplateName + ChatColor.GREEN + " défini à votre position !");
                    player.sendMessage(PREFIX + ChatColor.GRAY + "X: " + String.format("%.1f", spawnLoc.getX()) + " Y: "
                            + String.format("%.1f", spawnLoc.getY()) + " Z: " + String.format("%.1f", spawnLoc.getZ()));
                    break;

                default:
                    player.sendMessage("Unknown admin subcommand. Available subcommands: edit, save, purge, setspawn");
                    break;
            }
            return true;
        }

        if (subCommand.equals("instance")) {
            if (args.length < 2) {
                player.sendMessage(PREFIX + ChatColor.YELLOW + "Utilisation: /dungeon instance <nom-du-donjon>");
                return true;
            }

            String dungeonName = args[1];
            PartyManager partyManager = DungeonInstances.getInstance().getPartyManager();
            PartyManager.Party party = partyManager.getPartyByPlayer(player);

            if (party == null) {
                player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Vous n'êtes dans aucun groupe.");
                return true;
            }

            if (!party.getLeader().equals(player.getUniqueId())) {
                player.sendMessage(
                        PARTY_PREFIX + ChatColor.RED + "Seul le chef du groupe peut lancer une instance de donjon.");
                return true;
            }

            World instance = DungeonInstances.getInstance().getDungeonManager().createDungeonInstance(dungeonName,
                    "instance_" + dungeonName + "_" + UUID.randomUUID());
            if (instance != null) {
                partyManager.broadcastToParty(party,
                        PARTY_PREFIX + ChatColor.GREEN + "Le donjon " + ChatColor.LIGHT_PURPLE + dungeonName
                                + ChatColor.GREEN + " a été lancé par " + ChatColor.AQUA + player.getName()
                                + ChatColor.GREEN + " !");
                partyManager.broadcastToParty(party,
                        PARTY_PREFIX + ChatColor.YELLOW + "Téléportation dans 10 secondes...");

                final World dungeonWorld = instance;
                final Location spawnLocation = DungeonInstances.getInstance().getDungeonManager()
                        .getSpawnLocation(dungeonName, dungeonWorld);

                // We iterate members list to play wither sound immediately to give feedback
                // that the instance is launching, then schedule the teleport and countdown
                // messages/sounds
                for (UUID memberId : party.getMembers()) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null && member.isOnline()) {
                        try {
                            member.playSound(member.getLocation(), Sound.ENTITY_WITHER_SPAWN, 2.0f, 1.0f);
                        } catch (NoSuchFieldError | IllegalArgumentException ignored) {
                        }
                        // member.sendMessage(PARTY_PREFIX + ChatColor.YELLOW + "Le donjon " +
                        // ChatColor.LIGHT_PURPLE
                        // + dungeonName + ChatColor.YELLOW + " est en train de se lancer...");
                    }
                }

                // Schedule countdown sounds/messages at 3s, 2s and 1s before teleport
                long teleportDelay = 200L; // 10 seconds
                long t3 = teleportDelay - 60L; // 3 seconds before
                long t2 = teleportDelay - 40L; // 2 seconds before
                long t1 = teleportDelay - 20L; // 1 second before

                Bukkit.getScheduler().runTaskLater(DungeonInstances.getInstance(), () -> {
                    for (UUID memberId : party.getMembers()) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null && member.isOnline()) {
                            try {
                                member.playSound(member.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.6f);
                            } catch (NoSuchFieldError | IllegalArgumentException ignored) {
                            }
                            member.sendMessage(PREFIX + ChatColor.YELLOW + "3...");
                        }
                    }
                }, t3);

                Bukkit.getScheduler().runTaskLater(DungeonInstances.getInstance(), () -> {
                    for (UUID memberId : party.getMembers()) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null && member.isOnline()) {
                            try {
                                member.playSound(member.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.9f);
                            } catch (NoSuchFieldError | IllegalArgumentException ignored) {
                            }
                            member.sendMessage(PREFIX + ChatColor.YELLOW + "2...");
                        }
                    }
                }, t2);

                Bukkit.getScheduler().runTaskLater(DungeonInstances.getInstance(), () -> {
                    for (UUID memberId : party.getMembers()) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null && member.isOnline()) {
                            try {
                                member.playSound(member.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                            } catch (NoSuchFieldError | IllegalArgumentException ignored) {
                            }
                            member.sendMessage(PREFIX + ChatColor.YELLOW + "1...");
                        }
                    }
                }, t1);

                // Final teleport task
                Bukkit.getScheduler().runTaskLater(DungeonInstances.getInstance(), () -> {
                    for (UUID memberId : party.getMembers()) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null && member.isOnline()) {
                            // store previous world before teleporting into the instance
                            DungeonInstances.getInstance().getPartyManager().setPreviousWorld(member.getUniqueId(),
                                    member.getWorld().getName());

                            member.teleport(spawnLocation);
                            member.sendMessage(PREFIX + ChatColor.GREEN + "Vous avez été téléporté dans le donjon : "
                                    + ChatColor.LIGHT_PURPLE + dungeonName);

                            member.playSound(member.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 1.0f);
                        }
                    }
                }, teleportDelay); // 200 ticks = 10 secondes
            } else {
                player.sendMessage(PREFIX + ChatColor.RED + "Échec de la création de l'instance de donjon.");
            }

            return true;
        }

        if (subCommand.equals("list")) {
            File templatesFolder = new File(
                    DungeonInstances.getInstance().getDataFolder().getParentFile().getParentFile(),
                    "templates-dungeons");

            if (!templatesFolder.exists() || !templatesFolder.isDirectory()) {
                player.sendMessage(PREFIX + ChatColor.RED + "Le dossier templates-dungeons n'existe pas.");
                return true;
            }

            File[] dungeonFiles = templatesFolder.listFiles(File::isDirectory);
            if (dungeonFiles == null || dungeonFiles.length == 0) {
                player.sendMessage(PREFIX + ChatColor.YELLOW + "Aucun template de donjon trouvé.");
                return true;
            }

            player.sendMessage(PREFIX + ChatColor.YELLOW + "Donjons disponibles :");
            for (File dungeon : dungeonFiles) {
                player.sendMessage(" " + ChatColor.GRAY + "▪ " + ChatColor.LIGHT_PURPLE + dungeon.getName());
            }

            return true;
        }

        if (subCommand.equals("leave")) {
            World playerWorld = player.getWorld();
            if (!playerWorld.getName().startsWith("instance_")) {
                player.sendMessage(PREFIX + ChatColor.RED + "Vous n'êtes pas dans une instance de donjon.");
                return true;
            }

            // Broadcast to party that player is leaving
            PartyManager partyManager = DungeonInstances.getInstance().getPartyManager();
            PartyManager.Party currentParty = partyManager.getPartyByPlayer(player);
            if (currentParty != null) {
                partyManager.broadcastToParty(currentParty,
                        PARTY_PREFIX + ChatColor.AQUA + player.getName() + ChatColor.YELLOW + " a quitté le donjon.");
            }

            // Teleport player to spawn or a safe location
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            player.sendMessage(PREFIX + ChatColor.GREEN + "Vous avez quitté l'instance de donjon.");

            // Check if the instance is empty and unload it
            if (playerWorld.getPlayers().isEmpty()) {
                DungeonInstances.getInstance().getDungeonManager().unloadDungeonInstance(playerWorld.getName());
            }

            return true;
        }

        if (subCommand.equals("party")) {
            PartyManager partyManager = DungeonInstances.getInstance().getPartyManager();

            if (args.length < 2) {
                player.sendMessage(PARTY_PREFIX + ChatColor.YELLOW
                        + "Utilisation: /dungeon party <create|invite|accept|decline|leave|list|members>");
                return true;
            }

            String partySubCommand = args[1].toLowerCase();

            switch (partySubCommand) {
                case "create":
                    String partyName;
                    if (args.length < 3) {
                        // default party name to creator's player name
                        partyName = player.getName();
                    } else {
                        partyName = args[2];
                    }

                    // sanitize: replace spaces with underscores
                    partyName = partyName.replaceAll("\\s+", "_");

                    if (!partyName.matches("^[a-zA-Z0-9_-]+$")) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED
                                + "Nom de groupe invalide. Seuls les lettres, chiffres, tirets et underscores sont autorisés.");
                        return true;
                    }

                    if (partyManager.createParty(partyName, player)) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.GREEN + "Le groupe " + ChatColor.LIGHT_PURPLE
                                + partyName + ChatColor.GREEN + " a été créé avec succès !");
                    } else {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Un groupe avec ce nom existe déjà.");
                    }
                    break;

                case "invite":
                    if (args.length < 3) {
                        player.sendMessage(
                                PARTY_PREFIX + ChatColor.YELLOW + "Utilisation: /dungeon party invite <joueur>");
                        return true;
                    }
                    Player target = Bukkit.getPlayer(args[2]);
                    if (target == null || !target.isOnline()) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Le joueur " + ChatColor.AQUA + args[2]
                                + ChatColor.RED + " n'est pas en ligne.");
                        return true;
                    }
                    if (target.equals(player)) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Vous ne pouvez pas vous inviter vous-même.");
                        return true;
                    }
                    PartyManager.Party inviterParty = partyManager.getPartyByPlayer(player);
                    if (inviterParty == null) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Vous n'êtes dans aucun groupe.");
                        return true;
                    }
                    if (!inviterParty.getLeader().equals(player.getUniqueId())) {
                        player.sendMessage(
                                PARTY_PREFIX + ChatColor.RED + "Seul le chef du groupe peut inviter des joueurs.");
                        return true;
                    }
                    if (partyManager.invitePlayer(player, target)) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.GREEN + "Invitation envoyée à " + ChatColor.AQUA
                                + target.getName() + ChatColor.GREEN + " !");
                    }
                    break;

                case "accept":
                    if (partyManager.acceptInvite(player)) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.GREEN + "Vous avez accepté l'invitation !");
                    } else {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Vous n'avez aucune invitation en attente.");
                    }
                    break;

                case "decline":
                    if (partyManager.declineInvite(player)) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.GREEN + "Vous avez refusé l'invitation.");
                    } else {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Vous n'avez aucune invitation en attente.");
                    }
                    break;

                case "leave":
                    if (partyManager.leaveParty(player, true)) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.GREEN + "Vous avez quitté votre groupe.");
                    } else {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Vous n'êtes dans aucun groupe.");
                    }
                    break;

                case "list":
                    player.sendMessage(PARTY_PREFIX + ChatColor.YELLOW + "Groupes actifs :");
                    for (String partyInfo : partyManager.listParties()) {
                        player.sendMessage(" " + ChatColor.GRAY + "▪ " + partyInfo);
                    }
                    break;

                case "members":
                    PartyManager.Party memberParty = partyManager.getPartyByPlayer(player);
                    if (memberParty == null) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Vous n'êtes dans aucun groupe.");
                        return true;
                    }
                    player.sendMessage(PARTY_PREFIX + ChatColor.YELLOW + "Membres du groupe " + ChatColor.LIGHT_PURPLE
                            + memberParty.getName() + ChatColor.YELLOW + " :");
                    for (UUID memberId : memberParty.getMembers()) {
                        Player member = Bukkit.getPlayer(memberId);
                        String memberName = member != null ? member.getName()
                                : Bukkit.getOfflinePlayer(memberId).getName();
                        StringBuilder tags = new StringBuilder();
                        if (memberId.equals(memberParty.getLeader())) {
                            tags.append(ChatColor.GOLD + " (Leader)");
                        }
                        if (memberId.equals(player.getUniqueId())) {
                            tags.append(ChatColor.GREEN + " (Vous)");
                        }
                        String status = (member != null && member.isOnline()) ? ChatColor.GREEN + "●"
                                : ChatColor.RED + "●";
                        player.sendMessage(" " + status + " " + ChatColor.AQUA + memberName + tags.toString());
                    }
                    break;

                case "kick":
                    if (args.length < 3) {
                        player.sendMessage(
                                PARTY_PREFIX + ChatColor.YELLOW + "Utilisation: /dungeon party kick <joueur>");
                        return true;
                    }
                    String targetName = args[2];
                    PartyManager.Party leaderParty = partyManager.getPartyByPlayer(player);
                    if (leaderParty == null) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Vous n'êtes dans aucun groupe.");
                        return true;
                    }
                    if (!leaderParty.getLeader().equals(player.getUniqueId())) {
                        player.sendMessage(
                                PARTY_PREFIX + ChatColor.RED + "Seul le chef du groupe peut expulser un membre.");
                        return true;
                    }

                    // Find UUID of the target (online or offline)
                    UUID targetId = null;
                    Player targetPlayer = Bukkit.getPlayerExact(targetName);
                    if (targetPlayer != null) {
                        targetId = targetPlayer.getUniqueId();
                    } else {
                        targetId = Bukkit.getOfflinePlayer(targetName).getUniqueId();
                    }

                    if (targetId == null || !leaderParty.getMembers().contains(targetId)) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Le joueur " + ChatColor.AQUA + targetName
                                + ChatColor.RED + " n'est pas dans votre groupe.");
                        return true;
                    }

                    if (partyManager.kickMember(leaderParty, targetId)) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.GREEN + "Le joueur " + ChatColor.AQUA + targetName
                                + ChatColor.GREEN + " a été expulsé du groupe.");
                    } else {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Impossible d'expulser le joueur.");
                    }
                    break;

                case "disband":
                    PartyManager.Party p = partyManager.getPartyByPlayer(player);
                    if (p == null) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Vous n'êtes dans aucun groupe.");
                        return true;
                    }
                    if (!p.getLeader().equals(player.getUniqueId())) {
                        player.sendMessage(
                                PARTY_PREFIX + ChatColor.RED + "Seul le chef du groupe peut dissoudre le groupe.");
                        return true;
                    }
                    if (partyManager.disbandParty(p)) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.GREEN + "Le groupe " + ChatColor.LIGHT_PURPLE
                                + p.getName() + ChatColor.GREEN + " a été dissous.");
                    } else {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Impossible de dissoudre le groupe.");
                    }
                    break;

                default:
                    player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Sous-commande inconnue. " + ChatColor.YELLOW
                            + "Utilisation: /dungeon party <create|invite|accept|decline|leave|list|members>");
                    break;
            }
            return true;
        }


        // Handle unknown subcommands
        if (!subCommand.equals("admin") && !subCommand.equals("instance") && !subCommand.equals("list")
                && !subCommand.equals("leave") && !subCommand.equals("party")) {
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

        World instance = DungeonInstances.getInstance().getDungeonManager().createDungeonInstance(templateName,
                instanceName);
        if (instance != null) {
            player.teleport(instance.getSpawnLocation());
            player.sendMessage("Teleported to dungeon instance: " + instanceName);
        } else {
            player.sendMessage("Failed to create dungeon instance.");
        }

        return true;
    }
}