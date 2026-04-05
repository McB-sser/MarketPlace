package de.mcbesser.marketplace.market;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;

public class PendingMarketSale {

    private final List<ItemStack> items = new ArrayList<>();
    private Double price;
    private long expiryDuration;
    private MarketPriceMode priceMode = MarketPriceMode.TOTAL;

    public List<ItemStack> getItems() {
        return items;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public long getExpiryDuration() {
        return expiryDuration;
    }

    public void setExpiryDuration(long expiryDuration) {
        this.expiryDuration = expiryDuration;
    }

    public MarketPriceMode getPriceMode() {
        return priceMode;
    }

    public void setPriceMode(MarketPriceMode priceMode) {
        this.priceMode = priceMode;
    }
}
