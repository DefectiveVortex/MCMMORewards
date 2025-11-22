# McMMOLevelRewards

McMMOLevelRewards is a lightweight Minecraft plugin that allows server administrators to configure rewards (console commands) that are executed when a player reaches a specific level in an mcMMO skill.

## Features

- **Skill-Based Rewards**: Define rewards for any mcMMO skill (Mining, Herbalism, Acrobatics, etc.).
- **Level-Specific**: Trigger rewards at exact levels (e.g., Level 10, 50, 100).
- **Multiple Commands**: Execute multiple console commands for a single reward.
- **Placeholders**: Use `%player%` to target the player who leveled up.
- **Reload Command**: Reload configuration without restarting the server.

## Requirements

- **Java 17+**
- **Spigot/Paper 1.20+**
- **mcMMO**: This plugin requires mcMMO to be installed and running.

## Installation

1. Download the `McMMOLevelRewards.jar`.
2. Place it in your server's `plugins` folder.
3. Ensure you have `mcMMO` installed.
4. Restart your server.

## Configuration

The `config.yml` allows you to define rewards.

```yaml
rewards:
  mining:
    10:
      - "give %player% diamond 1"
      - "broadcast %player% has reached Mining level 10!"
    50:
      - "eco give %player% 500"
      - "broadcast %player% reached Mining 50!"
  herbalism:
    25:
      - "give %player% golden_apple 1"
```

- **Root key**: `rewards`
- **Skill Name**: (e.g., `mining`, `herbalism`). Must match mcMMO skill names (case-insensitive).
- **Level**: The integer level at which the reward triggers.
- **Commands**: A list of console commands to run. Do not include `/`.

## Commands

- `/mcmmorewards reload` - Reloads the `config.yml` file.

## Permissions

- `mcmmorewards.reload` - Allows access to the reload command (Default: OP).
