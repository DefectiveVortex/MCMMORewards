package com.defectivevortex.mcmmorewards.hooks;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class VaultHook {
    private final JavaPlugin plugin;
    private final boolean vaultPresent;
    private Economy economy;

    public VaultHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.vaultPresent = Bukkit.getPluginManager().getPlugin("Vault") != null;
        if (!vaultPresent) {
            plugin.getLogger().warning("Vault not found! Money rewards will be disabled.");
            return;
        }
        if (economy() != null) {
            plugin.getLogger().info("Vault economy hooked (" + economy.getName() + ").");
        } else {
            // Not fatal: the economy plugin may simply enable after us. We look again on the first payout.
            plugin.getLogger().info("Vault found, but no economy is registered yet. Will check again when money is paid out.");
        }
    }

    public boolean isEnabled() {
        return economy() != null;
    }

    /**
     * Pays the player and returns true if the economy accepted it.
     */
    public boolean deposit(OfflinePlayer player, double amount) {
        Economy eco = economy();
        if (eco == null) {
            return false;
        }
        EconomyResponse response = eco.depositPlayer(player, amount);
        if (response == null || !response.transactionSuccess()) {
            plugin.getLogger().warning("Economy refused a deposit of " + amount + " to " + player.getName()
                    + (response != null && response.errorMessage != null ? ": " + response.errorMessage : ""));
            return false;
        }
        return true;
    }

    // Looked up lazily, since the economy provider can register after we enable (or be swapped at runtime).
    private Economy economy() {
        if (!vaultPresent) {
            return null;
        }
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        }
        return economy;
    }
}
