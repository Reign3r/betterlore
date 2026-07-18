# NeoForge 1.20.5 Userdev adapter

NeoForge 20.5 publishes the legacy Userdev format, which is built with
NeoGradle 7 on Gradle 8.14.5. The main Stonecutter workspace stays on Gradle 9
for the modern Fabric, Forge, and NeoForge toolchains.

Run the adapter through the root workspace tasks:

```text
:neoforge:1.20.5:legacyUserdevTest
:neoforge:1.20.5:legacyUserdevBuild
```

Those tasks preprocess the selected Stonecutter sources and install a local
wrapper jar before invoking this project. Its final reobfuscated jar is part of
the standard release collector and verifier.
