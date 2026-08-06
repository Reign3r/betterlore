#!/usr/bin/env python3
"""Canonical Better Lore compatibility-artifact matrix.

The Gradle/Stonecutter matrix still compiles every loader/Minecraft pair.  This
module describes which byte-identical outputs may be packaged together and is
the single source of truth for collection, verification, Prism deployment, and
launch-smoke coverage.
"""

from __future__ import annotations

from dataclasses import dataclass

from verify_matrix import Artifact, declared_artifacts


MINECRAFT_VERSIONS = (
    "1.20.5",
    "1.20.6",
    "1.21",
    "1.21.1",
    "1.21.2",
    "1.21.3",
    "1.21.4",
    "1.21.5",
    "1.21.6",
    "1.21.7",
    "1.21.8",
    "1.21.9",
    "1.21.10",
    "1.21.11",
    "26.1",
    "26.1.1",
    "26.1.2",
    "26.2",
)

FABRIC_FAMILIES = (
    ("1.20.5",),
    ("1.20.6",),
    ("1.21", "1.21.1"),
    ("1.21.2", "1.21.3"),
    ("1.21.4",),
    ("1.21.5",),
    ("1.21.6",),
    ("1.21.7", "1.21.8"),
    ("1.21.9", "1.21.10"),
    ("1.21.11",),
    ("26.1", "26.1.1", "26.1.2"),
    ("26.2",),
)

NEOFORGE_FAMILIES = (
    ("1.20.5",),
    ("1.20.6",),
    ("1.21", "1.21.1"),
    ("1.21.2", "1.21.3"),
    ("1.21.4",),
    ("1.21.5",),
    ("1.21.6",),
    ("1.21.7",),
    ("1.21.8",),
    ("1.21.9", "1.21.10"),
    ("1.21.11",),
    ("26.1", "26.1.1", "26.1.2"),
    ("26.2",),
)

FORGE_FAMILIES = (
    ("1.20.6",),
    ("1.21", "1.21.1"),
    ("1.21.3",),
    ("1.21.4",),
    ("1.21.5",),
    ("1.21.6", "1.21.7", "1.21.8"),
    ("1.21.9", "1.21.10"),
    ("1.21.11",),
    ("26.1", "26.1.1", "26.1.2"),
    ("26.2",),
)

FAMILIES_BY_LOADER = {
    "fabric": FABRIC_FAMILIES,
    "neoforge": NEOFORGE_FAMILIES,
    "forge": FORGE_FAMILIES,
}


def version_label(versions: tuple[str, ...]) -> str:
    """Return a readable, filename-safe label for a compatibility range."""

    if not versions:
        raise ValueError("a compatibility artifact must support at least one version")
    return versions[0] if len(versions) == 1 else f"{versions[0]}-to-{versions[-1]}"


@dataclass(frozen=True)
class PublishedArtifact:
    loader: str
    versions: tuple[str, ...]
    strategy: str = "range"

    @property
    def anchor(self) -> str:
        return self.versions[0]

    @property
    def label(self) -> str:
        return version_label(self.versions)

    @property
    def maven_range(self) -> str:
        if len(self.versions) == 1:
            return f"[{self.versions[0]}]"
        return f"[{self.versions[0]},{self.versions[-1]}]"

    @property
    def fabric_minecraft_dependency(self) -> str | list[str]:
        if len(self.versions) == 1:
            return self.versions[0]
        return list(self.versions)

    def file_name(self, mod_version: str) -> str:
        return f"better-lore-{self.loader}-{self.label}-{mod_version}.jar"


def published_artifacts() -> tuple[PublishedArtifact, ...]:
    """Return the 24 externally distributed artifacts."""

    fabric_versions = tuple(version for family in FABRIC_FAMILIES for version in family)
    return (
        PublishedArtifact("fabric", fabric_versions, "fabric_bundle"),
        *(PublishedArtifact("neoforge", family) for family in NEOFORGE_FAMILIES),
        *(PublishedArtifact("forge", family) for family in FORGE_FAMILIES),
    )


def artifact_for_runtime(loader: str, minecraft: str) -> PublishedArtifact:
    matches = [
        artifact
        for artifact in published_artifacts()
        if artifact.loader == loader and minecraft in artifact.versions
    ]
    if len(matches) != 1:
        raise KeyError(
            f"expected one published artifact for {loader} {minecraft}, found {len(matches)}"
        )
    return matches[0]


def family_for_runtime(loader: str, minecraft: str) -> tuple[str, ...]:
    matches = [family for family in FAMILIES_BY_LOADER[loader] if minecraft in family]
    if len(matches) != 1:
        raise KeyError(
            f"expected one binary family for {loader} {minecraft}, found {len(matches)}"
        )
    return matches[0]


def fabric_inner_path(family: tuple[str, ...]) -> str:
    return f"META-INF/jars/better-lore-impl-{version_label(family)}.jar"


def validate_release_matrix() -> tuple[str, ...]:
    """Return every partition/order error in the declared release matrix."""

    errors: list[str] = []
    order = {version: index for index, version in enumerate(MINECRAFT_VERSIONS)}
    compiled = {(artifact.loader, artifact.minecraft) for artifact in declared_artifacts()}

    family_targets: list[tuple[str, str]] = []
    for loader, families in FAMILIES_BY_LOADER.items():
        for family in families:
            if not family:
                errors.append(f"{loader}: empty binary family")
                continue
            if len(set(family)) != len(family):
                errors.append(f"{loader} {family}: duplicate version")
            unknown = [version for version in family if version not in order]
            if unknown:
                errors.append(f"{loader} {family}: unknown version(s) {unknown}")
                continue
            positions = [order[version] for version in family]
            if positions != sorted(positions):
                errors.append(f"{loader} {family}: versions are not chronological")
            supported_for_loader = [
                version
                for version in MINECRAFT_VERSIONS
                if (loader, version) in compiled
            ]
            expected_slice = supported_for_loader[
                supported_for_loader.index(family[0]) : supported_for_loader.index(family[-1]) + 1
            ]
            if list(family) != expected_slice:
                errors.append(f"{loader} {family}: family is not contiguous")
            family_targets.extend((loader, version) for version in family)

    duplicates = sorted({target for target in family_targets if family_targets.count(target) > 1})
    if duplicates:
        errors.append("binary families overlap: " + ", ".join(f"{a} {v}" for a, v in duplicates))
    missing = sorted(compiled - set(family_targets))
    extra = sorted(set(family_targets) - compiled)
    if missing:
        errors.append("binary families miss: " + ", ".join(f"{a} {v}" for a, v in missing))
    if extra:
        errors.append("binary families include unsupported: " + ", ".join(f"{a} {v}" for a, v in extra))

    published = published_artifacts()
    published_targets = [
        (artifact.loader, version)
        for artifact in published
        for version in artifact.versions
    ]
    if len(published) != 24:
        errors.append(f"expected 24 published artifacts, found {len(published)}")
    if len(set(published_targets)) != len(published_targets):
        errors.append("published artifact ranges overlap")
    if set(published_targets) != compiled:
        errors.append("published artifact ranges do not exactly cover the compile matrix")
    fabric = [artifact for artifact in published if artifact.loader == "fabric"]
    if len(fabric) != 1 or fabric[0].strategy != "fabric_bundle":
        errors.append("Fabric must publish exactly one nested bundle")

    return tuple(errors)


if __name__ == "__main__":
    problems = validate_release_matrix()
    if problems:
        raise SystemExit("\n".join(f"ERROR: {problem}" for problem in problems))
    print("Release matrix is valid: 24 files cover 52 loader/version targets.")
