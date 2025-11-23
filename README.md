# McMMORewards

McMMORewards is a feature-rich plugin that rewards players when they level up mcMMO skills. It supports console commands, direct money payouts via Vault, and MMOCore XP grants.

## Features

### Core Features
- **Skill-Based Rewards**: Define rewards for any mcMMO skill (Mining, Herbalism, Acrobatics, etc.)
- **Multiple Reward Types**: Execute commands, give money (Vault), or grant MMOCore XP
- **Flexible Configuration**: Reward specific levels or every X levels
- **Placeholders**: Use `%player%`, `%skill%`, and `%level%` in commands
- **Reload Command**: Reload configuration without restarting the server

### Reward Types
1. **Console Commands**: Execute any console command as a reward
2. **Vault Money**: Directly deposit money into player accounts (requires Vault + economy plugin)
3. **MMOCore XP**: Grant experience to MMOCore classes or professions (requires MMOCore)

### Advanced Configuration
- **Specific Level Rewards**: Trigger rewards at exact levels (e.g., Level 10, 50, 100)
- **Every X Levels**: Automatically reward players every N levels (e.g., every 5 levels, every 100 levels)
- **Mixed Rewards**: Combine commands, money, and MMOCore XP in a single reward

## Requirements

- **Java 17+**
- **Spigot/Paper 1.20+**
- **mcMMO**: Required (mcMMO Classic)
- **Vault** *(Optional)*: For money rewards
- **MMOCore** *(Optional)*: For MMOCore XP rewards

## Installation

1. Download the `mcmmorewards-1.1.jar` from releases
2. Place it in your server's `plugins` folder
3. Ensure you have `mcMMO` installed
4. *(Optional)* Install `Vault` and an economy plugin for money rewards
5. *(Optional)* Install `MMOCore` for XP rewards
6. Restart your server

## Configuration

The `config.yml` allows you to define rewards with a flexible structure:

### Basic Example (Commands Only)
```yaml
rewards:
  mining:
    10:
      - "give %player% diamond 1"
      - "broadcast %player% has reached Mining level 10!"
```

### Advanced Example (All Features)
```yaml
rewards:
  mining:
    # Specific level rewards
    levels:
      25:
        money: 100.0
        commands:
          - "msg %player% You earned $100 for reaching Mining 25!"
      50:
        money: 500.0
        mmocore-xp:
          class: "warrior"
          amount: 100
        commands:
          - "broadcast %player% reached Mining 50!"

    # Every X levels rewards
    every:
      1:  # Every level
        mmocore-xp:
          class: "main"
          amount: 5
      10:  # Every 10 levels (10, 20, 30...)
        money: 50.0
        commands:
          - "msg %player% Keep up the good work! Here is $50."
      100:  # Every 100 levels (100, 200, 300...)
        money: 1000.0
        commands:
          - "broadcast %player% is a Mining Master! Level %level%!"

  herbalism:
    every:
      5:
        money: 20.0
```

### Configuration Structure

- **`rewards.<skill>.levels.<level>`**: Reward for a specific level
- **`rewards.<skill>.every.<interval>`**: Reward for every X levels
- **`rewards.<skill>.<level>`**: Legacy format (list of commands) - still supported

### Reward Options

Each reward entry can contain:
- **`commands`**: List of console commands to execute
- **`money`**: Amount of money to give (requires Vault)
- **`mmocore-xp`**: MMOCore experience to grant (requires MMOCore)
  - **`class`**: Class/profession name (`"main"` for main class XP)
  - **`amount`**: Amount of XP to grant

### Placeholders

- `%player%` - Player's name
- `%skill%` - Skill name (e.g., "Mining")
- `%level%` - Level reached

## Commands

- `/mcmmorewards reload` - Reloads the `config.yml` file

## Permissions

- `mcmmorewards.reload` - Allows access to the reload command (Default: OP)

## Support

For issues, feature requests, or questions, please open an issue on the GitHub repository.