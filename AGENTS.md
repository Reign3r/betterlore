# Better Lore repository rules

These instructions apply to the entire repository.

## Release artifact policy

- Since Better Lore 1.2.0, maintain and publish only the range compatibility artifacts returned by `scripts/release_matrix.py`'s `published_artifacts()` function. There are currently five public jars; this number may grow only when a real loader, metadata, resource, or binary-compatibility boundary requires a new range.
- Do not publish, document as releases, or maintain deployments for the 52 exact Minecraft/loader build outputs. They are disposable internal inputs used to assemble and validate the public range artifacts.
- Keep every declared target compiling and tested. Reducing the number of maintained artifacts must never reduce the supported version/loader matrix, per-target verification, or launch-smoke evidence.
- Collection, verification, provisioning, and smoke-test tooling must consume the canonical release matrix or its generated manifest. Do not introduce a second hand-maintained artifact map.

## Server API compatibility

- Server API v1 begins in Better Lore 1.3.0 and lives under `com.reign.betterlore.api.server`.
- Keep the public API loader-neutral, server-only, and free of client, Polymer, companion-mod, loader-specific, packet, component, and implementation types. Put version-sensitive behavior behind internal adapters.
- Preserve the v1 binary descriptors across every selected implementation in all public range artifacts. Evolve v1 additively; use a new major API for incompatible changes.
- Do not advertise a separately published API jar. Fabric consumers compile against the exact target's selected nested implementation, or an extracted compile-only copy of it, and deploy the public range carrier at runtime.

## Verification

- Treat the common automated tests and release-archive ABI checks as required regression coverage for API changes.
- Before release, run the full exact-target build and `verifyReleaseJars`, inspect all public artifacts, and complete the target-by-target Prism smoke matrix documented in `docs/release-compatibility.md`.
- Keep implementation jars and generated compatibility classes out of published API namespaces. Do not weaken release checks to make an incompatible API pass.
