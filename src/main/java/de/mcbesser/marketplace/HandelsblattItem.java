package de.mcbesser.marketplace;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class HandelsblattItem {

    private static final String MARKER = "handelsblatt";

    private HandelsblattItem() {
    }

    public static ItemStack create(MarketplacePlugin plugin) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("\u00A76Handelsblatt");
        meta.setLore(List.of(
                "\u00A76Marketplace",
                "\u00A77Rechtsklick \u00f6ffnet den Handel",
                "\u00A77Enth\u00e4lt Markt, Jobs, Lotto,",
                "\u00A77Auktion und Direkthandel",
                "\u00A7eLinksklick auf Spieler:",
                "\u00A7eHandelsanfrage senden/annehmen"
        ));
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, MARKER);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isHandelsblatt(MarketplacePlugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        String value = container.get(key(plugin), PersistentDataType.STRING);
        return MARKER.equals(value);
    }

    public static ShapelessRecipe createRecipe(MarketplacePlugin plugin) {
        ShapelessRecipe recipe = new ShapelessRecipe(key(plugin), create(plugin));
        recipe.addIngredient(Material.PAPER);
        recipe.addIngredient(Material.CHARCOAL);
        return recipe;
    }

    private static NamespacedKey key(MarketplacePlugin plugin) {
        return new NamespacedKey(plugin, "handelsblatt");
    }
}


