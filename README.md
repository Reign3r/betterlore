<p align="center">
  <img src="docs/icon.jpg" width="140" alt="Better Lore icon">
</p>

<h1 align="center">Better Lore</h1>

<p align="center">
  <strong>A vanilla-friendly anvil editor for colored item names and lore.</strong>
</p>

<p align="center">
  <a href="https://modrinth.com/mod/better-lore" target="_blank">
    <img src="docs/images/modrinth.png" align="absmiddle" alt="Modrinth" width="18" height="18" />
  </a>
  <a href="https://modrinth.com/mod/better-lore" target="_blank"><b>Modrinth</b></a> | 
  <a href="https://www.curseforge.com/minecraft/mc-mods/better-lore" target="_blank"> 
    <img src="https://cdn.simpleicons.org/curseforge/F16436" alt="CurseForge" width="18" height="18" align="absmiddle" />
  </a> 
  <a href="https://www.curseforge.com/minecraft/mc-mods/better-lore" target="_blank"><b>CurseForge</b>
  </a> | 
  <a href="https://github.com/Reign3r/betterlore" target="_blank"> 
    <img src="https://cdn.simpleicons.org/github/ffffff" alt="GitHub" width="18" height="18" align="absmiddle" />
  </a>
  <a href="https://github.com/Reign3r/betterlore" target="_blank"><b>GitHub</b>
  </a> | 
  <a href="https://github.com/Reign3r/betterlore/issues" target="_blank"> 
    <img src="https://cdn.simpleicons.org/github/ffffff" alt="Issues" width="18" height="18" align="absmiddle" />
  </a> 
  <a href="https://github.com/Reign3r/betterlore/issues" target="_blank"><b>Issues</b>
  </a> | 
  <a href="https://ko-fi.com/reign3r" target="_blank">
    <img src="https://cdn.simpleicons.org/kofi/FF5E5B" alt="Ko-fi" width="18" height="18" align="absmiddle" />
  </a>
  <a href="https://ko-fi.com/reign3r" target="_blank"><b>Support on Ko-fi</b></a>
</p>

## What is Better Lore?

Better Lore adds a survival-friendly extension to the anvil. You can now create unique items by customizing your name or adding lore to them using anvil!

## Features

- Add lore to any item through the anvil.
- Color and format item names.
- Add multiline, colored, formatted lore.
- Pick colors with RGB sliders, direct hex input, or a color wheel.
- Randomize the currently selected color with the dice button.
- Keep Better Lore entries separate from other mods' lore; only Better Lore-owned lines are editable here.
- Preserve exact Better Lore name markup and all lore when boats, minecarts, armor stands, or bucketable mobs make an item/entity/item round trip.
- Keep Better Lore text on water, lava, milk, and powder-snow buckets while they are emptied/refilled; mob buckets show the contained mob's text while privately retaining the bucket's own text for release.
- Preserve exact Better Lore names and lore when copper golems become statues and later awaken or drop again.
- Keep placed Better Lore text attached to dragon eggs when they teleport.
- Lore changes add one extra level to the anvil cost (will be configurable in the future).
- Layout compatibility with JEI and REI.

## QuickText examples

Tags used:

```text
<c #ff6630>Fire</c>
<gr #ff6630 #dbffa9>Sunlit Relic</gr>
<b>Bold</b>
<i>Italic</i>
<underlined>Underlined</underlined>
<st>Strikethrough</st>
<obf>Obfuscated</obf>
```

## Showcase

<div style="max-width: 400px; height: auto">
<h3>Mace example</h3>

![](docs/images/mace.jpg)

<h3>Yummers?</h3>

![](docs/images/moss.jpg)

<h3>Trident with some lore</h3>

![](docs/images/trident.jpg)

</div>

## Installation

### Requirements

- Minecraft Java Edition 26.1.2
- Fabric Loader
- Fabric API
- Text Placeholder API

Fabric API and Text Placeholder API are required dependencies. Install them alongside Better Lore.

### Compatibility releases

Since 1.2.0, Better Lore maintains and publishes only the range compatibility artifacts defined by the release matrix—currently five files, rather than one file per exact loader/version target. The 52 exact-target jars are disposable internal build and test inputs used to prove the ranges; they are not separate releases or maintained deployments. A future public artifact is added only when a real loader or binary-compatibility boundary requires one. See [release compatibility](docs/release-compatibility.md) for the public groups, retained binary-family checks, and Prism smoke-test workflow.

Starting with 1.3.0, server-side companion mods can integrate without copying Better Lore internals through the versioned [server API](docs/server-api.md). API v1 is included in every selected implementation represented by the five compatibility artifacts and does not add a client requirement.

### Client and server behavior

Install Better Lore on the **server** to enable anvil-based lore and name editing.

Install Better Lore on the **client** to use the custom anvil UI. Players without the client mod can still join a modded server, but they will only see the normal vanilla anvil screen.

## Support the project

If Better Lore is useful for your server, modpack, or roleplay setup, consider <a href="https://ko-fi.com/reign3r">supporting the development on Ko-fi</a>. It helps keep the mod maintained, tested, and updated. Thank you!
