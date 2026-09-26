# McMMORewards

A small add-on for mcMMO that hands out rewards when a player levels up a skill. A reward
can be any mix of:

- console commands (`give`, `lp user ... permission set`, `broadcast`, whatever you like)
- money, paid through Vault
- MMOCore experience, for a class or a profession

You pick which skills pay out and at which levels: an exact level (Mining 50), or every N
levels (every 10 levels of Herbalism).

## Requirements

- Paper 1.20.5 or newer, on Java 17 or newer. Version 1.2 was tested on Paper 26.3, 1.21.11
  and 1.20.6. Older 1.20 servers will probably work with an older mcMMO 2.x, but I haven't
  tried that.
- mcMMO 2.x. Tested with 2.3.001, which itself needs 1.20.5 or newer.
- Vault plus an economy plugin, if you want money rewards (optional).
- MMOCore, if you want MMOCore XP rewards (optional).

Vault and MMOCore aren't required. If one is missing, the plugin logs a warning at startup,
skips that part of any reward, and still runs everything else.

## Installing

1. Grab `mcmmorewards-<version>.jar` from the releases page (GitHub or Modrinth).
2. Drop it into `plugins/` next to mcMMO.
3. Restart the server. A default `plugins/McMMOLevelRewards/config.yml` is created on
   first start.
4. Edit the config, then run `/mcmmorewards reload`.

## Config

Everything lives under `rewards`, one section per mcMMO skill. Skill names aren't case
sensitive, so `mining`, `Mining` and `MINING` all work. Only what's in your file counts:
if you delete one of the example rewards, it's gone.

```yaml
rewards:
  mining:
    # Exact levels
    levels:
      25:
        money: 100
        commands:
          - "msg %player% Here's $100 for reaching %skill% %level%."
      50:
        money: 500
        mmocore-xp:
          class: "mining"   # an MMOCore profession id, or anything else for main class XP
          amount: 100
        commands:
          - "broadcast %player% just hit Mining 50!"

    # Every N levels (every: 10 fires at 10, 20, 30, ...)
    every:
      10:
        money: 50
      100:
        commands:
          - "broadcast %player% reached Mining %level%!"

  herbalism:
    every:
      5:
        money: 20
```

A reward can have any combination of these keys:

| Key | What it does |
|---|---|
| `commands` | List of commands, run from the console. A leading `/` is fine. |
| `money` | Amount to deposit through Vault. |
| `mmocore-xp.class` | The MMOCore profession to give XP in. If the name isn't a profession, the XP goes to the player's main class instead (`main` is a handy name for that). |
| `mmocore-xp.amount` | How much XP. Decimals are allowed. |

Commands can use three placeholders:

- `%player%`: the player's name
- `%skill%`: the skill, written like `Mining` or `Woodcutting`
- `%level%`: the level that was just reached

If a level matches more than one entry, every one of them pays out. At Mining 50 with the
config above, the player gets the `levels: 50` reward and the `every: 10` reward.

The older 1.0 format, a plain list of commands directly under the level, still works:

```yaml
rewards:
  mining:
    10:
      - "give %player% diamond 1"
```

## How level ups are counted

- **Several levels at once.** mcMMO can hand out a pile of levels in one go (a big XP drop,
  `/addlevels`, `/mmoedit`). Each level along the way counts. Going from 8 to 12 with
  `every: 5` configured pays for level 10, even though mcMMO only announces 12.
- **Cancelled level ups.** If another plugin cancels mcMMO's level up event, mcMMO takes the
  levels back and no reward is given.
- **Losing and regaining levels.** The plugin doesn't remember what it already paid. If a
  player drops below a level (an admin edit, or mcMMO's hardcore death penalty if you've
  turned it on) and climbs back up, that level pays out again. Keep that in mind before
  putting big rewards on low levels on a hardcore server.

## Commands and permissions

| Command | Permission | Default |
|---|---|---|
| `/mcmmorewards reload` | `mcmmorewards.reload` | ops |

## Building

```bash
mvn package
```

Needs JDK 17 or newer. The jar ends up in `target/mcmmorewards-<version>.jar`. Everything
comes from public Maven repositories (Paper, mcMMO's nexus, Phoenix Development's nexus for
the MMOCore API, and JitPack for Vault), so no local jars are needed.

## License

GPL-3.0, see [LICENSE](LICENSE).
