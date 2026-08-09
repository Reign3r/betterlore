# Server integration API

Server API v1 was introduced in Better Lore 1.3.0. Companion integrations that
use this contract must require Better Lore 1.3.0 or newer.

Better Lore exposes a small server-only integration contract at
`com.reign.betterlore.api.server`. It lets an optional companion choose between
Better Lore's native client editor and another server-authoritative interface
without copying the parser, anvil session state, or result logic.

## Contract v1

Obtain the lazily initialized singleton through `BetterLoreServerApis.get()`.
`BetterLoreServerApi.API_VERSION` and `apiVersion()` both identify contract v1.
The API supports four operations:

- `hasNativeEditor(player)` checks whether that connection advertises Better
  Lore's native editor channel. It does not send a packet or open a screen.
- `currentAnvilSession(player)` returns an immutable snapshot only while the
  player has a valid, non-empty anvil session.
- `validateDraft(draft)` parses every edited field without changing game state
  and returns Better Lore's exact parser feedback plus canonical markup.
- `submitDraft(player, draft)` revalidates the complete draft and applies it
  only when its container and session identifiers still match the player's
  authoritative anvil.

An `AnvilEditorDraft` has separate edit flags so an intentionally cleared name
or lore value is different from an untouched field. Null strings entering the
public records are normalized to empty strings. Submission validates all
edited fields before invoking one combined stateful transition, preventing a
partially accepted name-and-lore update and rebuilding the anvil result only
once.

Calls that inspect or mutate an anvil must run on the logical server thread.
An integration should keep its unsubmitted draft locally; cancelling such a
draft requires no Better Lore mutation.

## Isolation and compatibility

The public package contains only the facade, immutable value records, result
types, Java standard-library types, and `ServerPlayer` at the call boundary.
It has no loader API, client class, Polymer dependency, companion dependency,
dialog type, item stack, component, or packet in its public surface.

The implementation lives behind `CompatibilityRuntime` and delegates to the
same networking capability check, markup parser/decompiler, anvil session, and
result handlers used by Better Lore itself. Fabric, Forge, and NeoForge do not
need separate public contracts. No tick hook, player map, entity, background
task, or additional network message is introduced.

Lore ownership is cooperative edit isolation, not confidentiality or a
security boundary. Better Lore edits only its marked section and preserves
unmarked lore, but every in-process mod can still inspect or replace vanilla
item lore and custom data. The server API intentionally exposes the owned draft
to integrations that choose to participate in the editor workflow.

All five 1.3.0 release artifacts carry API v1. The Fabric artifact remains a
code-free carrier, so the API is inside every version-selected nested
`better_lore_impl` jar. A Fabric companion should compile against the exact
Minecraft-version nested implementation selected for its target (or an
extracted compile-only copy of that same nested jar) and deploy the universal
carrier at runtime. Better Lore does not publish a separate API artifact. The
four flat Forge/NeoForge artifacts expose the API at the archive root.

Release collection fails if a public API class differs between binary families
that share a flat artifact, because that would relocate and hide the class.
Release verification also rejects missing or generated-package copies. This is
an intentional guard against accidentally leaking version- or loader-specific
implementation details into the stable ABI.

Contract v1 is intended to evolve without changing existing binary descriptors,
record constructors, accessors, or enum constants. A future incompatible
contract must use a new major API rather than silently changing these
descriptors. This does not promise compatibility with an arbitrarily breaking
third-party build; it provides the stable boundary that ordinary Better Lore
updates can retain.
