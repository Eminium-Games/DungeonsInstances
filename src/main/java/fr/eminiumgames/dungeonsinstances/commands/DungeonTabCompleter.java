package fr.eminiumgames.dungeonsinstances.commands;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class DungeonTabCompleter implements TabCompleter {

    private static final String DUNGEON_TEMPLATES_FOLDER = "templates-dungeons";

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            suggestions.add("edit");
            suggestions.add("save");
            suggestions.add("purge");
            return suggestions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("instance")) {
            File templatesFolder = new File(DUNGEON_TEMPLATES_FOLDER);
            if (templatesFolder.exists() && templatesFolder.isDirectory()) {
                for (File file : templatesFolder.listFiles()) {
                    if (file.isDirectory()) {
                        suggestions.add(file.getName());
                    }
                }
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("edit") || args[1].equalsIgnoreCase("save"))) {
            List<String> worldNames = Bukkit.getWorlds().stream()
                .map(World::getName)
                .filter(name -> name.startsWith("editmode_"))
                .collect(Collectors.toList());
            return worldNames;
        }

        return suggestions;
    }
}