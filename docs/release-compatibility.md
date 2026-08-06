# Release compatibility

Better Lore keeps exact compilation and test coverage for every supported Minecraft/loader pair while minimizing the files users and testers need to handle.

## Published shape

The 52 compile targets are represented by five external jars while the proven
binary implementation families remain explicit:

| Loader | Public files | Public groups | Retained binary families |
| --- | ---: | --- | ---: |
| Fabric | 1 | all supported versions in one carrier | 12 nested implementations |
| NeoForge | 2 | through `1.21.8`; `1.21.9` and newer | 13 |
| Forge | 2 | through `1.21.8` (excluding unsupported `1.21.2`); `1.21.9` and newer | 10 |

Fabric Loader resolves exactly one nested `better_lore_impl` candidate for the running Minecraft version. Forge and NeoForge use two flat adapter jars apiece, split at the resource-pack format-65 boundary. Their descriptors use a Maven union of exact tested Minecraft versions rather than a broad first-to-last interval, so the jars do not claim unbuilt versions. Forge and NeoForge remain separate because their entrypoints, service providers, and loader APIs are distinct.

The canonical partition is `scripts/release_matrix.py`. Collection, verification, Prism deployment, and launch-smoke coverage all read that same model or its generated manifest.

## Safety checks

`collect_release_jars.py` still inspects all 52 exact build outputs. The existing 12 Fabric, 13 NeoForge, and 10 Forge binary families remain the implementation-selection boundaries. Fabric retains one exact implementation per nested candidate. Forge and NeoForge keep byte-identical classes shared and relocate only version-sensitive classes plus their descriptor-level dependants into generated family namespaces. A tiny runtime selector loads one family; inactive generated classes are never initialized.

The collector also:

- generates one public Fabric container with range-specific nested candidates;
- preserves package access and payload signatures while relocating flat-adapter classes;
- selects the active JEI identifier signature before JEI reflects its plugin;
- writes exact-version Maven unions for Forge and NeoForge;
- preserves loader entrypoints and mixins while selecting networking implementations directly;
- writes a schema-2 manifest mapping all 52 runtimes to their five files;
- publishes through atomic file replacement on Windows so rebuilding cannot mutate hardlinked jars in Prism instances;
- excludes high-resolution repository artwork that is not used at runtime.

`verify_release_jars.py` recursively checks the Fabric bundle, flat-adapter descriptors, mapping namespaces, mixins, services, pack formats, source hashes, target coverage, stale files, publication strategies, and size budgets. Pack metadata is emitted on the correct side of Minecraft's format-65 boundary: older groups receive `supported_formats`, while newer groups omit the now-forbidden legacy key.

## Commands

Build every exact target, collect once, and validate the published set:

```powershell
.\gradlew.bat verifyReleaseJars --no-daemon --no-parallel --max-workers=1 --no-configuration-cache
```

Deploy only Better Lore jars to the existing Prism testing matrix:

```powershell
python scripts\provision_prism_instances.py `
  --instances-root "$env:APPDATA\PrismLauncher\instances" `
  --better-lore-only `
  --configure-test-runtime
```

Launch every mapped runtime through Prism using cached metadata and checkpoint the results after each instance:

```powershell
.\scripts\smoke_test_prism_instances.ps1
```

The test-runtime configuration uses a 1024 MiB initial heap and a 2048 MiB maximum heap. It pins LWJGL (and Fabric intermediary mappings) as required offline components, preventing a failed metadata refresh from silently omitting them from the launch classpath. It also disables Forge/NeoForge's optional native early splash in testing instances because that pre-game window can crash or block unattended launches independently of Minecraft and Better Lore. The normal game window and rendering path are still launched.

The smoke harness verifies the installed artifact hash, positive loader discovery, the selected Fabric candidate where applicable, a Java process correlated to the exact instance, the 2048 MiB maximum heap, client readiness, and the absence of Better Lore mixin/linkage/loading failures. `-Resume` reuses only passes whose manifest, selected target set, artifact hash, nested candidate, launch mode, and memory limit still match.

It also closes only Prism processes created for the current `_Testing` launch. This prevents a crashed/non-responsive Prism console from absorbing later command-line launches while preserving any launcher process that existed before the test.

## Launch-certification requirement

The five-artifact partition is release-ready only after all five files pass structural verification and every one of the 52 mapped loader/version targets is launched through Prism at the 2048 MiB limit. Sharing one public file never substitutes for target-by-target launch coverage.

The exception is Forge 26.1. Its current official latest loader, [Forge 62.0.9](https://files.minecraftforge.net/net/minecraftforge/forge/index_26.1.html), crashes during vanilla `FireBlock` bootstrap inside Forge's `FieldToMethodTransformer`. A control launch with an empty `mods` directory reproduces the same stack, proving it is independent of Better Lore. The shared Better Lore Forge artifact successfully reaches readiness on both 26.1.1 and 26.1.2; Forge 26.1 itself cannot be launch-certified until its loader can complete an empty-mod client launch.
