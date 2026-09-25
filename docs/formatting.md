# Formatting

Better Lore uses one built-in QuickText-compatible parser for names, lore,
client previews, and server validation on every supported Fabric, Forge, and
NeoForge version. Formatting does not load third-party parser services and does
not depend on Text Placeholder API, even when another mod installs that API.

## Supported tags

| Formatting | Tags | Example |
| --- | --- | --- |
| Color | `c`, `color` | `<c #ff6630>Fire</c>` |
| Smooth gradient | `gr`, `gradient` | `<gr #ff6630 #dbffa9>Sunlit Relic</gr>` |
| Hard gradient | `hgr`, `hard_gradient` | `<hgr red blue>Relic</hgr>` |
| Rainbow | `rb`, `rainbow` | `<rb f:1 s:1 o:0>Rainbow</rb>` |
| Bold | `b`, `bold` | `<b>Bold</b>` |
| Italic | `i`, `italic` | `<i>Italic</i>` |
| Underline | `u`, `underline`, `underlined` | `<u>Underlined</u>` |
| Strikethrough | `s`, `st`, `strikethrough` | `<st>Struck out</st>` |
| Obfuscated | `o`, `obf`, `obfuscated` | `<obf>Hidden</obf>` |

Colors accept six hexadecimal digits, an optional `#`, or Minecraft's named
colors. Color tags also accept `value:`, such as `<c value:red>Text</c>`.
Gradient tags require at least two colors. They use OKLab interpolation by
default; `type:oklab`, `type:hsv` (also `type:hvs`), and `type:hard` explicitly
select their interpolation. These mode attributes are accepted as compatibility
syntax but are hidden from the editor: the editor displays ordinary `<gr>` or
`<hgr>` tags and preserves the selected mode in the saved markup. Rainbow
frequency, saturation, and offset accept
positional values or `f:`, `s:`, `o:` (and their full names), each from 0 to 1.

Formatting can be nested. `</>` closes the innermost supported tag. Final closing
tags may be omitted. Use `\<` for a literal opening angle bracket and `\\` for a
literal backslash. Unknown tags and invalid gradient arguments are displayed
literally; for example, `<gr 0>Text</gr>` remains text and never enters an unbounded argument
loop. Closing tags that do not match the active formatting are also literal and
do not discard the following text.

Names replace line breaks with spaces. Lore supports up to 16 lines. Both use a
4096-character input limit, a 256-tag limit, and a 255-visible-code-point limit;
emoji count as code points, not UTF-16 code units. Legacy section-sign formatting
and invalid Unicode/control characters are rejected.

## Migration in 1.4.0

The editor checks saved Better Lore markup against the item's actual text and
styles using both historical parser behaviors. This works across every supported
Minecraft version and loader, including items moved between loaders. Old unmarked
Forge/NeoForge lore is recognized as editable when its complete visible lore
matches the historical source; replacing or clearing it removes the old section.

Old Fabric gradients retain their original colors, spacing, nested color overrides,
and formatting. They remain ordinary gradient expressions in the editor. Migration
keeps `type:legacy_oklab`, `type:legacy_hsv`, or `type:legacy_hard` as hidden saved
metadata when needed; rainbows keep `type:legacy_hsv` the same way. All loaders
implement these compatibility modes internally, without installing or loading Text
Placeholder API. New expressions keep the shared parser's default behavior.

Malformed historical nesting is normalized into balanced markup while preserving
the visible text, including any literal closing tags the old parser displayed.
Migration also validates names and preserves the normalized source through new
item/entity transfers and block placements. Reading an item does not rewrite its
saved components; applying an edit persists the migrated source and ownership.

Migration does not claim unrelated lore: current inline ownership markers remain
authoritative, and an unmarked item needs a complete match to its saved Better Lore
source. Stale metadata, changed foreign lines, and removed current ownership markers
do not authorize deleting another mod's text.

The server API v1 descriptors, item component keys, and five public compatibility
artifact groups are unchanged by the parser migration.
