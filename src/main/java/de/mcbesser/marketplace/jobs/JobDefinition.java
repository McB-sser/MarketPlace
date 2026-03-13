package de.mcbesser.marketplace.jobs;

import java.util.List;
import org.bukkit.Material;

public record JobDefinition(String id, String name, List<JobRequirement> requirements, double reward,
                            long durationMinutes, long cooldownMinutes) {

    public record JobRequirement(Material material, int amount) {
    }
}


