package de.mcbesser.marketplace.jobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.inventory.ItemStack;

public class PlayerJob {

    private final String instanceId;
    private final String definitionId;
    private final long createdAt;
    private final long expiresAt;
    private final Map<String, Integer> delivered = new HashMap<>();
    private final List<ItemStack> storedItems = new ArrayList<>();

    public PlayerJob(String instanceId, String definitionId, long createdAt, long expiresAt) {
        this.instanceId = instanceId;
        this.definitionId = definitionId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public Map<String, Integer> getDelivered() {
        return delivered;
    }

    public List<ItemStack> getStoredItems() {
        return storedItems;
    }
}


