package com.defectivevortex.mcmmorewards;

import com.defectivevortex.mcmmorewards.hooks.MMOCoreHook;
import com.defectivevortex.mcmmorewards.hooks.VaultHook;
import com.defectivevortex.mcmmorewards.model.Reward;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        List<Reward> rewards = findRewards(plugin.getConfig().getConfigurationSection("rewards"), skillName, level, logger);
        for (Reward reward : rewards) {
            executeReward(player, reward, displayName(skillName), level);
        }
    }

    /**
     * Everything configured for this skill at this level, in config order: the old
     * "skill.level" list, then "levels.level", then each matching "every" interval.
     */
    static List<Reward> findRewards(ConfigurationSection rewardsSection, String skillName, int level, Logger logger) {
        List<Reward> found = new ArrayList<>();
        if (rewardsSection == null) {
            return found;
        }
        ConfigurationSection skillSection = childIgnoreCase(rewardsSection, skillName);
        if (skillSection == null) {
            return found;
        }

        String levelKey = String.valueOf(level);

        // 1. Old format: a list of commands straight under the skill
        addIfPresent(found, parseReward(skillSection.get(levelKey)));

        // 2. Specific level under 'levels'
        ConfigurationSection levelsSection = skillSection.getConfigurationSection("levels");
        if (levelsSection != null) {
            addIfPresent(found, parseReward(levelsSection.get(levelKey)));
        }

        // 3. Every X levels
        ConfigurationSection everySection = skillSection.getConfigurationSection("every");
        if (everySection != null) {
            for (String key : everySection.getKeys(false)) {
                int interval;
                try {
                    interval = Integer.parseInt(key.trim());
                } catch (NumberFormatException e) {
                    logger.warning("Ignoring '" + key + "' under rewards." + skillSection.getName()
                            + ".every: it isn't a whole number.");
                    continue;
                }
                if (interval > 0 && level % interval == 0) {
                    addIfPresent(found, parseReward(everySection.get(key)));
                }
            }
        }
        return found;
    }

    // mcMMO reports skills as MINING, configs usually say mining or Mining. Match any of them.
    private static ConfigurationSection childIgnoreCase(ConfigurationSection parent, String name) {
        ConfigurationSection exact = parent.getConfigurationSection(name);
        if (exact != null) {
            return exact;
        }
        for (String key : parent.getKeys(false)) {
            if (key.equalsIgnoreCase(name) && parent.isConfigurationSection(key)) {
                return parent.getConfigurationSection(key);
            }
        }
        return null;
    }

    private static void addIfPresent(List<Reward> list, Reward reward) {
        if (reward != null) {
            list.add(reward);
        }
    }

    static Reward parseReward(Object data) {
        if (data instanceof List<?> list) {
            // Old format: just a list of commands
            List<String> commands = new ArrayList<>();
            for (Object entry : list) {
                if (entry != null) {
                    commands.add(entry.toString());
                }
            }
            return new Reward(commands, 0, null);
        }
        if (data instanceof ConfigurationSection section) {
            List<String> commands = section.getStringList("commands");
            double money = section.getDouble("money", 0.0);

            Reward.MMOCoreReward mmoCoreReward = null;
            ConfigurationSection xpSection = section.getConfigurationSection("mmocore-xp");
            if (xpSection != null) {
                String target = xpSection.getString("class");
                double amount = xpSection.getDouble("amount");
                if (target != null && amount > 0) {
                    mmoCoreReward = new Reward.MMOCoreReward(target, amount);
                }
            }
            return new Reward(commands, money, mmoCoreReward);
        }
        return null;
    }

    // MINING -> Mining, used for %skill%
    static String displayName(String skillName) {
        if (skillName == null || skillName.isEmpty()) {
            return skillName;
        }
        String lower = skillName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private void executeReward(Player player, Reward reward, String skillName, int level) {
        logger.info("Giving rewards to " + player.getName() + " for reaching " + skillName + " level " + level);

        // 1. Commands
        for (String command : reward.getCommands()) {
            String finalCommand = command
                    .replace("%player%", player.getName())
                    .replace("%skill%", skillName)
                    .replace("%level%", String.valueOf(level));
            if (finalCommand.startsWith("/")) {
                finalCommand = finalCommand.substring(1);
            }
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to execute reward command: " + finalCommand, e);
            }
        }

        // 2. Money
        if (reward.getMoney() > 0) {
            if (!vaultHook.isEnabled()) {
                logger.warning("Skipped a money reward of " + reward.getMoney() + " for " + player.getName()
                        + ": Vault or an economy plugin isn't installed.");
            } else if (vaultHook.deposit(player, reward.getMoney())) {
                logger.info("Deposited " + reward.getMoney() + " to " + player.getName());
            }
        }

        // 3. MMOCore XP
        Reward.MMOCoreReward xp = reward.getMmocoreReward();
        if (xp != null) {
            if (mmoCoreHook.isEnabled()) {
                mmoCoreHook.giveExperience(player, xp.getName(), xp.getAmount());
            } else {
                logger.warning("Skipped an MMOCore XP reward for " + player.getName() + ": MMOCore isn't installed.");
            }
        }
    }
}
