package de.mcbesser.marketplace.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public class TradeSession {

    private final UUID firstPlayer;
    private final UUID secondPlayer;
    private final long createdAt;
    private double firstCoins;
    private double secondCoins;
    private boolean firstAccepted;
    private boolean secondAccepted;
    private final List<ItemStack> firstItems = new ArrayList<>();
    private final List<ItemStack> secondItems = new ArrayList<>();

    public TradeSession(UUID firstPlayer, UUID secondPlayer, long createdAt) {
        this.firstPlayer = firstPlayer;
        this.secondPlayer = secondPlayer;
        this.createdAt = createdAt;
    }

    public UUID getFirstPlayer() {
        return firstPlayer;
    }

    public UUID getSecondPlayer() {
        return secondPlayer;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public double getFirstCoins() {
        return firstCoins;
    }

    public void setFirstCoins(double firstCoins) {
        this.firstCoins = firstCoins;
    }

    public double getSecondCoins() {
        return secondCoins;
    }

    public void setSecondCoins(double secondCoins) {
        this.secondCoins = secondCoins;
    }

    public boolean isFirstAccepted() {
        return firstAccepted;
    }

    public void setFirstAccepted(boolean firstAccepted) {
        this.firstAccepted = firstAccepted;
    }

    public boolean isSecondAccepted() {
        return secondAccepted;
    }

    public void setSecondAccepted(boolean secondAccepted) {
        this.secondAccepted = secondAccepted;
    }

    public List<ItemStack> getFirstItems() {
        return firstItems;
    }

    public List<ItemStack> getSecondItems() {
        return secondItems;
    }
}


