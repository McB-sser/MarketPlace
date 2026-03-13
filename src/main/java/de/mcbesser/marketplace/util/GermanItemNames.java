package de.mcbesser.marketplace.util;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class GermanItemNames {

    private GermanItemNames() {
    }

    public static String of(ItemStack item) {
        if (item == null) {
            return "Unbekannt";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        }
        return of(item.getType());
    }

    public static String of(Material material) {
        return switch (material) {
            case WHEAT -> "Weizen";
            case CARROT -> "Karotte";
            case EGG -> "Ei";
            case MELON_SLICE -> "Melonenscheibe";
            case PUMPKIN -> "K\u00fcrbis";
            case LEATHER -> "Leder";
            case WHITE_WOOL -> "Wei\u00dfe Wolle";
            case BEEF -> "Rindfleisch";
            case HONEY_BOTTLE -> "Honigflasche";
            case SUGAR_CANE -> "Zuckerrohr";
            case PAPER -> "Papier";
            case CHARCOAL -> "Holzkohle";
            case SUNFLOWER -> "Sonnenblume";
            case HAY_BLOCK -> "Heuballen";
            case NAME_TAG -> "Namensschild";
            case GOLD_INGOT -> "Goldbarren";
            case GOLD_NUGGET -> "Goldnugget";
            case GOLD_BLOCK -> "Goldblock";
            case CLOCK -> "Uhr";
            case BOOK -> "Buch";
            case CHEST -> "Truhe";
            case BARREL -> "Fass";
            case HOPPER -> "Trichter";
            case EMERALD -> "Smaragd";
            case EMERALD_BLOCK -> "Smaragdblock";
            case ANVIL -> "Amboss";
            default -> prettify(material.name());
        };
    }

    private static String prettify(String enumName) {
        String lower = enumName.toLowerCase();
        String[] parts = lower.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }
}


