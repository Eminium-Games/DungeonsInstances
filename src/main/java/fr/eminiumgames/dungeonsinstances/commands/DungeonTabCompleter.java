package fr.eminiumgames.dungeonsinstances.commands;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class DungeonTabCompleter implements TabCompleter {

    private static final String DUNGEON_TEMPLATES_FOLDER = "templates-dungeons";

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            File templatesFolder = new File(DUNGEON_TEMPLATES_FOLDER);
            if (templatesFolder.exists() && templatesFolder.isDirectory()) {
                for (File file : templatesFolder.listFiles()) {
                    if (file.isDirectory()) {
                        suggestions.add(file.getName());
                    }
                }
            }
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

        return suggestions;
    }
}