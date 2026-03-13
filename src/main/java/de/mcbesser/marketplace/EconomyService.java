package de.mcbesser.marketplace;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;

public class EconomyService {

    private final MarketplacePlugin plugin;
    private final File file;
    private final Map<UUID, Double> balances = new HashMap<>();
    private Economy vaultEconomy;

    public EconomyService(MarketplacePlugin plugin) throws IOException {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "balances.yml");
        setupVault();
        load();
    }

    private void load() throws IOException {
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("balances.yml konnte nicht erstellt werden.");
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.getConfigurationSection("balances") == null) {
            return;
        }
        for (String key : yaml.getConfigurationSection("balances").getKeys(false)) {
            balances.put(UUID.fromString(key), yaml.getDouble("balances." + key));
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            yaml.set("balances." + entry.getKey(), entry.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Konnte balances.yml nicht speichern: " + exception.getMessage());
        }
    }

    public double getBalance(UUID playerId) {
        if (vaultEconomy != null) {
            return vaultEconomy.getBalance(plugin.getServer().getOfflinePlayer(playerId));
        }
        return balances.getOrDefault(playerId, 0.0D);
    }

    public boolean withdraw(UUID playerId, double amount) {
        if (vaultEconomy != null) {
            return vaultEconomy.withdrawPlayer(plugin.getServer().getOfflinePlayer(playerId), amount).transactionSuccess();
        }
        double current = getBalance(playerId);
        if (current < amount) {
            return false;
        }
        balances.put(playerId, current - amount);
        save();
        return true;
    }

    public void deposit(UUID playerId, double amount) {
        if (vaultEconomy != null) {
            vaultEconomy.depositPlayer(plugin.getServer().getOfflinePlayer(playerId), amount);
            return;
        }
        balances.put(playerId, getBalance(playerId) + amount);
        save();
    }

    private void setupVault() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        if (plugin.getServer().getServicesManager().getRegistration(Economy.class) == null) {
            return;
        }
        vaultEconomy = plugin.getServer().getServicesManager().getRegistration(Economy.class).getProvider();
    }
}


