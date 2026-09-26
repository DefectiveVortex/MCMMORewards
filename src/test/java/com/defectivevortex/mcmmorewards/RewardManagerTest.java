package com.defectivevortex.mcmmorewards;

import com.defectivevortex.mcmmorewards.model.Reward;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
}
