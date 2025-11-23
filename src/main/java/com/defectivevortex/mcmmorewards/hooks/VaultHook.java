package com.defectivevortex.mcmmorewards.hooks;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class VaultHook {
    private Economy economy;
    private boolean enabled = false;

    public VaultHook(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found! Money rewards will be disabled.");
            return;
        }
        setupEconomy(plugin);
    }

    private void setupEconomy(JavaPlugin plugin) {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("No Economy provider found for Vault! Money rewards will be disabled.");
            return;
        }
        economy = rsp.getProvider();
        enabled = true;
        plugin.getLogger().info("Vault Economy hooked successfully.");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Economy getEconomy() {
        return economy;
    }
}
