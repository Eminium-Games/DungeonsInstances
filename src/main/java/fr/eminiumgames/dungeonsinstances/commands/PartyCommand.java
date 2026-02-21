package fr.eminiumgames.dungeonsinstances.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import fr.eminiumgames.dungeonsinstances.DungeonInstances;
import fr.eminiumgames.dungeonsinstances.managers.PartyManager;

public class PartyCommand implements CommandExecutor {

    private static final String PREFIX = PartyManager.PREFIX;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return true;
        }

        Player player = (Player) sender;
        PartyManager partyManager = DungeonInstances.getInstance().getPartyManager();

        if (args.length < 1) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Utilisation: /dparty <create|join|leave|list> [nom-du-groupe]");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                if (args.length < 2) {
                    player.sendMessage(PREFIX + ChatColor.YELLOW + "Utilisation: /dparty create <nom-du-groupe>");
                    return true;
                }
                String partyName = args[1];
                if (!partyName.matches("^[a-zA-Z0-9_-]+$")) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Nom de groupe invalide. Seuls les lettres, chiffres, tirets et underscores sont autorisés.");
                    return true;
                }
                if (partyManager.createParty(partyName, player)) {
                    player.sendMessage(PREFIX + ChatColor.GREEN + "Le groupe " + ChatColor.LIGHT_PURPLE + partyName + ChatColor.GREEN + " a été créé avec succès !");
                } else {
                    player.sendMessage(PREFIX + ChatColor.RED + "Un groupe avec ce nom existe déjà.");
                }
                break;

            case "join":
                if (args.length < 2) {
                    player.sendMessage(PREFIX + ChatColor.YELLOW + "Utilisation: /dparty join <nom-du-groupe>");
                    return true;
                }
                partyName = args[1];
                if (partyManager.joinParty(partyName, player)) {
                    player.sendMessage(PREFIX + ChatColor.GREEN + "Vous avez rejoint le groupe " + ChatColor.LIGHT_PURPLE + partyName + ChatColor.GREEN + " !");
                } else {
                    player.sendMessage(PREFIX + ChatColor.RED + "Impossible de rejoindre le groupe. Il n'existe pas ou vous êtes déjà dans un groupe.");
                }
                break;

            case "leave":
                if (partyManager.leaveParty(player)) {
                    player.sendMessage(PREFIX + ChatColor.GREEN + "Vous avez quitté votre groupe.");
                } else {
                    player.sendMessage(PREFIX + ChatColor.RED + "Vous n'êtes dans aucun groupe.");
                }
                break;

            case "list":
                player.sendMessage(PREFIX + ChatColor.YELLOW + "Groupes actifs :");
                for (String partyInfo : partyManager.listParties()) {
                    player.sendMessage(" " + ChatColor.GRAY + "▪ " + partyInfo);
                }
                break;

            default:
                player.sendMessage(PREFIX + ChatColor.RED + "Sous-commande inconnue. " + ChatColor.YELLOW + "Utilisation: /dparty <create|join|leave|list> [nom-du-groupe]");
                break;
        }

        return true;
    }
}