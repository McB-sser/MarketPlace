package de.mcbesser.marketplace.jobs;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

public class JobCreateState {

    private final List<JobDraftItem> items = new ArrayList<>();
    private int selectedIndex;
    private double reward;

    public JobCreateState(double reward) {
        this.reward = reward;
    }

    public List<JobDraftItem> getItems() {
        return items;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int selectedIndex) {
        this.selectedIndex = selectedIndex;
    }

    public double getReward() {
        return reward;
    }

    public void setReward(double reward) {
        this.reward = reward;
    }

    public JobDraftItem ensureSelected() {
        if (items.isEmpty()) {
            return null;
        }
        if (selectedIndex < 0 || selectedIndex >= items.size()) {
            selectedIndex = 0;
        }
        return items.get(selectedIndex);
    }

    public int findByMaterial(Material material) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).material() == material) {
                return i;
            }
        }
        return -1;
    }

    public record JobDraftItem(Material material, int amount) {
        public JobDraftItem withAmount(int nextAmount) {
            return new JobDraftItem(material, nextAmount);
        }
    }
}
