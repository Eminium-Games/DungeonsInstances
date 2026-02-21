package fr.eminiumgames.dungeonsinstances.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class PartyTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            // Suggest subcommands for the first argument
            suggestions.addAll(Arrays.asList("create", "join", "leave", "list"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            // Suggest party names for the second argument if the subcommand is "join"
            // Example: Fetch party names from the PartyManager
            // Replace with actual logic to fetch party names
            suggestions.add("party1");
            suggestions.add("party2");
        }

        return suggestions;
    }
}