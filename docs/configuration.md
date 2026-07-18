# Better Lore configuration

Better Lore creates `config/better_lore.properties` when the mod starts.

```properties
lore_edit_level_cost=1
```

`lore_edit_level_cost` is the number of experience levels added when lore is
changed. It accepts every integer from `0` (free) through `255`. Values outside
that range are clamped, and malformed values fall back to `1`. Restart the
server or integrated client after changing the file.
