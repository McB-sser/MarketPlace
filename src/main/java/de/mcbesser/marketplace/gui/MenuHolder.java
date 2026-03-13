package de.mcbesser.marketplace.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class MenuHolder implements InventoryHolder {

    private final MenuType type;
    private final int page;
    private final String context;

    public MenuHolder(MenuType type) {
        this(type, 0, "");
    }

    public MenuHolder(MenuType type, int page, String context) {
        this.type = type;
        this.page = page;
        this.context = context;
    }

    public MenuType getType() {
        return type;
    }

    public int getPage() {
        return page;
    }

    public String getContext() {
        return context;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}


