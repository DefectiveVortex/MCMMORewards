package com.defectivevortex.mcmmorewards;

import com.defectivevortex.mcmmorewards.hooks.MMOCoreHook;
import com.defectivevortex.mcmmorewards.hooks.VaultHook;
import com.defectivevortex.mcmmorewards.model.Reward;
import com.gmail.nossr50.api.ExperienceAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
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
        String skill = displayName(skillName);
        List<Reward> rewards = findRewards(plugin.getConfig().getConfigurationSection("rewards"), skillName, level, logger);
        for (Reward reward : rewards) {
            executeReward(player, reward, skill, level, "reaching " + skill + " level " + level);
        }

        ConfigurationSection multiSection = plugin.getConfig().getConfigurationSection("multi-rewards");
        Map<String, Reward> multi = findMultiRewards(multiSection, skillName, level, other -> currentLevel(player, other));
        for (Map.Entry<String, Reward> entry : multi.entrySet()) {
            executeReward(player, entry.getValue(), skill, level,
                    "completing multi-reward '" + entry.getKey() + "' with " + skill + " level " + level);
        }
    }

    // The player's level in another skill right now, or -1 if mcMMO can't tell (unknown skill,
    // profile not loaded yet). -1 never meets a requirement, so the reward waits.
    private int currentLevel(Player player, String skillName) {
        try {
            return ExperienceAPI.getLevel(player, skillName);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /**
     * Multi-rewards completed by reaching this level in this skill, keyed by their name.
     *
     * A multi-reward pays when its LAST requirement is met: this skill has to be one of its
     * requirements, at exactly this level, and every other requirement has to be met already.
     * That makes it pay once, at the level up that completes it, whichever skill comes last,
     * the same way a "levels:" reward pays once when its level is reached.
     */
    static Map<String, Reward> findMultiRewards(ConfigurationSection multiSection, String skillName, int level,
            ToIntFunction<String> currentLevel) {
        Map<String, Reward> found = new LinkedHashMap<>();
        if (multiSection == null) {
            return found;
        }
        String skill = skillName.toLowerCase(Locale.ROOT);
        for (String name : multiSection.getKeys(false)) {
            ConfigurationSection entry = multiSection.getConfigurationSection(name);
            if (entry == null) {
                continue;
            }
            Map<String, Integer> requires = parseRequirements(entry, name, null);
            Integer needed = requires.get(skill);
            if (needed == null || needed != level) {
                continue;
            }
            boolean othersMet = true;
            for (Map.Entry<String, Integer> req : requires.entrySet()) {
                if (!req.getKey().equals(skill) && currentLevel.applyAsInt(req.getKey()) < req.getValue()) {
                    othersMet = false;
                    break;
                }
            }
            if (othersMet) {
                addIfPresent(found, name, parseReward(entry));
            }
        }
        return found;
    }

    /**
     * The "requires" map of one multi-reward: lower case skill name -> level. Entries that
     * aren't a whole number of at least 1 are left out (and reported when a logger is given).
     * The same skill written twice in different case keeps the higher level.
     */
    static Map<String, Integer> parseRequirements(ConfigurationSection entry, String name, Logger logger) {
        Map<String, Integer> requires = new LinkedHashMap<>();
        ConfigurationSection section = entry.getConfigurationSection("requires");
        if (section == null) {
            if (logger != null) {
                logger.warning("multi-rewards." + name + " has no 'requires' section, so it never pays.");
            }
            return requires;
        }
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            int needed = value instanceof Number number && number.doubleValue() == number.intValue()
                    ? number.intValue() : parseWholeNumber(value);
            if (needed < 1) {
                if (logger != null) {
                    logger.warning("Ignoring multi-rewards." + name + ".requires." + key + ": '" + value
                            + "' isn't a level of 1 or more.");
                }
                continue;
            }
            requires.merge(key.toLowerCase(Locale.ROOT), needed, Math::max);
        }
        if (requires.isEmpty() && logger != null) {
            logger.warning("multi-rewards." + name + " has no valid requirements, so it never pays.");
        }
        return requires;
    }

    private static int parseWholeNumber(Object value) {
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                // falls through
            }
        }
        return -1;
    }

    /**
     * Logs a warning for each multi-reward that can never pay: no requirements, a broken
     * level, or a skill mcMMO doesn't know. Returns how many multi-rewards are configured.
     */
    static int validateMultiRewards(ConfigurationSection multiSection, Predicate<String> isSkill, Logger logger) {
        if (multiSection == null) {
            return 0;
        }
        int count = 0;
        for (String name : multiSection.getKeys(false)) {
            ConfigurationSection entry = multiSection.getConfigurationSection(name);
            if (entry == null) {
                logger.warning("Ignoring multi-rewards." + name + ": it isn't a section.");
                continue;
            }
            count++;
            for (String skill : parseRequirements(entry, name, logger).keySet()) {
                if (!isSkill.test(skill)) {
                    logger.warning("multi-rewards." + name + " requires '" + skill
                            + "', which isn't an mcMMO skill, so it never pays.");
                }
            }
        }
        return count;
    }

    public int validateMultiRewards() {
        return validateMultiRewards(plugin.getConfig().getConfigurationSection("multi-rewards"), skill -> {
            try {
                return ExperienceAPI.isValidSkillType(skill);
            } catch (RuntimeException | LinkageError e) {
                return true; // can't check on this mcMMO version; don't cry wolf
            }
        }, logger);
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

    private static void addIfPresent(Map<String, Reward> map, String name, Reward reward) {
        if (reward != null) {
            map.put(name, reward);
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

    private void executeReward(Player player, Reward reward, String skillName, int level, String reason) {
        logger.info("Giving rewards to " + player.getName() + " for " + reason);

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
