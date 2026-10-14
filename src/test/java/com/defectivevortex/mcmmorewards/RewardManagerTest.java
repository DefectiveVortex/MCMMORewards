package com.defectivevortex.mcmmorewards;

import com.defectivevortex.mcmmorewards.model.Reward;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class RewardManagerTest {

    private static final Logger LOG = Logger.getLogger("test");

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(text);
        return config;
    }

    private static List<Reward> find(YamlConfiguration config, String skill, int level) {
        return RewardManager.findRewards(config.getConfigurationSection("rewards"), skill, level, LOG);
    }

    @Test
    void defaultConfigParses() throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(in, "config.yml missing from resources");
            config.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        assertNotNull(config.getConfigurationSection("rewards"));
        // Mining 10 in the default config: the old-style list, every 1 and every 10
        assertEquals(3, find(config, "MINING", 10).size());
    }

    @Test
    void legacyListUnderSkill() throws Exception {
        YamlConfiguration c = yaml("rewards:\n  mining:\n    10:\n      - \"say hi %player%\"\n      - 42\n");
        List<Reward> r = find(c, "MINING", 10);
        assertEquals(1, r.size());
        assertEquals(List.of("say hi %player%", "42"), r.get(0).getCommands());
        assertEquals(0, r.get(0).getMoney());
        assertTrue(find(c, "MINING", 11).isEmpty());
    }

    @Test
    void specificLevels() throws Exception {
        YamlConfiguration c = yaml("""
                rewards:
                  mining:
                    levels:
                      25:
                        money: 100.5
                        commands: [ "a" ]
                        mmocore-xp: { class: warrior, amount: 12.5 }
                """);
        List<Reward> r = find(c, "MINING", 25);
        assertEquals(1, r.size());
        assertEquals(100.5, r.get(0).getMoney());
        assertEquals(List.of("a"), r.get(0).getCommands());
        assertEquals("warrior", r.get(0).getMmocoreReward().getName());
        assertEquals(12.5, r.get(0).getMmocoreReward().getAmount());
        assertTrue(find(c, "MINING", 24).isEmpty());
    }

    @Test
    void everyIntervalsStack() throws Exception {
        YamlConfiguration c = yaml("""
                rewards:
                  mining:
                    every:
                      1: { money: 1 }
                      5: { money: 5 }
                      10: { money: 10 }
                      0: { money: 999 }
                      abc: { money: 999 }
                """);
        assertEquals(1, find(c, "MINING", 3).size());
        assertEquals(2, find(c, "MINING", 5).size());
        assertEquals(3, find(c, "MINING", 20).size());
        double total = find(c, "MINING", 20).stream().mapToDouble(Reward::getMoney).sum();
        assertEquals(16, total);
    }

    @Test
    void skillNameIsCaseInsensitive() throws Exception {
        YamlConfiguration c = yaml("rewards:\n  Mining:\n    5:\n      - \"x\"\n  WOODCUTTING:\n    5:\n      - \"y\"\n");
        assertEquals(1, find(c, "MINING", 5).size());
        assertEquals(1, find(c, "mining", 5).size());
        assertEquals(1, find(c, "WOODCUTTING", 5).size());
        assertEquals(1, find(c, "Woodcutting", 5).size());
        assertTrue(find(c, "HERBALISM", 5).isEmpty());
    }

    @Test
    void allFormatsCombine() throws Exception {
        YamlConfiguration c = yaml("""
                rewards:
                  mining:
                    10: [ "legacy" ]
                    levels:
                      10: { commands: [ "level" ] }
                    every:
                      10: { commands: [ "every" ] }
                """);
        List<Reward> r = find(c, "MINING", 10);
        assertEquals(3, r.size());
        assertEquals(List.of("legacy"), r.get(0).getCommands());
        assertEquals(List.of("level"), r.get(1).getCommands());
        assertEquals(List.of("every"), r.get(2).getCommands());
    }

    @Test
    void missingOrBrokenSectionsGiveNothing() throws Exception {
        assertTrue(RewardManager.findRewards(null, "MINING", 1, LOG).isEmpty());
        YamlConfiguration c = yaml("rewards:\n  mining: \"not a section\"\n  herbalism:\n    levels: 5\n    every: 3\n");
        assertTrue(find(c, "MINING", 1).isEmpty());
        assertTrue(find(c, "HERBALISM", 5).isEmpty());
    }

    @Test
    void mmocoreXpNeedsClassAndPositiveAmount() throws Exception {
        YamlConfiguration c = yaml("""
                rewards:
                  mining:
                    levels:
                      1: { mmocore-xp: { amount: 5 } }
                      2: { mmocore-xp: { class: main, amount: 0 } }
                      3: { mmocore-xp: { class: main, amount: 5 } }
                """);
        assertNull(find(c, "MINING", 1).get(0).getMmocoreReward());
        assertNull(find(c, "MINING", 2).get(0).getMmocoreReward());
        assertEquals("main", find(c, "MINING", 3).get(0).getMmocoreReward().getName());
    }

    @Test
    void displayName() {
        assertEquals("Mining", RewardManager.displayName("MINING"));
        assertEquals("Mining", RewardManager.displayName("mining"));
        assertEquals("Unarmed", RewardManager.displayName("UNARMED"));
        assertEquals("Some skill", RewardManager.displayName("SOME_SKILL"));
    }

    // ---- multi-rewards ----

    private static final String ACRO_MINER = """
            multi-rewards:
              acro-miner:
                requires:
                  acrobatics: 10
                  mining: 20
                money: 250
                commands: [ "broadcast %player% did it" ]
            """;

    /**
     * A player whose levels change the way mcMMO changes them: the skill's level is raised
     * first, then every level along the way is checked, as LevelRewardListener does.
     */
    private static final class Sim {
        final YamlConfiguration config;
        final Map<String, Integer> levels = new HashMap<>();
        final List<String> paid = new ArrayList<>();

        Sim(YamlConfiguration config) {
            this.config = config;
        }

        Sim set(String skill, int level) {
            levels.put(skill.toLowerCase(Locale.ROOT), level);
            return this;
        }

        Sim levelUp(String skill, int gained) {
            String key = skill.toLowerCase(Locale.ROOT);
            int newLevel = levels.getOrDefault(key, 0) + gained;
            levels.put(key, newLevel);
            for (int level = Math.max(1, newLevel - gained + 1); level <= newLevel; level++) {
                Map<String, Reward> found = RewardManager.findMultiRewards(config.getConfigurationSection("multi-rewards"),
                        skill.toUpperCase(Locale.ROOT), level, s -> levels.getOrDefault(s, 0));
                for (String name : found.keySet()) {
                    paid.add(name + "@" + skill.toUpperCase(Locale.ROOT) + level);
                }
            }
            return this;
        }
    }

    @Test
    void multiPaysWhenLastRequirementIsMining() throws Exception {
        Sim sim = new Sim(yaml(ACRO_MINER)).set("acrobatics", 12).set("mining", 18);
        sim.levelUp("mining", 1);
        assertTrue(sim.paid.isEmpty());
        sim.levelUp("mining", 1);
        assertEquals(List.of("acro-miner@MINING20"), sim.paid);
    }

    @Test
    void multiPaysWhenLastRequirementIsAcrobatics() throws Exception {
        Sim sim = new Sim(yaml(ACRO_MINER)).set("acrobatics", 9).set("mining", 25);
        sim.levelUp("acrobatics", 1);
        assertEquals(List.of("acro-miner@ACROBATICS10"), sim.paid);
    }

    @Test
    void multiWaitsWhileAnotherRequirementIsUnmet() throws Exception {
        Sim sim = new Sim(yaml(ACRO_MINER)).set("acrobatics", 5).set("mining", 19);
        sim.levelUp("mining", 1);        // mining 20 reached, acrobatics only 5
        sim.levelUp("acrobatics", 4);    // 9: still short
        assertTrue(sim.paid.isEmpty());
        sim.levelUp("acrobatics", 1);    // 10: the last one
        assertEquals(List.of("acro-miner@ACROBATICS10"), sim.paid);
    }

    @Test
    void multiLevelJumpPaysOnceForTheLevelCrossed() throws Exception {
        Sim sim = new Sim(yaml(ACRO_MINER)).set("acrobatics", 10).set("mining", 3);
        sim.levelUp("mining", 40);       // 3 -> 43 in one event, passing 20
        assertEquals(List.of("acro-miner@MINING20"), sim.paid);
    }

    @Test
    void multiNeverPaysTwiceOverAWholeProgression() throws Exception {
        Sim sim = new Sim(yaml(ACRO_MINER));
        for (int i = 0; i < 60; i++) {
            sim.levelUp("mining", 1);
            if (i % 3 == 0) {
                sim.levelUp("acrobatics", 1);
            }
            if (i % 7 == 0) {
                sim.levelUp("herbalism", 5);
            }
        }
        sim.levelUp("mining", 100).levelUp("acrobatics", 100);
        assertEquals(1, sim.paid.size(), sim.paid.toString());
    }

    @Test
    void multiAlreadyMetAtStartDoesNotPayLater() throws Exception {
        // 1.2 behaviour: levels a player already has never pay. Someone past both requirements
        // when the reward is added doesn't get it from later level ups either.
        Sim sim = new Sim(yaml(ACRO_MINER)).set("acrobatics", 30).set("mining", 30);
        sim.levelUp("mining", 5).levelUp("acrobatics", 5);
        assertTrue(sim.paid.isEmpty());
    }

    @Test
    void multiRequirementsStack() throws Exception {
        Sim sim = new Sim(yaml("""
                multi-rewards:
                  triple:
                    requires: { mining: 5, woodcutting: 5, fishing: 5 }
                    commands: [ "x" ]
                """));
        sim.levelUp("mining", 5).levelUp("fishing", 5);
        assertTrue(sim.paid.isEmpty());
        sim.levelUp("woodcutting", 7);
        assertEquals(List.of("triple@WOODCUTTING5"), sim.paid);
    }

    @Test
    void multiRewardsAreIndependent() throws Exception {
        Sim sim = new Sim(yaml("""
                multi-rewards:
                  a:
                    requires: { mining: 10, acrobatics: 10 }
                    money: 1
                  b:
                    requires: { mining: 10, herbalism: 10 }
                    money: 2
                  c:
                    requires: { mining: 10 }
                    money: 3
                """)).set("acrobatics", 10).set("herbalism", 10).set("mining", 9);
        sim.levelUp("mining", 1);
        assertEquals(List.of("a@MINING10", "b@MINING10", "c@MINING10"), sim.paid);
    }

    @Test
    void multiParsesTheRewardItself() throws Exception {
        Map<String, Reward> found = RewardManager.findMultiRewards(yaml(ACRO_MINER).getConfigurationSection("multi-rewards"),
                "MINING", 20, s -> 10);
        Reward r = found.get("acro-miner");
        assertEquals(250, r.getMoney());
        assertEquals(List.of("broadcast %player% did it"), r.getCommands());
    }

    @Test
    void multiSkillNamesIgnoreCaseAndDuplicatesKeepTheHigherLevel() throws Exception {
        YamlConfiguration c = yaml("""
                multi-rewards:
                  x:
                    requires: { Mining: 10, MINING: 15, Acrobatics: 2 }
                """);
        Map<String, Integer> req = RewardManager.parseRequirements(
                c.getConfigurationSection("multi-rewards.x"), "x", null);
        assertEquals(Map.of("mining", 15, "acrobatics", 2), req);
        Sim sim = new Sim(c).set("acrobatics", 2);
        sim.levelUp("mining", 20);
        assertEquals(List.of("x@MINING15"), sim.paid);
    }

    @Test
    void multiBrokenEntriesNeverPay() throws Exception {
        YamlConfiguration c = yaml("""
                multi-rewards:
                  no-requires:
                    money: 5
                  bad-levels:
                    requires: { mining: 0, fishing: -3, acrobatics: abc, herbalism: 10.5 }
                    money: 5
                  partly-bad:
                    requires: { mining: 10, fishing: lots }
                    money: 5
                  not-a-section: 7
                """);
        Sim sim = new Sim(c);
        sim.levelUp("mining", 50).levelUp("fishing", 50).levelUp("acrobatics", 50).levelUp("herbalism", 50);
        // the broken fishing requirement is dropped, so partly-bad is just "mining 10"
        assertEquals(List.of("partly-bad@MINING10"), sim.paid);
        assertEquals(Map.of("mining", 10), RewardManager.parseRequirements(
                c.getConfigurationSection("multi-rewards.partly-bad"), "partly-bad", null));
    }

    @Test
    void multiStringLevelsAreAccepted() throws Exception {
        YamlConfiguration c = yaml("multi-rewards:\n  x:\n    requires:\n      mining: \"12\"\n");
        assertEquals(Map.of("mining", 12), RewardManager.parseRequirements(
                c.getConfigurationSection("multi-rewards.x"), "x", null));
    }

    @Test
    void multiMissingSectionGivesNothing() throws Exception {
        assertTrue(RewardManager.findMultiRewards(null, "MINING", 1, s -> 99).isEmpty());
        // a 1.2 config: no multi-rewards at all
        YamlConfiguration c = yaml("rewards:\n  mining:\n    levels:\n      20: { money: 1 }\n");
        assertTrue(RewardManager.findMultiRewards(c.getConfigurationSection("multi-rewards"), "MINING", 20, s -> 99)
                .isEmpty());
        assertEquals(1, find(c, "MINING", 20).size());
    }

    @Test
    void validateWarnsAboutRewardsThatCanNeverPay() throws Exception {
        YamlConfiguration c = yaml("""
                multi-rewards:
                  ok:
                    requires: { mining: 10, acrobatics: 5 }
                  typo:
                    requires: { minning: 10 }
                  empty:
                    money: 1
                  bad-level:
                    requires: { mining: zero }
                  junk: 3
                """);
        List<String> warnings = new ArrayList<>();
        Logger log = Logger.getLogger("validate-test");
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { warnings.add(record.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        int count = RewardManager.validateMultiRewards(c.getConfigurationSection("multi-rewards"),
                Set.of("mining", "acrobatics")::contains, log);
        assertEquals(4, count);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("typo") && w.contains("minning")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("empty") && w.contains("requires")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("bad-level.requires.mining")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("junk")), warnings.toString());
        assertTrue(warnings.stream().noneMatch(w -> w.contains("multi-rewards.ok")), warnings.toString());
    }

    @Test
    void defaultConfigHasTheAcroMinerExample() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            config.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        Sim sim = new Sim(config).set("mining", 20).set("acrobatics", 9);
        sim.levelUp("acrobatics", 1);
        assertEquals(List.of("acrobatic-miner@ACROBATICS10"), sim.paid);
        Reward r = RewardManager.findMultiRewards(config.getConfigurationSection("multi-rewards"), "ACROBATICS", 10,
                s -> 20).get("acrobatic-miner");
        assertEquals(250, r.getMoney());
    }
}
