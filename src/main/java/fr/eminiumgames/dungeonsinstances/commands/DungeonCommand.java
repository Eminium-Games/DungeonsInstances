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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import fr.eminiumgames.dungeonsinstances.DungeonInstances;
import fr.eminiumgames.dungeonsinstances.managers.DungeonManager;
import fr.eminiumgames.dungeonsinstances.managers.LootTableManager;
import fr.eminiumgames.dungeonsinstances.managers.PartyManager;

public class DungeonCommand implements CommandExecutor {

    private static final String PREFIX = ChatColor.DARK_PURPLE + "[Dungeon] " + ChatColor.RESET;
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
            player.sendMessage(
                    "/dungeon instance <dungeon-name> [difficulty] - Create a dungeon instance (default Normal)");
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
                player.sendMessage("Available subcommands: edit, save, purge, setspawn, alias, reloadloot");
                player.sendMessage(
                        "/dungeon admin save <world> [radius] [y<value>] - persist mobs; optional radius limits to nearby creatures, y<value> ignores mobs below that Y");
                player.sendMessage(
                        "/dungeon admin alias <name> - tag the mob you are looking at so its drops come from the corresponding pool; use 'none' to clear");
                player.sendMessage("/dungeon admin reloadloot - reload the lootTables.json file from disk");
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
                        // only clear/spawn if there are saved mobs and world has none
                        World worldRef = editWorld;
                        String templateRef = templateName;
                        Bukkit.getScheduler().runTaskLater(DungeonInstances.getInstance(), () -> {
                            java.util.List<fr.eminiumgames.dungeonsinstances.managers.DungeonManager.MobData> saved = DungeonInstances
                                    .getInstance().getDungeonManager().loadEditMobs(templateRef);
                            // always re‑populate from the saved file if any entries exist. in
                            // the past we only did this when the world contained no living
                            // entities, which meant stale skeletons from an earlier session
                            // would remain and display incorrectly without their NBT.
                            if (!saved.isEmpty()) {
                                DungeonInstances.getInstance().getDungeonManager().clearMobs(worldRef);
                                DungeonInstances.getInstance().getDungeonManager().spawnSavedMobs(templateRef, worldRef,
                                        DungeonManager.Difficulty.NORMAL);
                            }
                        }, 20L); // 1 second delay
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

                        // Teleport the player to the editing instance spawn (use configured spawn if
                        // set)
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

                case "alias":
                    // set or show loot alias on the mob the player is looking at
                    if (args.length < 3) {
                        // just display current alias if possible
                        org.bukkit.entity.Entity tgt = getTargetEntity(player, 10);
                        if (tgt instanceof LivingEntity) {
                            String cur = ((LivingEntity) tgt).getPersistentDataContainer()
                                    .get(DungeonManager.getLootAliasKey(),
                                            org.bukkit.persistence.PersistentDataType.STRING);
                            player.sendMessage(PREFIX + "Current loot alias: " + (cur == null ? "<none>" : cur));
                        } else {
                            player.sendMessage(PREFIX + "You must be looking at a living entity to set an alias.");
                        }
                        return true;
                    }
                    org.bukkit.entity.Entity tgt = getTargetEntity(player, 10);
                    if (!(tgt instanceof LivingEntity)) {
                        player.sendMessage(PREFIX + "You must look at a mob to assign an alias.");
                        return true;
                    }
                    String newAlias = args[2];
                    if ("none".equalsIgnoreCase(newAlias)) {
                        ((LivingEntity) tgt).getPersistentDataContainer().remove(DungeonManager.getLootAliasKey());
                        player.sendMessage(PREFIX + "Cleared loot alias for the targeted mob.");
                    } else {
                        ((LivingEntity) tgt).getPersistentDataContainer().set(DungeonManager.getLootAliasKey(),
                                org.bukkit.persistence.PersistentDataType.STRING, newAlias);
                        player.sendMessage(PREFIX + "Set loot alias '" + newAlias + "' on the targeted mob.");
                    }
                    return true;
                case "reloadloot":
                    LootTableManager.getInstance().load();
                    player.sendMessage(PREFIX + "Loot tables reloaded.");
                    return true;
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
                    // extra arguments may be filters (radius or y<value>)
                    if (args.length >= 4) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 3; i < args.length; i++) {
                            if (i > 3)
                                sb.append(",");
                            sb.append(args[i]);
                        }
                        player.sendMessage(PREFIX + ChatColor.GRAY + "(filter arguments: " + sb.toString() + ")");
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

                    player.sendMessage(PREFIX + ChatColor.YELLOW + "Saving...");

                    // Ensure the world is saved with all entities (mobs with armor, attributes, NBT
                    // data, etc.)
                    World w = Bukkit.getWorld(worldNameToSave);
                    if (w != null) {
                        w.save();
                        // optional parameters may specify a radius and/or a Y cutoff
                        double radius = -1.0;
                        double minY = Double.NEGATIVE_INFINITY;
                        for (int i = 3; i < args.length; i++) {
                            String param = args[i];
                            if (param.toLowerCase().startsWith("y")) {
                                String num = param.substring(1);
                                if (num.startsWith("=")) {
                                    num = num.substring(1);
                                }
                                try {
                                    minY = Double.parseDouble(num);
                                } catch (NumberFormatException nfe) {
                                    player.sendMessage(PREFIX + ChatColor.RED + "Invalid Y threshold. Use y<value>.");
                                    return true;
                                }
                            } else {
                                try {
                                    radius = Double.parseDouble(param);
                                } catch (NumberFormatException nfe) {
                                    player.sendMessage(
                                            PREFIX + ChatColor.RED + "Invalid radius value. Provide a number.");
                                    return true;
                                }
                            }
                        }
                        if (radius > 0) {
                            player.sendMessage(
                                    PREFIX + ChatColor.GRAY + "Filtering mobs within " + radius + " blocks of you.");
                        }
                        if (minY != Double.NEGATIVE_INFINITY) {
                            player.sendMessage(PREFIX + ChatColor.GRAY + "Ignoring mobs below Y=" + minY + ".");
                        }
                        // always force-load chunks on manual save so no mobs are missed
                        if (radius > 0 || minY != Double.NEGATIVE_INFINITY) {
                            DungeonInstances.getInstance().getDungeonManager().saveEditMobs(
                                    worldNameToSave.replace("editmode_", ""), w,
                                    true, player.getLocation(), radius, minY);
                        } else {
                            DungeonInstances.getInstance().getDungeonManager().saveEditMobs(
                                    worldNameToSave.replace("editmode_", ""), w,
                                    true, null, 0.0, Double.NEGATIVE_INFINITY);
                        }
                    }

                    // Copy the edited world back to the template folder
                    DungeonInstances.getInstance().getDungeonManager().copyWorld(editWorldFolder, templateFolder);

                    // Schedule unload after a delay to ensure all data is saved
                    final String worldToUnload = worldNameToSave;
                    Bukkit.getScheduler().runTaskLater(DungeonInstances.getInstance(), () -> {
                        // DungeonInstances.getInstance().getDungeonManager().unloadDungeonInstance(worldToUnload);
                        player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                        player.sendMessage(PREFIX + ChatColor.GREEN + "Dungeon template '"
                                + worldToUnload.replace("editmode_", "") + "' has been saved successfully!");
                    }, 40L); // 2 secondes de délai pour s'assurer que tout est sauvegardé
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
                                + "You must be in an edit mode world (editmode_) to set the spawn.");
                        return true;
                    }

                    String spawnTemplateName = currentWorldName.replace("editmode_", "");
                    Location spawnLoc = player.getLocation();
                    DungeonInstances.getInstance().getDungeonManager().setSpawnPoint(spawnTemplateName, spawnLoc);
                    player.sendMessage(PREFIX + ChatColor.GREEN + "Dungeon spawn " + ChatColor.LIGHT_PURPLE
                            + spawnTemplateName + ChatColor.GREEN + " set to your current location!");
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
                player.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /dungeon instance <dungeon-name> [difficulty]");
                player.sendMessage(PREFIX + ChatColor.GRAY
                        + "Available difficulties: Beginner, Normal, Heroic, Mythic (default Normal)");
                return true;
            }

            String dungeonName = args[1];
            // parse optional difficulty arg
            DungeonManager.Difficulty difficulty = DungeonManager.Difficulty.NORMAL;
            if (args.length >= 3) {
                difficulty = DungeonManager.Difficulty.fromString(args[2]);
            }

            PartyManager partyManager = DungeonInstances.getInstance().getPartyManager();
            PartyManager.Party party = partyManager.getPartyByPlayer(player);

            if (party == null) {
                player.sendMessage(PARTY_PREFIX + ChatColor.RED + "You are not in a party.");
                return true;
            }

            if (!party.getLeader().equals(player.getUniqueId())) {
                player.sendMessage(
                        PARTY_PREFIX + ChatColor.RED + "Only the party leader can start a dungeon instance.");
                return true;
            }

            World instance = DungeonInstances.getInstance().getDungeonManager()
                    .createDungeonInstance(dungeonName,
                            "instance_" + dungeonName + "_" + UUID.randomUUID(),
                            difficulty);
            if (instance != null) {
                String diffName = difficulty.toString();
                partyManager.broadcastToParty(party,
                        PARTY_PREFIX + ChatColor.GREEN + "Dungeon " + ChatColor.LIGHT_PURPLE + dungeonName
                                + ChatColor.GREEN + " (" + diffName + ") has been started by " + ChatColor.AQUA
                                + player.getName()
                                + ChatColor.GREEN + "!");
                partyManager.broadcastToParty(party,
                        PARTY_PREFIX + ChatColor.YELLOW + "Teleporting in 10 seconds...");

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
                            member.sendMessage(PREFIX + ChatColor.GREEN + "You have been teleported to the dungeon: "
                                    + ChatColor.LIGHT_PURPLE + dungeonName + ChatColor.GREEN + " (" + diffName + ")");

                            member.playSound(member.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 1.0f);
                        }
                    }
                }, teleportDelay); // 200 ticks = 10 secondes
            } else {
                player.sendMessage(PREFIX + ChatColor.RED + "Failed to create dungeon instance.");
            }

            return true;
        }

        if (subCommand.equals("list")) {
            File templatesFolder = new File(
                    DungeonInstances.getInstance().getDataFolder().getParentFile().getParentFile(),
                    "templates-dungeons");

            if (!templatesFolder.exists() || !templatesFolder.isDirectory()) {
                player.sendMessage(PREFIX + ChatColor.RED + "The templates-dungeons folder does not exist.");
                return true;
            }

            File[] dungeonFiles = templatesFolder.listFiles(File::isDirectory);
            if (dungeonFiles == null || dungeonFiles.length == 0) {
                player.sendMessage(PREFIX + ChatColor.YELLOW + "No dungeon templates found.");
                return true;
            }

            player.sendMessage(PREFIX + ChatColor.YELLOW + "Available dungeons:");
            for (File dungeon : dungeonFiles) {
                player.sendMessage(" " + ChatColor.GRAY + "▪ " + ChatColor.LIGHT_PURPLE + dungeon.getName());
            }

            return true;
        }

        if (subCommand.equals("leave")) {
            World playerWorld = player.getWorld();
            if (!playerWorld.getName().startsWith("instance_")) {
                player.sendMessage(PREFIX + ChatColor.RED + "You are not in a dungeon instance.");
                return true;
            }

            // Broadcast to party that player is leaving
            PartyManager partyManager = DungeonInstances.getInstance().getPartyManager();
            PartyManager.Party currentParty = partyManager.getPartyByPlayer(player);
            if (currentParty != null) {
                partyManager.broadcastToParty(currentParty,
                        PARTY_PREFIX + ChatColor.AQUA + player.getName() + ChatColor.YELLOW + " has left the dungeon.");
            }

            // Teleport player to spawn or a safe location
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            player.sendMessage(PREFIX + ChatColor.GREEN + "You have left the dungeon instance.");

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
                        + "Usage: /dungeon party <create|invite|accept|decline|leave|list|members>");
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
                        player.sendMessage(PARTY_PREFIX + ChatColor.GREEN + "Party " + ChatColor.LIGHT_PURPLE
                                + partyName + ChatColor.GREEN + " created successfully!");
                    } else {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "A party with that name already exists.");
                    }
                    break;

                case "invite":
                    if (args.length < 3) {
                        player.sendMessage(
                                PARTY_PREFIX + ChatColor.YELLOW + "Usage: /dungeon party invite <player>");
                        return true;
                    }
                    Player target = Bukkit.getPlayer(args[2]);
                    if (target == null || !target.isOnline()) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Player " + ChatColor.AQUA + args[2]
                                + ChatColor.RED + " is not online.");
                        return true;
                    }
                    if (target.equals(player)) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "You cannot invite yourself.");
                        return true;
                    }
                    PartyManager.Party inviterParty = partyManager.getPartyByPlayer(player);
                    if (inviterParty == null) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "You are not in a party.");
                        return true;
                    }
                    if (!inviterParty.getLeader().equals(player.getUniqueId())) {
                        player.sendMessage(
                                PARTY_PREFIX + ChatColor.RED + "Only the party leader can invite players.");
                        return true;
                    }
                    if (partyManager.invitePlayer(player, target)) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.GREEN + "Invitation sent to " + ChatColor.AQUA
                                + target.getName() + ChatColor.GREEN + "!");
                    }
                    break;

                case "accept":
                    if (partyManager.acceptInvite(player)) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.GREEN + "You have accepted the invitation!");
                    } else {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "You have no pending invitations.");
                    }
                    break;

                case "decline":
                    if (partyManager.declineInvite(player)) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.GREEN + "You have declined the invitation.");
                    } else {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "You have no pending invitations.");
                    }
                    break;

                case "leave":
                    if (partyManager.leaveParty(player, true)) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.GREEN + "You have left your party.");
                    } else {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "You are not in a party.");
                    }
                    break;

                case "list":
                    player.sendMessage(PARTY_PREFIX + ChatColor.YELLOW + "Active parties:");
                    for (String partyInfo : partyManager.listParties()) {
                        player.sendMessage(" " + ChatColor.GRAY + "▪ " + partyInfo);
                    }
                    break;

                case "members":
                    PartyManager.Party memberParty = partyManager.getPartyByPlayer(player);
                    if (memberParty == null) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "You are not in a party.");
                        return true;
                    }
                    player.sendMessage(PARTY_PREFIX + ChatColor.YELLOW + "Members of party " + ChatColor.LIGHT_PURPLE
                            + memberParty.getName() + ChatColor.YELLOW + ":");
                    for (UUID memberId : memberParty.getMembers()) {
                        Player member = Bukkit.getPlayer(memberId);
                        String memberName = member != null ? member.getName()
                                : Bukkit.getOfflinePlayer(memberId).getName();
                        StringBuilder tags = new StringBuilder();
                        if (memberId.equals(memberParty.getLeader())) {
                            tags.append(ChatColor.GOLD + " (Leader)");
                        }
                        if (memberId.equals(player.getUniqueId())) {
                            tags.append(ChatColor.GREEN + " (You)");
                        }
                        String status = (member != null && member.isOnline()) ? ChatColor.GREEN + "●"
                                : ChatColor.RED + "●";
                        player.sendMessage(" " + status + " " + ChatColor.AQUA + memberName + tags.toString());
                    }
                    break;

                case "kick":
                    if (args.length < 3) {
                        player.sendMessage(
                                PARTY_PREFIX + ChatColor.YELLOW + "Usage: /dungeon party kick <player>");
                        return true;
                    }
                    String targetName = args[2];
                    PartyManager.Party leaderParty = partyManager.getPartyByPlayer(player);
                    if (leaderParty == null) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "You are not in a party.");
                        return true;
                    }
                    if (!leaderParty.getLeader().equals(player.getUniqueId())) {
                        player.sendMessage(
                                PARTY_PREFIX + ChatColor.RED + "Only the party leader can kick a member.");
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
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Player " + ChatColor.AQUA + targetName
                                + ChatColor.RED + " is not in your party.");
                        return true;
                    }

                    if (partyManager.kickMember(leaderParty, targetId)) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.GREEN + "Player " + ChatColor.AQUA + targetName
                                + ChatColor.GREEN + " has been kicked from the party.");
                    } else {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Unable to kick the player.");
                    }
                    break;

                case "disband":
                    PartyManager.Party p = partyManager.getPartyByPlayer(player);
                    if (p == null) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "You are not in a party.");
                        return true;
                    }
                    if (!p.getLeader().equals(player.getUniqueId())) {
                        player.sendMessage(
                                PARTY_PREFIX + ChatColor.RED + "Only the party leader can disband the party.");
                        return true;
                    }
                    if (partyManager.disbandParty(p)) {
                        player.sendMessage(PARTY_PREFIX + ChatColor.GREEN + "Party " + ChatColor.LIGHT_PURPLE
                                + p.getName() + ChatColor.GREEN + " has been disbanded.");
                    } else {
                        player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Unable to disband the party.");
                    }
                    break;

                default:
                    player.sendMessage(PARTY_PREFIX + ChatColor.RED + "Unknown subcommand. " + ChatColor.YELLOW
                            + "Usage: /dungeon party <create|invite|accept|decline|leave|list|members>");
                    break;
            }
            return true;
        }

        // Handle unknown subcommands
        if (!subCommand.equals("admin") && !subCommand.equals("instance") && !subCommand.equals("list")
                && !subCommand.equals("leave") && !subCommand.equals("party")) {
            player.sendMessage("Unknown subcommand. Available subcommands:");
            player.sendMessage(
                    "/dungeon instance <dungeon-name> [difficulty] - Create a dungeon instance (default Normal)");
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

    /**
     * Ray-trace from player's eyes and return the first living entity hit
     * within the given distance (excluding the player itself).
     */
    private static org.bukkit.entity.Entity getTargetEntity(Player player, double maxDistance) {
        if (player == null || player.getWorld() == null) {
            return null;
        }
        RayTraceResult res = player.getWorld().rayTraceEntities(player.getEyeLocation(),
                player.getEyeLocation().getDirection(), maxDistance,
                0.5, e -> e instanceof LivingEntity && !e.equals(player));
        if (res != null) {
            return res.getHitEntity();
        }
        return null;
    }
}