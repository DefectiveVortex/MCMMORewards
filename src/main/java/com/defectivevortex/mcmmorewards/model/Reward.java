package com.defectivevortex.mcmmorewards.model;

import java.util.List;

public class Reward {
    private List<String> commands;
    private double money;
    private MMOCoreReward mmocoreReward;

    public Reward(List<String> commands, double money, MMOCoreReward mmocoreReward) {
        this.commands = commands;
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
        private String name;
        private int amount;

        public MMOCoreReward(String name, int amount) {
            this.name = name;
            this.amount = amount;
        }

        public String getName() {
            return name;
        }

        public int getAmount() {
            return amount;
        }
    }
}
