package com.defectivevortex.mcmmorewards;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

public class McMMOLevelRewards extends JavaPlugin {

    private RewardManager rewardManager;

    @Override
    public void onEnable() {
        // Check for mcMMO
        if (getServer().getPluginManager().getPlugin("mcMMO") == null) {
            getLogger().severe("mcMMO not found! Disabling McMMOLevelRewards.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        rewardManager = new RewardManager(this);
        getServer().getPluginManager().registerEvents(new LevelRewardListener(this, rewardManager), this);

        getLogger().info("McMMOLevelRewards enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("McMMOLevelRewards disabled!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("mcmmorewards")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("mcmmorewards.reload")) {
                    sender.sendMessage("§cYou do not have permission to use this command.");
                    return true;
                }
                reloadConfig();
                sender.sendMessage("§aMcMMOLevelRewards config reloaded!");
                getLogger().info("Config reloaded by " + sender.getName());
                return true;
            }
        }
        return false;
    }
}
