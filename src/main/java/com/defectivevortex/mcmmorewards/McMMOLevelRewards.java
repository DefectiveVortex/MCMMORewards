package com.defectivevortex.mcmmorewards;

import com.defectivevortex.mcmmorewards.hooks.MMOCoreHook;
import com.defectivevortex.mcmmorewards.hooks.VaultHook;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class McMMOLevelRewards extends JavaPlugin {

    private RewardManager rewardManager;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("mcMMO") == null) {
            getLogger().severe("mcMMO not found! Disabling McMMOLevelRewards.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        VaultHook vaultHook = new VaultHook(this);
        MMOCoreHook mmoCoreHook = new MMOCoreHook(this);

        rewardManager = new RewardManager(this, vaultHook, mmoCoreHook);
        getServer().getPluginManager().registerEvents(new LevelRewardListener(this, rewardManager), this);

        getLogger().info("McMMOLevelRewards enabled with rewards for " + countSkills() + " skill(s).");
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        // Bukkit fills anything missing from config.yml in from the copy inside the jar. For rewards
        // that's wrong: a reward someone deleted (or never had) would still pay out. Only the file counts.
        getConfig().setDefaults(new MemoryConfiguration());
    }

    @Override
    public void onDisable() {
        getLogger().info("McMMOLevelRewards disabled!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§eUsage: /" + label + " reload");
            return true;
        }
        if (!sender.hasPermission("mcmmorewards.reload")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }
        reloadConfig();
        sender.sendMessage("§aMcMMOLevelRewards config reloaded (" + countSkills() + " skill(s) with rewards).");
        getLogger().info("Config reloaded by " + sender.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("mcmmorewards.reload")
                && "reload".startsWith(args[0].toLowerCase())) {
            return List.of("reload");
        }
        return List.of();
    }

    private int countSkills() {
        ConfigurationSection rewards = getConfig().getConfigurationSection("rewards");
        return rewards == null ? 0 : rewards.getKeys(false).size();
    }
}
