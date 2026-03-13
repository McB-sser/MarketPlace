package de.mcbesser.marketplace.lotto;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public class LottoRound {

    private final int sourceListingId;
    private final UUID sellerId;
    private final ItemStack item;
    private final double itemPrice;
    private final double ticketPrice;
    private final long endsAt;
    private final Map<UUID, Integer> tickets = new HashMap<>();

    public LottoRound(int sourceListingId, UUID sellerId, ItemStack item, double itemPrice, double ticketPrice, long endsAt) {
        this.sourceListingId = sourceListingId;
        this.sellerId = sellerId;
        this.item = item;
        this.itemPrice = itemPrice;
        this.ticketPrice = ticketPrice;
        this.endsAt = endsAt;
    }

    public int getSourceListingId() {
        return sourceListingId;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public ItemStack getItem() {
        return item;
    }

    public double getItemPrice() {
        return itemPrice;
    }

    public double getTicketPrice() {
        return ticketPrice;
    }

    public long getEndsAt() {
        return endsAt;
    }

    public Map<UUID, Integer> getTickets() {
        return tickets;
    }

    public int getTotalTickets() {
        return tickets.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getParticipantCount() {
        return tickets.size();
    }

    public boolean hasItemPrize() {
        return item != null;
    }
}


