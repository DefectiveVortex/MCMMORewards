package com.defectivevortex.mcmmorewards;

import com.gmail.nossr50.events.experience.McMMOPlayerLevelUpEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class LevelRewardListener implements Listener {

    private final RewardManager rewardManager;
    private final McMMOLevelRewards plugin;

    public LevelRewardListener(McMMOLevelRewards plugin, RewardManager rewardManager) {
        this.plugin = plugin;
        this.rewardManager = rewardManager;
    }

    // MONITOR + ignoreCancelled: if another plugin cancels the level up, mcMMO takes the levels
    // back, so we shouldn't pay for them either.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMcMMOLevelUp(McMMOPlayerLevelUpEvent event) {
        String skillName;
        try {
            // getSkill() returns SkillType on old mcMMO Classic and PrimarySkillType on 2.x.
            // Going through reflection keeps one jar working with both.
            Object skill = event.getClass().getMethod("getSkill").invoke(event);
            skillName = skill.toString();
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Couldn't read the skill from a mcMMO level up event: " + e);
            return;
        }

        // mcMMO sends a single event when several levels are gained at once (a big XP drop,
        // /addlevels, ...). getSkillLevel() is the level after the jump, so walk back over
        // every level that was skipped past, or milestones in between would never pay out.
        int newLevel = event.getSkillLevel();
        int gained = Math.max(1, event.getLevelsGained());
        int firstLevel = Math.max(1, newLevel - gained + 1);

        for (int level = firstLevel; level <= newLevel; level++) {
            rewardManager.checkAndGiveRewards(event.getPlayer(), skillName, level);
        }
    }
}
