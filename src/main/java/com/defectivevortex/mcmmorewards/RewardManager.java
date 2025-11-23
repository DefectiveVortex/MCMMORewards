package com.defectivevortex.mcmmorewards;

import com.defectivevortex.mcmmorewards.hooks.MMOCoreHook;
import com.defectivevortex.mcmmorewards.hooks.VaultHook;
import com.defectivevortex.mcmmorewards.model.Reward;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RewardManager {

    private final McMMOLevelRewards plugin;
    private final Logger logger;
    private final VaultHook vaultHook;
    private final MMOCoreHook mmoCoreHook;

    public RewardManager(McMMOLevelRewards plugin, VaultHook vaultHook, MMOCoreHook mmoCoreHook) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.vaultHook = vaultHook;
        this.mmoCoreHook = mmoCoreHook;
    }

    public void checkAndGiveRewards(Player player, String skillName, int level) {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection rewardsSection = config.getConfigurationSection("rewards");

        if (rewardsSection == null) {
            return;
        }

        // Case-insensitive check for skill name
        ConfigurationSection skillSection = rewardsSection.getConfigurationSection(skillName);
        if (skillSection == null) {
            skillSection = rewardsSection.getConfigurationSection(skillName.toLowerCase());
        }

        if (skillSection == null) {
            return;
        }

        List<Reward> rewardsToGive = new ArrayList<>();

        // 1. Check specific level (Old format: direct under skill)
        if (skillSection.contains(String.valueOf(level))) {
            Reward reward = parseReward(skillSection.get(String.valueOf(level)));
            if (reward != null)
                rewardsToGive.add(reward);
        }

        // 2. Check specific level (New format: under 'levels')
        if (skillSection.isConfigurationSection("levels")) {
            ConfigurationSection levelsSection = skillSection.getConfigurationSection("levels");
            if (levelsSection != null && levelsSection.contains(String.valueOf(level))) {
                Reward reward = parseReward(levelsSection.get(String.valueOf(level)));
                if (reward != null)
                    rewardsToGive.add(reward);
            }
        }

        // 3. Check "every X levels"
        if (skillSection.isConfigurationSection("every")) {
            ConfigurationSection everySection = skillSection.getConfigurationSection("every");
            if (everySection != null) {
                Set<String> keys = everySection.getKeys(false);
                for (String key : keys) {
                    try {
                        int interval = Integer.parseInt(key);
                        if (interval > 0 && level % interval == 0) {
                            Reward reward = parseReward(everySection.get(key));
                            if (reward != null)
                                rewardsToGive.add(reward);
                        }
                    } catch (NumberFormatException e) {
                        logger.warning("Invalid interval in 'every' section for skill " + skillName + ": " + key);
                    }
                }
            }
        }

        // Execute all collected rewards
        for (Reward reward : rewardsToGive) {
            executeReward(player, reward, skillName, level);
        }
    }

    private Reward parseReward(Object data) {
        if (data instanceof List) {
            // Old format: just a list of commands
            return new Reward((List<String>) data, 0, null);
        } else if (data instanceof ConfigurationSection) {
            ConfigurationSection section = (ConfigurationSection) data;
            List<String> commands = section.getStringList("commands");
            double money = section.getDouble("money", 0.0);

            Reward.MMOCoreReward mmoCoreReward = null;
            if (section.isConfigurationSection("mmocore-xp")) {
                ConfigurationSection xpSection = section.getConfigurationSection("mmocore-xp");
                if (xpSection != null) {
                    String clazz = xpSection.getString("class");
                    int amount = xpSection.getInt("amount");
                    if (clazz != null && amount > 0) {
                        mmoCoreReward = new Reward.MMOCoreReward(clazz, amount);
                    }
                }
            }

            return new Reward(commands, money, mmoCoreReward);
        } else if (data instanceof java.util.Map) {
            // Handle MemorySection if needed, but usually ConfigurationSection covers it if
            // accessed via API
            // Sometimes get() returns a Map if not strictly typed, but Bukkit usually
            // returns ConfigurationSection or List
            return null;
        }
        return null;
    }

    private void executeReward(Player player, Reward reward, String skillName, int level) {
        logger.info("Giving rewards to " + player.getName() + " for reaching " + skillName + " level " + level);

        // 1. Commands
        if (reward.getCommands() != null) {
            for (String command : reward.getCommands()) {
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

        // 2. Money
        if (reward.getMoney() > 0) {
            if (vaultHook.isEnabled()) {
                vaultHook.getEconomy().depositPlayer(player, reward.getMoney());
                logger.info("Deposited " + reward.getMoney() + " to " + player.getName());
            } else {
                logger.warning("Attempted to give money reward but Vault is not enabled.");
            }
        }

        // 3. MMOCore XP
        if (reward.getMmocoreReward() != null) {
            if (mmoCoreHook.isEnabled()) {
                mmoCoreHook.giveExperience(player, reward.getMmocoreReward().getName(),
                        reward.getMmocoreReward().getAmount());
            } else {
                logger.warning("Attempted to give MMOCore XP reward but MMOCore is not enabled.");
            }
        }
    }
}
