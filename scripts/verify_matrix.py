#!/usr/bin/env python3
"""Validate Better Lore's declared Stonecutter release matrix.

The matrix is intentionally declared here rather than inferred from currently
enabled Stonecutter branches.  A developer may temporarily enable a subset
while porting a version, but release validation must still cover every profile
that is meant to produce an artifact.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import re
import sys
from typing import Mapping


ROOT = Path(__file__).resolve().parents[1]

# Keep this list in the same order as the CI matrix and release documentation.
VERSIONS = (
    "26.2",
    "26.1.2",
    "26.1.1",
    "26.1",
    "1.21.11",
    "1.21.10",
    "1.21.9",
    "1.21.8",
    "1.21.7",
    "1.21.6",
    "1.21.5",
    "1.21.4",
    "1.21.3",
    "1.21.2",
    "1.21.1",
    "1.21",
    "1.20.6",
    "1.20.5",
)
LOADERS = ("fabric", "neoforge", "forge")
FORGE_EXCLUDED_VERSIONS = frozenset(("1.20.5", "1.21.2"))
UNSUPPORTED = "UNSUPPORTED"

OPTIONAL_INTEGRATION_KEYS = frozenset(
    (
        "deps.jei",
        "deps.jei_fabric",
        "deps.jei_neoforge",
        "deps.jei_forge",
        "deps.rei",
    )
)

# These values are consumed directly by the loader build scripts or their
# processed metadata.  Do not silently derive them from another coordinate:
# doing so has hidden profile mistakes in the past.
COMMON_PROFILE_KEYS = (
    "java.version",
    "deps.minecraft",
    "minecraft.resource_pack_format",
    "minecraft.resource_pack_minor",
    "deps.fabric_loader",
    "deps.fabric_api",
    "deps.placeholder_api",
    "placeholder_api_version",
    "deps.neoforge",
    "neoforge_loader_version",
    "deps.forge",
    "deps.jei",
    "deps.rei",
)
FORGE_PROFILE_KEYS = ("forge_loader_version",)
ENABLED_VERSION_PROPERTIES = {
    "common": "stonecutter_enabled_common_versions",
    "fabric": "stonecutter_enabled_fabric_versions",
    "neoforge": "stonecutter_enabled_neoforge_versions",
    "forge": "stonecutter_enabled_forge_versions",
}
_VERIFY_TOKEN = re.compile(r"VERIFY_[A-Za-z0-9_.+-]+")
_PROPERTY_WHITESPACE = " " + chr(9) + chr(12)
_SIMPLE_ESCAPES = {
    "t": chr(9),
    "n": chr(10),
    "r": chr(13),
    "f": chr(12),
}


@dataclass(frozen=True)
class Artifact:
    loader: str
    minecraft: str


@dataclass(frozen=True)
class MatrixState:
    root: Path
    root_properties: Mapping[str, str]
    profiles: Mapping[str, Mapping[str, str]]
    enabled_versions: Mapping[str, tuple[str, ...]]
    errors: tuple[str, ...]

    @property
    def artifacts(self) -> tuple[Artifact, ...]:
        return declared_artifacts()


def declared_artifacts() -> tuple[Artifact, ...]:
    """Return the canonical stable-release artifact matrix."""

    return tuple(
        Artifact(loader, version)
        for loader in LOADERS
        for version in VERSIONS
        if not (loader == "forge" and version in FORGE_EXCLUDED_VERSIONS)
    )


def _has_continuation(line: str) -> bool:
    backslashes = 0
    for character in reversed(line):
        if character != "\\":
            break
        backslashes += 1
    return backslashes % 2 == 1


def _logical_lines(text: str):
    """Yield Java-properties logical lines and their first physical line."""

    buffered = ""
    start_line = 0
    for line_number, physical in enumerate(text.splitlines(), start=1):
        if not buffered:
            buffered = physical
            start_line = line_number
        else:
            # Java properties ignore indentation after a continuation marker.
            buffered += physical.lstrip(_PROPERTY_WHITESPACE)

        if _has_continuation(buffered):
            buffered = buffered[:-1]
            continue

        yield buffered, start_line
        buffered = ""

    if buffered:
        # An unfinished continuation is invalid instead of being interpreted
        # differently by Gradle and this verifier.
        raise ValueError(f"line {start_line}: unterminated continuation")


def _unescape(value: str, *, source: Path, line_number: int) -> str:
    result: list[str] = []
    position = 0
    while position < len(value):
        character = value[position]
        if character != "\\":
            result.append(character)
            position += 1
            continue

        position += 1
        if position >= len(value):
            raise ValueError(f"{source}:{line_number}: dangling escape")

        escaped = value[position]
        if escaped == "u":
            digits = value[position + 1 : position + 5]
            if len(digits) != 4 or any(digit not in "0123456789abcdefABCDEF" for digit in digits):
                raise ValueError(f"{source}:{line_number}: invalid Unicode escape")
            result.append(chr(int(digits, 16)))
            position += 5
        elif escaped in _SIMPLE_ESCAPES:
            result.append(_SIMPLE_ESCAPES[escaped])
            position += 1
        else:
            # Java's Properties also uses a backslash to quote separators and
            # whitespace.  For any other character it simply removes it.
            result.append(escaped)
            position += 1

    return "".join(result)


def _split_property(line: str) -> tuple[str, str]:
    escaped = False
    separator = len(line)
    for index, character in enumerate(line):
        if escaped:
            escaped = False
            continue
        if character == "\\":
            escaped = True
            continue
        if character in "=:" + _PROPERTY_WHITESPACE:
            separator = index
            break

    key = line[:separator]
    value_start = separator
    if separator < len(line):
        if line[separator] in _PROPERTY_WHITESPACE:
            while value_start < len(line) and line[value_start] in _PROPERTY_WHITESPACE:
                value_start += 1
            if value_start < len(line) and line[value_start] in "=:":
                value_start += 1
        else:
            value_start += 1
        while value_start < len(line) and line[value_start] in _PROPERTY_WHITESPACE:
            value_start += 1

    return key, line[value_start:]


def load_properties(path: Path) -> dict[str, str]:
    """Load a Java ``.properties`` file and reject duplicate keys.

    Gradle itself uses Java properties semantics.  Parsing them here prevents a
    harmless comment containing ``VERIFY_`` from blocking a release while also
    avoiding a later duplicate key silently overriding an earlier coordinate.
    """

    values: dict[str, str] = {}
    duplicates: list[str] = []
    text = path.read_text(encoding="utf-8")
    for line, line_number in _logical_lines(text):
        stripped = line.lstrip(_PROPERTY_WHITESPACE)
        if not stripped or stripped.startswith(("#", "!")):
            continue
        raw_key, raw_value = _split_property(stripped)
        key = _unescape(raw_key, source=path, line_number=line_number)
        value = _unescape(raw_value, source=path, line_number=line_number)
        if not key:
            raise ValueError(f"{path}:{line_number}: empty property key")
        if key in values:
            duplicates.append(f"{path}:{line_number}: duplicate property '{key}'")
        values[key] = value

    if duplicates:
        raise ValueError("\n".join(duplicates))
    return values


def _require_profile_value(
    profile: Mapping[str, str],
    *,
    path: Path,
    key: str,
    errors: list[str],
) -> str | None:
    value = profile.get(key)
    if value is None or not value.strip():
        errors.append(f"{path}: missing required property '{key}'")
        return None
    return value.strip()


def _validate_profile(version: str, path: Path, profile: Mapping[str, str], errors: list[str]) -> None:
    required = list(COMMON_PROFILE_KEYS)
    if version not in FORGE_EXCLUDED_VERSIONS:
        required.extend(FORGE_PROFILE_KEYS)

    values = {
        key: _require_profile_value(profile, path=path, key=key, errors=errors)
        for key in required
    }

    minecraft = values.get("deps.minecraft")
    if minecraft is not None and minecraft != version:
        errors.append(
            f"{path}: deps.minecraft is '{minecraft}', expected profile version '{version}'"
        )

    java_version = values.get("java.version")
    if java_version is not None:
        try:
            if int(java_version) < 17:
                raise ValueError
        except ValueError:
            errors.append(f"{path}: java.version must be an integer release >= 17, got '{java_version}'")

    for key in ("minecraft.resource_pack_format", "minecraft.resource_pack_minor"):
        value = values.get(key)
        if value is not None:
            try:
                if int(value) < 0:
                    raise ValueError
            except ValueError:
                errors.append(f"{path}: {key} must be a non-negative integer, got '{value}'")

    for key, value in profile.items():
        stripped = value.strip()
        if _VERIFY_TOKEN.search(stripped):
            errors.append(f"{path}: unresolved value for '{key}': '{value}'")
        if stripped.upper() == UNSUPPORTED and stripped != UNSUPPORTED:
            errors.append(f"{path}: '{key}' must use the exact sentinel '{UNSUPPORTED}'")
        if stripped == UNSUPPORTED and not (
            key in OPTIONAL_INTEGRATION_KEYS
            or (key == "deps.forge" and version in FORGE_EXCLUDED_VERSIONS)
        ):
            # JEI/REI are optional integrations.  A profile may explicitly
            # disable one when no compatible API exists for that Minecraft
            # release; core loader and game dependencies may not do this.
            errors.append(f"{path}: '{key}' cannot be '{UNSUPPORTED}' for this target")

    forge_coordinate = values.get("deps.forge")
    if version in FORGE_EXCLUDED_VERSIONS:
        if forge_coordinate != UNSUPPORTED:
            errors.append(
                f"{path}: deps.forge must be '{UNSUPPORTED}' because Forge {version} is excluded"
            )
    elif forge_coordinate == UNSUPPORTED:
        errors.append(f"{path}: deps.forge is unsupported but Forge {version} is a declared target")


def _validate_enabled_versions(
    root: Path, root_properties: Mapping[str, str], errors: list[str]
) -> dict[str, tuple[str, ...]]:
    enabled: dict[str, tuple[str, ...]] = {}
    declared_versions = set(VERSIONS)

    for loader, property_name in ENABLED_VERSION_PROPERTIES.items():
        raw_value = root_properties.get(property_name)
        if raw_value is None:
            errors.append(f"{root / 'gradle.properties'}: missing '{property_name}'")
            enabled[loader] = ()
            continue

        raw_versions = raw_value.split(",")
        versions = tuple(part.strip() for part in raw_versions if part.strip())
        if raw_value.strip() and any(not part.strip() for part in raw_versions):
            errors.append(f"{root / 'gradle.properties'}: invalid empty entry in '{property_name}'")
        duplicates = sorted({version for version in versions if versions.count(version) > 1})
        if duplicates:
            errors.append(
                f"{root / 'gradle.properties'}: duplicate {loader} versions: {', '.join(duplicates)}"
            )
        unknown = sorted(set(versions) - declared_versions)
        if unknown:
            errors.append(
                f"{root / 'gradle.properties'}: {loader} enables undeclared versions: {', '.join(unknown)}"
            )
        if loader == "forge":
            incorrectly_enabled = sorted(FORGE_EXCLUDED_VERSIONS.intersection(versions))
            if incorrectly_enabled:
                errors.append(
                    "Forge is incorrectly enabled for: " + ", ".join(incorrectly_enabled)
                )
        enabled[loader] = versions

    common_versions = set(enabled.get("common", ()))
    missing_common_versions = sorted(
        {
            version
            for loader, versions in enabled.items()
            if loader != "common"
            for version in versions
            if version not in common_versions
        }
    )
    if missing_common_versions:
        errors.append(
            f"{root / 'gradle.properties'}: stonecutter_enabled_common_versions must include "
            f"every enabled loader version; missing: {', '.join(missing_common_versions)}"
        )

    return enabled


def _validate_sources(root: Path, errors: list[str]) -> None:
    for relative in ("common/src/main/java", "common/src/main/resources"):
        if not (root / relative).is_dir():
            errors.append(f"Missing common source directory: {relative}")

    for loader in LOADERS:
        for relative in (f"{loader}/src/main/java", f"{loader}/src/main/resources"):
            if not (root / relative).is_dir():
                errors.append(f"Missing loader source directory: {relative}")

    common_java = root / "common/src/main/java"
    if not common_java.is_dir():
        return
    for path in common_java.rglob("*.java"):
        text = path.read_text(encoding="utf-8", errors="ignore")
        for forbidden in ("net.fabricmc.", "net.minecraftforge.", "net.neoforged."):
            if forbidden in text:
                errors.append(f"Loader import leaked into common: {path.relative_to(root)} -> {forbidden}")


def validate_matrix(root: Path = ROOT) -> MatrixState:
    """Return a complete validation result without exiting the interpreter."""

    errors: list[str] = []
    root_properties: dict[str, str] = {}
    profiles: dict[str, Mapping[str, str]] = {}

    root_properties_path = root / "gradle.properties"
    try:
        root_properties = load_properties(root_properties_path)
    except (OSError, ValueError) as error:
        errors.append(str(error))

    enabled = _validate_enabled_versions(root, root_properties, errors)

    profile_root = root / "versions"
    if profile_root.is_dir():
        actual_profiles = {
            path.parent.name
            for path in profile_root.glob("*/gradle.properties")
            if path.parent.is_dir()
        }
        extras = sorted(actual_profiles - set(VERSIONS))
        if extras:
            errors.append(f"Undeclared version profiles: {', '.join(extras)}")
    else:
        errors.append(f"Missing profiles directory: {profile_root}")

    for version in VERSIONS:
        profile_path = profile_root / version / "gradle.properties"
        if not profile_path.is_file():
            errors.append(f"Missing profile: {profile_path}")
            continue
        try:
            profile = load_properties(profile_path)
        except (OSError, ValueError) as error:
            errors.append(str(error))
            continue
        profiles[version] = profile
        _validate_profile(version, profile_path, profile, errors)

    _validate_sources(root, errors)

    return MatrixState(
        root=root,
        root_properties=root_properties,
        profiles=profiles,
        enabled_versions=enabled,
        errors=tuple(errors),
    )


def main() -> int:
    state = validate_matrix()
    if state.errors:
        print("\n".join(f"ERROR: {error}" for error in state.errors))
        return 1

    enabled_count = sum(
        len(versions)
        for loader, versions in state.enabled_versions.items()
        if loader != "common"
    )
    disabled_integrations = [
        f"{version}:{key.removeprefix('deps.')}"
        for version, profile in state.profiles.items()
        for key in sorted(OPTIONAL_INTEGRATION_KEYS)
        if profile.get(key) == UNSUPPORTED
    ]

    print(f"Declared matrix is valid: {len(state.artifacts)} loader artifacts.")
    print(f"Currently enabled loader targets: {enabled_count}.")
    if disabled_integrations:
        print("Explicitly disabled optional integrations: " + ", ".join(disabled_integrations))
    return 0


if __name__ == "__main__":
    sys.exit(main())
