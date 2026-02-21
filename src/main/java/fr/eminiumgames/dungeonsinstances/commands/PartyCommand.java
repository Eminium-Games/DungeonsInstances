package fr.eminiumgames.dungeonsinstances.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import fr.eminiumgames.dungeonsinstances.DungeonInstances;
import fr.eminiumgames.dungeonsinstances.managers.PartyManager;

public class PartyCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        PartyManager partyManager = DungeonInstances.getInstance().getPartyManager();

        if (args.length < 1) {
            player.sendMessage("Usage: /dparty <create|join|leave|list> [party-name]");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                if (args.length < 2) {
                    player.sendMessage("Usage: /dparty create <party-name>");
                    return true;
                }
                String partyName = args[1];
                if (partyManager.createParty(partyName, player)) {
                    player.sendMessage("Party '" + partyName + "' created successfully.");
                } else {
                    player.sendMessage("A party with that name already exists.");
                }
                break;

            case "join":
                if (args.length < 2) {
                    player.sendMessage("Usage: /dparty join <party-name>");
                    return true;
                }
                partyName = args[1];
                if (partyManager.joinParty(partyName, player)) {
                    player.sendMessage("You joined the party '" + partyName + "'.");
                } else {
                    player.sendMessage("Failed to join the party. It may not exist or you are already in a party.");
                }
                break;

            case "leave":
                if (partyManager.leaveParty(player)) {
                    player.sendMessage("You left your party.");
                } else {
                    player.sendMessage("You are not in a party.");
                }
                break;

            case "list":
                player.sendMessage("Active parties:");
                for (String partyInfo : partyManager.listParties()) {
                    player.sendMessage(partyInfo);
                }
                break;

            default:
                player.sendMessage("Unknown subcommand. Usage: /dparty <create|join|leave|list> [party-name]");
                break;
        }

        return true;
    }
}