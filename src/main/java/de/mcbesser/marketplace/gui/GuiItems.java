package de.mcbesser.marketplace.gui;

import de.mcbesser.marketplace.profile.PlayerHeadCache;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class GuiItems {

    private GuiItems() {
    }

    public static ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(coloredLore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack playerHead(OfflinePlayer player, String name, List<String> lore, PlayerHeadCache headCache) {
        ItemStack item = button(Material.PLAYER_HEAD, name, lore);
        ItemMeta baseMeta = item.getItemMeta();
        if (baseMeta instanceof SkullMeta meta) {
            headCache.applyCachedProfile(meta, player);
            item.setItemMeta(meta);
        }
        return item;
    }
}


