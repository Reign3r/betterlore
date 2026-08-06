#!/usr/bin/env python3
"""Canonical Better Lore compatibility-artifact matrix.

The Gradle/Stonecutter matrix still compiles every loader/Minecraft pair.  This
module keeps binary implementation families separate from the smaller public-
artifact partition and is the single source of truth for collection,
verification, Prism deployment, and launch-smoke coverage.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import re

from verify_matrix import Artifact, declared_artifacts


ROOT = Path(__file__).resolve().parents[1]
COMPATIBILITY_RUNTIME = (
    ROOT
    / "common"
    / "src"
    / "main"
    / "java"
    / "com"
    / "reign"
    / "betterlore"
    / "compat"
    / "CompatibilityRuntime.java"
)


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

# Forge and NeoForge will expose two flat adapter jars each.  These public
# groups deliberately do not replace the narrower binary families above: the
# implementation-selection and byte-equality checks still operate at those
# established boundaries.
NEOFORGE_PUBLIC_GROUPS = (
    (
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
    ),
    ("1.21.9", "1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2", "26.2"),
)

FORGE_PUBLIC_GROUPS = (
    (
        "1.20.6",
        "1.21",
        "1.21.1",
        "1.21.3",
        "1.21.4",
        "1.21.5",
        "1.21.6",
        "1.21.7",
        "1.21.8",
    ),
    ("1.21.9", "1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2", "26.2"),
)

PUBLIC_GROUPS_BY_LOADER = {
    "neoforge": NEOFORGE_PUBLIC_GROUPS,
    "forge": FORGE_PUBLIC_GROUPS,
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
    strategy: str

    @property
    def anchor(self) -> str:
        return self.versions[0]

    @property
    def label(self) -> str:
        return version_label(self.versions)

    @property
    def maven_range(self) -> str:
        """Return a union containing only the Minecraft versions we test.

        A broad ``[first,last]`` interval would make the legacy Forge jar claim
        unsupported 1.21.2 and make the modern jars claim hypothetical releases
        between 1.21.11 and 26.1.  Maven's restriction-union syntax keeps the
        public file count small without overstating runtime compatibility.
        """

        return ",".join(f"[{version}]" for version in self.versions)

    @property
    def fabric_minecraft_dependency(self) -> str | list[str]:
        if len(self.versions) == 1:
            return self.versions[0]
        return list(self.versions)

    def file_name(self, mod_version: str) -> str:
        return f"better-lore-{self.loader}-{self.label}-{mod_version}.jar"


def published_artifacts() -> tuple[PublishedArtifact, ...]:
    """Return the five externally distributed artifacts."""

    fabric_versions = tuple(version for family in FABRIC_FAMILIES for version in family)
    return (
        PublishedArtifact("fabric", fabric_versions, "fabric_bundle"),
        *(
            PublishedArtifact("neoforge", versions, "flat_adapter")
            for versions in NEOFORGE_PUBLIC_GROUPS
        ),
        *(
            PublishedArtifact("forge", versions, "flat_adapter")
            for versions in FORGE_PUBLIC_GROUPS
        ),
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


def _family_id(family: tuple[str, ...]) -> str:
    return "mc" + version_label(family).replace(".", "_").replace("-", "_")


def _validate_runtime_family_mapping(errors: list[str]) -> None:
    """Keep Java runtime dispatch identical to this canonical Python matrix."""

    try:
        source = COMPATIBILITY_RUNTIME.read_text(encoding="utf-8")
    except OSError as error:
        errors.append(f"cannot read {COMPATIBILITY_RUNTIME}: {error}")
        return

    method_by_loader = {
        "fabric": "fabricFamily",
        "neoforge": "neoForgeFamily",
        "forge": "forgeFamily",
    }
    for loader, method_name in method_by_loader.items():
        method = re.search(
            rf"private static String {method_name}\(String version\) \{{(.*?)\n\t\}}",
            source,
            re.DOTALL,
        )
        if method is None:
            errors.append(f"CompatibilityRuntime is missing {method_name}(String)")
            continue

        actual: dict[str, str] = {}
        for case in re.finditer(
            r"case\s+(.+?)\s+->\s+\"([^\"]+)\";",
            method.group(1),
        ):
            for version in re.findall(r'\"([^\"]+)\"', case.group(1)):
                if version in actual:
                    errors.append(
                        f"CompatibilityRuntime {method_name} maps {version} more than once"
                    )
                actual[version] = case.group(2)

        expected = {
            version: _family_id(family)
            for family in FAMILIES_BY_LOADER[loader]
            for version in family
        }
        if actual != expected:
            missing = sorted(set(expected) - set(actual))
            extra = sorted(set(actual) - set(expected))
            wrong = sorted(
                version
                for version in set(actual) & set(expected)
                if actual[version] != expected[version]
            )
            errors.append(
                f"CompatibilityRuntime {method_name} differs from {loader} families "
                f"(missing={missing}, extra={extra}, wrong={wrong})"
            )


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
    if len(published) != 5:
        errors.append(f"expected 5 published artifacts, found {len(published)}")
    if len(set(published_targets)) != len(published_targets):
        errors.append("published artifact ranges overlap")
    if set(published_targets) != compiled:
        errors.append("published artifact ranges do not exactly cover the compile matrix")
    fabric = [artifact for artifact in published if artifact.loader == "fabric"]
    if len(fabric) != 1 or fabric[0].strategy != "fabric_bundle":
        errors.append("Fabric must publish exactly one nested bundle")
    for loader in ("neoforge", "forge"):
        loader_artifacts = [artifact for artifact in published if artifact.loader == loader]
        if len(loader_artifacts) != 2 or any(
            artifact.strategy != "flat_adapter" for artifact in loader_artifacts
        ):
            errors.append(f"{loader}: must publish exactly two flat adapter jars")
        expected_groups = PUBLIC_GROUPS_BY_LOADER[loader]
        actual_groups = tuple(artifact.versions for artifact in loader_artifacts)
        if actual_groups != expected_groups:
            errors.append(
                f"{loader}: published groups are {actual_groups!r}, expected {expected_groups!r}"
            )

    for artifact in published:
        expected_union = ",".join(f"[{version}]" for version in artifact.versions)
        if artifact.loader != "fabric" and artifact.maven_range != expected_union:
            errors.append(
                f"{artifact.loader} {artifact.label}: Maven range must be the exact-version "
                f"union {expected_union!r}, found {artifact.maven_range!r}"
            )

    _validate_runtime_family_mapping(errors)

    return tuple(errors)


if __name__ == "__main__":
    problems = validate_release_matrix()
    if problems:
        raise SystemExit("\n".join(f"ERROR: {problem}" for problem in problems))
    print("Release matrix is valid: 5 files cover 52 loader/version targets.")
