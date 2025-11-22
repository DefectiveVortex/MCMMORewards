package com.defectivevortex.mcmmorewards;

import com.gmail.nossr50.events.experience.McMMOPlayerLevelUpEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class LevelRewardListener implements Listener {

    private final RewardManager rewardManager;
    private final McMMOLevelRewards plugin;

    public LevelRewardListener(McMMOLevelRewards plugin, RewardManager rewardManager) {
        this.plugin = plugin;
        this.rewardManager = rewardManager;
    }

    @EventHandler
    public void onMcMMOLevelUp(McMMOPlayerLevelUpEvent event) {
        String skillName;
        int newLevel;

        try {
            // Use reflection to get skill name to support both mcMMO Classic (returns
            // SkillType) and Modern (returns PrimarySkillType)
            Object skillObj = event.getClass().getMethod("getSkill").invoke(event);
            skillName = skillObj.toString();

            // getSkillLevel() usually returns int in both versions, but let's be safe or
            // just use the direct call if it's stable.
            // The error was specifically about getSkill(), so we'll trust getSkillLevel()
            // for now,
            // but if getSkillLevel also changed (unlikely for int), we'd need reflection
            // there too.
            // However, to be purely safe against the "NoSuchMethodError" for the getter, we
            // can use reflection for both.
            newLevel = event.getSkillLevel();
        } catch (Exception e) {
            // Fallback or log error if structure is completely different
            plugin.getLogger().warning(
                    "Could not retrieve skill info from McMMOPlayerLevelUpEvent via reflection: " + e.getMessage());
            return;
        }

        rewardManager.checkAndGiveRewards(event.getPlayer(), skillName, newLevel);
    }
}
