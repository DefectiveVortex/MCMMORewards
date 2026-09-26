package com.defectivevortex.mcmmorewards.model;

import java.util.List;

public class Reward {
    private final List<String> commands;
    private final double money;
    private final MMOCoreReward mmocoreReward;

    public Reward(List<String> commands, double money, MMOCoreReward mmocoreReward) {
        this.commands = commands == null ? List.of() : commands;
        this.money = money;
        this.mmocoreReward = mmocoreReward;
    }

    public List<String> getCommands() {
        return commands;
    }

    public double getMoney() {
        return money;
    }

    public MMOCoreReward getMmocoreReward() {
        return mmocoreReward;
    }

    public static class MMOCoreReward {
        private final String name;
        private final double amount;

        public MMOCoreReward(String name, double amount) {
            this.name = name;
            this.amount = amount;
        }

        public String getName() {
            return name;
        }

        public double getAmount() {
            return amount;
        }
    }
}
