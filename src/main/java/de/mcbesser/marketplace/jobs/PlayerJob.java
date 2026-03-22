package de.mcbesser.marketplace.jobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public class PlayerJob {

    private final String instanceId;
    private final String definitionId;
    private final long createdAt;
    private final long expiresAt;
    private final UUID creatorId;
    private final String creatorName;
    private final String customName;
    private final double rewardOverride;
    private final List<JobDefinition.JobRequirement> customRequirements = new ArrayList<>();
    private final Map<String, Integer> delivered = new HashMap<>();
    private final List<ItemStack> storedItems = new ArrayList<>();

    public PlayerJob(String instanceId, String definitionId, long createdAt, long expiresAt) {
        this(instanceId, definitionId, createdAt, expiresAt, null, null, null, 0);
    }

    public PlayerJob(String instanceId, String definitionId, long createdAt, long expiresAt,
                     UUID creatorId, String creatorName, String customName, double rewardOverride) {
        this.instanceId = instanceId;
        this.definitionId = definitionId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.creatorId = creatorId;
        this.creatorName = creatorName;
        this.customName = customName;
        this.rewardOverride = rewardOverride;
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

    public UUID getCreatorId() {
        return creatorId;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public String getCustomName() {
        return customName;
    }

    public double getRewardOverride() {
        return rewardOverride;
    }

    public boolean isCustom() {
        return creatorId != null;
    }

    public List<JobDefinition.JobRequirement> getCustomRequirements() {
        return customRequirements;
    }

    public Map<String, Integer> getDelivered() {
        return delivered;
    }

    public List<ItemStack> getStoredItems() {
        return storedItems;
    }
}


