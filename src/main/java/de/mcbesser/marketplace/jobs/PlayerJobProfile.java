package de.mcbesser.marketplace.jobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerJobProfile {

    private final List<PlayerJob> activeJobs = new ArrayList<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private String pinnedJobInstanceId;

    public List<PlayerJob> getActiveJobs() {
        return activeJobs;
    }

    public Map<String, Long> getCooldowns() {
        return cooldowns;
    }

    public String getPinnedJobInstanceId() {
        return pinnedJobInstanceId;
    }

    public void setPinnedJobInstanceId(String pinnedJobInstanceId) {
        this.pinnedJobInstanceId = pinnedJobInstanceId;
    }
}


