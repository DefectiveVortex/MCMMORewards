package com.defectivevortex.mcmmorewards;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RewardManager {

    private final McMMOLevelRewards plugin;
    private final Logger logger;

    public RewardManager(McMMOLevelRewards plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void checkAndGiveRewards(Player player, String skillName, int level) {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection rewardsSection = config.getConfigurationSection("rewards");

        if (rewardsSection == null) {
            return;
        }

        // Case-insensitive check for skill name if needed, but config keys are usually case-sensitive or we standardize.
        // mcMMO skill names are usually capitalized or lowercase depending on usage. We'll try exact match first, then lowercase.
        ConfigurationSection skillSection = rewardsSection.getConfigurationSection(skillName);
        if (skillSection == null) {
            skillSection = rewardsSection.getConfigurationSection(skillName.toLowerCase());
        }

        if (skillSection == null) {
            return;
        }

        List<String> commands = skillSection.getStringList(String.valueOf(level));
        if (commands.isEmpty()) {
            return;
        }

        logger.info("Giving rewards to " + player.getName() + " for reaching " + skillName + " level " + level);

        for (String command : commands) {
            String finalCommand = command
                    .replace("%player%", player.getName())
                    .replace("%skill%", skillName)
                    .replace("%level%", String.valueOf(level));

            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to execute reward command: " + finalCommand, e);
            }
        }
    }
}
