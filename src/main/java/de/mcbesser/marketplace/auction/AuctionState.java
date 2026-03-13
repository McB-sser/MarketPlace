package de.mcbesser.marketplace.auction;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public class AuctionState {

    private final UUID sellerId;
    private final String sellerName;
    private final ItemStack item;
    private final long expiresAt;
    private final double startPrice;
    private double currentPrice;
    private UUID highestBidderId;
    private String highestBidderName;

    public AuctionState(UUID sellerId, String sellerName, ItemStack item, long expiresAt, double startPrice) {
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.item = item;
        this.expiresAt = expiresAt;
        this.startPrice = startPrice;
        this.currentPrice = startPrice;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public String getSellerName() {
        return sellerName;
    }

    public ItemStack getItem() {
        return item;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public double getStartPrice() {
        return startPrice;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public UUID getHighestBidderId() {
        return highestBidderId;
    }

    public void setHighestBidderId(UUID highestBidderId) {
        this.highestBidderId = highestBidderId;
    }

    public String getHighestBidderName() {
        return highestBidderName;
    }

    public void setHighestBidderName(String highestBidderName) {
        this.highestBidderName = highestBidderName;
    }
}


