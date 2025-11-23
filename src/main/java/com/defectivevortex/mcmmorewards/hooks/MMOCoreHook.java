package com.defectivevortex.mcmmorewards.hooks;

import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.experience.Profession;
import net.Indyuce.mmocore.experience.EXPSource;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class MMOCoreHook {
    private boolean enabled = false;
    private final JavaPlugin plugin;

    public MMOCoreHook(JavaPlugin plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().getPlugin("MMOCore") == null) {
            plugin.getLogger().warning("MMOCore not found! MMOCore XP rewards will be disabled.");
            return;
        }
        enabled = true;
        plugin.getLogger().info("MMOCore hooked successfully.");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void giveExperience(Player player, String skillOrClass, int amount) {
        if (!enabled)
            return;

        try {
            PlayerData data = PlayerData.get(player);

            // Try to find a profession with the given name
            if (MMOCore.plugin.professionManager.has(skillOrClass)) {
                Profession profession = MMOCore.plugin.professionManager.get(skillOrClass);
                data.getCollectionSkills().giveExperience(profession, amount, EXPSource.OTHER);
                plugin.getLogger().log(Level.INFO,
                        "Gave " + amount + " XP in profession " + skillOrClass + " to " + player.getName());
                return;
            }

            // If not a profession, give Main Class XP
            data.giveExperience(amount, EXPSource.OTHER);
            plugin.getLogger().log(Level.INFO, "Gave " + amount + " Main Class XP to " + player.getName());

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to give MMOCore XP to " + player.getName(), e);
        }
    }
}
