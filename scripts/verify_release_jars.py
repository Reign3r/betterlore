#!/usr/bin/env python3
"""Verify the collected Better Lore release jars are structurally deployable.

``collect_release_jars.py`` proves that a complete, deterministic set of
remapped/reobfuscated jars was selected.  This verifier is deliberately a
second step: it opens every collected archive and checks the metadata and
runtime-discovery resources that a mod loader will use after publication.
"""

from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import re
import sys
import tempfile
from typing import Any, Mapping
import zipfile

try:  # Python 3.11+ provides TOML parsing without an extra build dependency.
    import tomllib
except ModuleNotFoundError:  # pragma: no cover - covered by the actionable error below.
    tomllib = None  # type: ignore[assignment]


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

from verify_matrix import Artifact, ROOT, UNSUPPORTED, declared_artifacts, validate_matrix


RELEASE_DIRECTORY_NAME = "release"
MANIFEST_NAME = "manifest.json"
SUMMARY_NAME = "SUMMARY.txt"
MIXIN_CONFIGURATION = "better_lore.mixins.json"

SERVER_NETWORKING_SERVICE = "com.reign.betterlore.net.BetterLoreNetworkingPlatform"
CLIENT_NETWORKING_SERVICE = "com.reign.betterlore.client.net.BetterLoreClientNetworkingPlatform"
QUICKTEXT_PARSER_SERVICE = "com.reign.betterlore.lore.quicktext.QuickTextParserAdapter"
JEI_PLUGIN_CLASS = "com.reign.betterlore.compat.jei.BetterLoreJeiPlugin"
JEI_PLUGIN_CLASS_PATH = JEI_PLUGIN_CLASS.replace(".", "/") + ".class"

# These are an intentional part of the release contract.  Requiring exactly
# one implementation avoids ServiceLoader's first-provider-wins behaviour from
# changing with an accidentally bundled or stale service declaration.  Forge
# and NeoForge use the clean-room parser fallback, so only Fabric supplies the
# optional Placeholder API adapter.
EXPECTED_SERVICE_PROVIDERS: Mapping[str, Mapping[str, tuple[str, ...]]] = {
    "fabric": {
        SERVER_NETWORKING_SERVICE: (
            "com.reign.betterlore.net.fabric.FabricBetterLoreNetworkingPlatform",
        ),
        CLIENT_NETWORKING_SERVICE: (
            "com.reign.betterlore.client.net.fabric.FabricBetterLoreClientNetworkingPlatform",
        ),
        QUICKTEXT_PARSER_SERVICE: (
            "com.reign.betterlore.lore.quicktext.fabric.FabricPlaceholderApiQuickTextAdapter",
        ),
    },
    "forge": {
        SERVER_NETWORKING_SERVICE: (
            "com.reign.betterlore.net.forge.ForgeBetterLoreNetworkingPlatform",
        ),
        CLIENT_NETWORKING_SERVICE: (
            "com.reign.betterlore.client.net.forge.ForgeBetterLoreClientNetworkingPlatform",
        ),
    },
    "neoforge": {
        SERVER_NETWORKING_SERVICE: (
            "com.reign.betterlore.net.neoforge.NeoForgeBetterLoreNetworkingPlatform",
        ),
        CLIENT_NETWORKING_SERVICE: (
            "com.reign.betterlore.client.net.neoforge.NeoForgeBetterLoreClientNetworkingPlatform",
        ),
    },
}

_UNRESOLVED_TOKEN = re.compile(r"\$\{[^}\r\n]+\}")
_QUALIFIED_CLASS = re.compile(
    r"^[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)+$"
)
_SHA256 = re.compile(r"^[0-9a-f]{64}$")


class ReleaseJarVerificationError(RuntimeError):
    """Raised when a collected release directory is incomplete or malformed."""


@dataclass(frozen=True)
class VerifiedArtifact:
    loader: str
    minecraft: str
    file: str
    bytes: int
    sha256: str


def _archive_name(artifact: Artifact, mod_version: str) -> str:
    return f"better-lore-{artifact.loader}-{artifact.minecraft}-{mod_version}.jar"


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _is_mapping(value: object) -> bool:
    return isinstance(value, Mapping)


def _read_archive_text(
    archive: zipfile.ZipFile,
    name: str,
    errors: list[str],
    archive_label: str,
) -> str | None:
    try:
        return archive.read(name).decode("utf-8")
    except KeyError:
        errors.append(f"{archive_label}: missing {name}")
    except UnicodeDecodeError as error:
        errors.append(f"{archive_label}: {name} is not valid UTF-8 ({error})")
    except OSError as error:
        errors.append(f"{archive_label}: could not read {name} ({error})")
    return None


def _unresolved_token_errors(text: str, resource: str, errors: list[str], archive_label: str) -> None:
    tokens = sorted(set(_UNRESOLVED_TOKEN.findall(text)))
    if tokens:
        rendered = ", ".join(tokens[:3])
        if len(tokens) > 3:
            rendered += ", ..."
        errors.append(f"{archive_label}: {resource} contains unresolved Gradle token(s): {rendered}")


def _require_equal(
    value: object,
    expected: object,
    field: str,
    resource: str,
    errors: list[str],
    archive_label: str,
) -> None:
    if value != expected:
        errors.append(
            f"{archive_label}: {resource} {field} is {value!r}, expected {expected!r}"
        )


def _string_list(
    value: object,
    field: str,
    resource: str,
    errors: list[str],
    archive_label: str,
) -> list[str]:
    if not isinstance(value, list):
        errors.append(f"{archive_label}: {resource} {field} must be an array of strings")
        return []
    invalid = [entry for entry in value if not isinstance(entry, str) or not entry]
    if invalid:
        errors.append(f"{archive_label}: {resource} {field} must contain only non-empty strings")
    return [entry for entry in value if isinstance(entry, str) and entry]


def _validate_fabric_descriptor(
    text: str,
    artifact: Artifact,
    mod_id: str,
    mod_version: str,
    jei_available: bool,
    errors: list[str],
    archive_label: str,
) -> None:
    resource = "fabric.mod.json"
    try:
        descriptor = json.loads(text)
    except json.JSONDecodeError as error:
        errors.append(f"{archive_label}: {resource} is invalid JSON ({error})")
        return
    if not _is_mapping(descriptor):
        errors.append(f"{archive_label}: {resource} must be a JSON object")
        return

    _require_equal(descriptor.get("schemaVersion"), 1, "schemaVersion", resource, errors, archive_label)
    _require_equal(descriptor.get("id"), mod_id, "id", resource, errors, archive_label)
    _require_equal(str(descriptor.get("version")), mod_version, "version", resource, errors, archive_label)
    if not isinstance(descriptor.get("name"), str) or not descriptor["name"].strip():
        errors.append(f"{archive_label}: {resource} name must be a non-empty string")

    dependencies = descriptor.get("depends")
    if not _is_mapping(dependencies):
        errors.append(f"{archive_label}: {resource} depends must be an object")
    else:
        _require_equal(
            dependencies.get("minecraft"),
            artifact.minecraft,
            "depends.minecraft",
            resource,
            errors,
            archive_label,
        )

    mixins = _string_list(descriptor.get("mixins"), "mixins", resource, errors, archive_label)
    if MIXIN_CONFIGURATION not in mixins:
        errors.append(f"{archive_label}: {resource} does not declare {MIXIN_CONFIGURATION}")

    entrypoints = descriptor.get("entrypoints")
    if not _is_mapping(entrypoints):
        errors.append(f"{archive_label}: {resource} entrypoints must be an object")
        return
    jei_entrypoint = entrypoints.get("jei_mod_plugin")
    if jei_available:
        expected = [JEI_PLUGIN_CLASS]
        if jei_entrypoint != expected:
            errors.append(
                f"{archive_label}: {resource} jei_mod_plugin is {jei_entrypoint!r}, expected {expected!r}"
            )
    elif jei_entrypoint is not None:
        errors.append(
            f"{archive_label}: {resource} declares jei_mod_plugin although the profile disables JEI"
        )


def _toml_mixin_configs(
    descriptor: Mapping[str, Any],
    resource: str,
    errors: list[str],
    archive_label: str,
) -> list[str]:
    entries = descriptor.get("mixins")
    if not isinstance(entries, list):
        errors.append(f"{archive_label}: {resource} [[mixins]] table is missing or invalid")
        return []
    configs: list[str] = []
    for index, entry in enumerate(entries):
        if not _is_mapping(entry) or not isinstance(entry.get("config"), str) or not entry["config"].strip():
            errors.append(
                f"{archive_label}: {resource} mixins entry {index} must have a non-empty config string"
            )
            continue
        configs.append(entry["config"])
    return configs


def _validate_toml_descriptor(
    text: str,
    artifact: Artifact,
    mod_id: str,
    mod_version: str,
    jei_available: bool,
    errors: list[str],
    archive_label: str,
) -> None:
    resource = "META-INF/mods.toml" if artifact.loader == "forge" else "META-INF/neoforge.mods.toml"
    if tomllib is None:
        errors.append(
            f"{archive_label}: cannot parse {resource}; Python 3.11+ (tomllib) is required"
        )
        return
    try:
        descriptor = tomllib.loads(text)
    except tomllib.TOMLDecodeError as error:
        errors.append(f"{archive_label}: {resource} is invalid TOML ({error})")
        return
    if not _is_mapping(descriptor):
        errors.append(f"{archive_label}: {resource} must be a TOML table")
        return

    _require_equal(descriptor.get("modLoader"), "javafml", "modLoader", resource, errors, archive_label)
    if not isinstance(descriptor.get("loaderVersion"), str) or not descriptor["loaderVersion"].strip():
        errors.append(f"{archive_label}: {resource} loaderVersion must be a non-empty string")

    mods = descriptor.get("mods")
    if not isinstance(mods, list):
        errors.append(f"{archive_label}: {resource} [[mods]] table is missing or invalid")
    else:
        matching = [entry for entry in mods if _is_mapping(entry) and entry.get("modId") == mod_id]
        if len(matching) != 1:
            errors.append(
                f"{archive_label}: {resource} must contain exactly one [[mods]] entry for {mod_id!r}; "
                f"found {len(matching)}"
            )
        else:
            mod = matching[0]
            _require_equal(mod.get("version"), mod_version, "[[mods]].version", resource, errors, archive_label)
            if not isinstance(mod.get("displayName"), str) or not mod["displayName"].strip():
                errors.append(f"{archive_label}: {resource} [[mods]].displayName must be a non-empty string")

    mixins = _toml_mixin_configs(descriptor, resource, errors, archive_label)
    if MIXIN_CONFIGURATION not in mixins:
        errors.append(f"{archive_label}: {resource} does not declare {MIXIN_CONFIGURATION}")

    dependencies = descriptor.get("dependencies")
    if not _is_mapping(dependencies):
        errors.append(f"{archive_label}: {resource} dependencies table is missing or invalid")
        return
    dependency_entries = dependencies.get(mod_id)
    if _is_mapping(dependency_entries):
        dependency_entries = [dependency_entries]
    if not isinstance(dependency_entries, list):
        errors.append(f"{archive_label}: {resource} has no dependency table for {mod_id!r}")
        return
    minecraft_dependencies = [
        entry
        for entry in dependency_entries
        if _is_mapping(entry) and entry.get("modId") == "minecraft"
    ]
    expected_range = f"[{artifact.minecraft}]"
    if not minecraft_dependencies:
        errors.append(f"{archive_label}: {resource} is missing its required minecraft dependency")
    elif not any(entry.get("versionRange") == expected_range for entry in minecraft_dependencies):
        actual_ranges = ", ".join(repr(entry.get("versionRange")) for entry in minecraft_dependencies)
        errors.append(
            f"{archive_label}: {resource} minecraft dependency versionRange is {actual_ranges}, "
            f"expected {expected_range!r}"
        )

    jei_dependencies = [
        entry
        for entry in dependency_entries
        if _is_mapping(entry) and entry.get("modId") == "jei"
    ]
    if jei_available and not jei_dependencies:
        errors.append(f"{archive_label}: {resource} is missing its optional JEI dependency declaration")
    elif not jei_available and jei_dependencies:
        errors.append(
            f"{archive_label}: {resource} declares JEI although the profile disables JEI"
        )


def _mixin_class_name(package: str, entry: str) -> str:
    return entry if entry.startswith(package + ".") else f"{package}.{entry}"


def _validate_mixin_configuration(
    archive: zipfile.ZipFile,
    names: set[str],
    errors: list[str],
    archive_label: str,
) -> None:
    text = _read_archive_text(archive, MIXIN_CONFIGURATION, errors, archive_label)
    if text is None:
        return
    _unresolved_token_errors(text, MIXIN_CONFIGURATION, errors, archive_label)
    try:
        config = json.loads(text)
    except json.JSONDecodeError as error:
        errors.append(f"{archive_label}: {MIXIN_CONFIGURATION} is invalid JSON ({error})")
        return
    if not _is_mapping(config):
        errors.append(f"{archive_label}: {MIXIN_CONFIGURATION} must be a JSON object")
        return

    package = config.get("package")
    if not isinstance(package, str) or not _QUALIFIED_CLASS.fullmatch(package):
        errors.append(f"{archive_label}: {MIXIN_CONFIGURATION} package must be a valid Java package name")
        return

    declared: list[str] = []
    for field in ("mixins", "client", "server"):
        if field not in config:
            continue
        declared.extend(_string_list(config[field], field, MIXIN_CONFIGURATION, errors, archive_label))
    if not declared:
        errors.append(f"{archive_label}: {MIXIN_CONFIGURATION} declares no mixin classes")
        return

    for entry in declared:
        class_name = _mixin_class_name(package, entry)
        if not _QUALIFIED_CLASS.fullmatch(class_name):
            errors.append(
                f"{archive_label}: {MIXIN_CONFIGURATION} declares invalid mixin class {entry!r}"
            )
            continue
        class_path = class_name.replace(".", "/") + ".class"
        if class_path not in names:
            errors.append(
                f"{archive_label}: {MIXIN_CONFIGURATION} declares mixin class {class_name}, "
                f"but {class_path} is absent from the jar"
            )


def _parse_service_providers(
    text: str,
    resource: str,
    errors: list[str],
    archive_label: str,
) -> tuple[str, ...]:
    providers: list[str] = []
    for line_number, raw_line in enumerate(text.lstrip("\ufeff").splitlines(), start=1):
        provider = raw_line.split("#", 1)[0].strip()
        if not provider:
            continue
        if not _QUALIFIED_CLASS.fullmatch(provider):
            errors.append(
                f"{archive_label}: {resource}:{line_number} is not a valid Java provider class: {provider!r}"
            )
            continue
        providers.append(provider)
    if len(providers) != len(set(providers)):
        errors.append(f"{archive_label}: {resource} declares duplicate ServiceLoader providers")
    return tuple(providers)


def _validate_service_providers(
    archive: zipfile.ZipFile,
    names: set[str],
    artifact: Artifact,
    errors: list[str],
    archive_label: str,
) -> None:
    resources = sorted(name for name in names if name.startswith("META-INF/services/") and not name.endswith("/"))
    parsed: dict[str, tuple[str, ...]] = {}
    for resource in resources:
        service = resource.removeprefix("META-INF/services/")
        if not _QUALIFIED_CLASS.fullmatch(service):
            errors.append(f"{archive_label}: invalid ServiceLoader resource name {resource!r}")
            continue
        text = _read_archive_text(archive, resource, errors, archive_label)
        if text is None:
            continue
        providers = _parse_service_providers(text, resource, errors, archive_label)
        parsed[service] = providers
        for provider in providers:
            class_path = provider.replace(".", "/") + ".class"
            if class_path not in names:
                errors.append(
                    f"{archive_label}: {resource} declares {provider}, but {class_path} is absent from the jar"
                )

    for service, expected in EXPECTED_SERVICE_PROVIDERS[artifact.loader].items():
        actual = parsed.get(service)
        resource = f"META-INF/services/{service}"
        if actual is None:
            errors.append(
                f"{archive_label}: missing required ServiceLoader declaration {resource}"
            )
            continue
        if actual != expected:
            errors.append(
                f"{archive_label}: {resource} providers are {list(actual)!r}, expected {list(expected)!r}"
            )


def _validate_archive(
    path: Path,
    artifact: Artifact,
    mod_id: str,
    mod_version: str,
    jei_available: bool = True,
    resource_pack_format: int | None = None,
    resource_pack_minor: int = 0,
) -> list[str]:
    """Return every structural problem in one release archive."""

    errors: list[str] = []
    archive_label = f"{artifact.loader} {artifact.minecraft} ({path.name})"
    try:
        with zipfile.ZipFile(path) as archive:
            infos = archive.infolist()
            names = {info.filename for info in infos}
            duplicates = sorted(name for name, count in Counter(info.filename for info in infos).items() if count > 1)
            if duplicates:
                rendered = ", ".join(duplicates[:5])
                if len(duplicates) > 5:
                    rendered += ", ..."
                errors.append(f"{archive_label}: archive contains duplicate path(s): {rendered}")

            descriptor_name = (
                "fabric.mod.json"
                if artifact.loader == "fabric"
                else "META-INF/mods.toml"
                if artifact.loader == "forge"
                else "META-INF/neoforge.mods.toml"
            )
            descriptor = _read_archive_text(archive, descriptor_name, errors, archive_label)
            if descriptor is not None:
                _unresolved_token_errors(descriptor, descriptor_name, errors, archive_label)
                if artifact.loader == "fabric":
                    _validate_fabric_descriptor(
                        descriptor, artifact, mod_id, mod_version, jei_available, errors, archive_label
                    )
                else:
                    _validate_toml_descriptor(
                        descriptor, artifact, mod_id, mod_version, jei_available, errors, archive_label
                    )

            if jei_available and JEI_PLUGIN_CLASS_PATH not in names:
                errors.append(
                    f"{archive_label}: profile enables JEI but {JEI_PLUGIN_CLASS_PATH} is absent from the jar"
                )
            elif not jei_available and JEI_PLUGIN_CLASS_PATH in names:
                errors.append(
                    f"{archive_label}: profile disables JEI but {JEI_PLUGIN_CLASS_PATH} is present in the jar"
                )

            pack_metadata = _read_archive_text(archive, "pack.mcmeta", errors, archive_label)
            if pack_metadata is not None:
                _unresolved_token_errors(pack_metadata, "pack.mcmeta", errors, archive_label)
                try:
                    pack_document = json.loads(pack_metadata)
                except json.JSONDecodeError as error:
                    errors.append(f"{archive_label}: pack.mcmeta is invalid JSON ({error})")
                else:
                    pack = pack_document.get("pack") if _is_mapping(pack_document) else None
                    if not _is_mapping(pack):
                        errors.append(f"{archive_label}: pack.mcmeta pack must be a JSON object")
                    elif resource_pack_format is not None:
                        _require_equal(
                            pack.get("pack_format"),
                            resource_pack_format,
                            "pack.pack_format",
                            "pack.mcmeta",
                            errors,
                            archive_label,
                        )
                        expected_range = [resource_pack_format, resource_pack_minor]
                        _require_equal(
                            pack.get("min_format"),
                            expected_range,
                            "pack.min_format",
                            "pack.mcmeta",
                            errors,
                            archive_label,
                        )
                        _require_equal(
                            pack.get("max_format"),
                            expected_range,
                            "pack.max_format",
                            "pack.mcmeta",
                            errors,
                            archive_label,
                        )

            _validate_mixin_configuration(archive, names, errors, archive_label)
            _validate_service_providers(archive, names, artifact, errors, archive_label)
    except (OSError, zipfile.BadZipFile) as error:
        errors.append(f"{archive_label}: invalid jar archive ({error})")
    return errors


def _validate_manifest(
    path: Path,
    release_directory: Path,
    root: Path,
    artifacts: tuple[Artifact, ...],
    mod_version: str,
) -> list[str]:
    errors: list[str] = []
    if not path.is_file():
        return [f"missing {MANIFEST_NAME}"]
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        return [f"{MANIFEST_NAME} is invalid JSON ({error})"]
    if not _is_mapping(payload):
        return [f"{MANIFEST_NAME} must be a JSON object"]

    expected_by_key = {(artifact.loader, artifact.minecraft): artifact for artifact in artifacts}
    _require_equal(payload.get("schema_version"), 1, "schema_version", MANIFEST_NAME, errors, "release manifest")
    _require_equal(payload.get("artifact_count"), len(artifacts), "artifact_count", MANIFEST_NAME, errors, "release manifest")
    if not isinstance(payload.get("generated_at"), str) or not payload["generated_at"].strip():
        errors.append(f"release manifest: {MANIFEST_NAME} generated_at must be a non-empty string")

    records = payload.get("artifacts")
    if not isinstance(records, list):
        return errors + [f"release manifest: {MANIFEST_NAME} artifacts must be an array"]
    if len(records) != len(artifacts):
        errors.append(
            f"release manifest: {MANIFEST_NAME} contains {len(records)} artifact record(s), expected {len(artifacts)}"
        )

    seen: set[tuple[str, str]] = set()
    for index, record in enumerate(records):
        label = f"release manifest: {MANIFEST_NAME} artifacts[{index}]"
        if not _is_mapping(record):
            errors.append(f"{label} must be an object")
            continue
        loader = record.get("loader")
        minecraft = record.get("minecraft")
        if not isinstance(loader, str) or not isinstance(minecraft, str):
            errors.append(f"{label} loader and minecraft must be strings")
            continue
        key = (loader, minecraft)
        artifact = expected_by_key.get(key)
        if artifact is None:
            errors.append(f"{label} names unexpected artifact {loader} {minecraft}")
            continue
        if key in seen:
            errors.append(f"{label} duplicates artifact {loader} {minecraft}")
            continue
        seen.add(key)

        expected_file = _archive_name(artifact, mod_version)
        _require_equal(record.get("file"), expected_file, "file", label, errors, "release manifest")
        expected_source = f"{loader}/versions/{minecraft}/build/libs/{expected_file}"
        _require_equal(record.get("source"), expected_source, "source", label, errors, "release manifest")

        jar = release_directory / expected_file
        if not jar.is_file() or jar.is_symlink():
            continue
        actual_size = jar.stat().st_size
        if type(record.get("bytes")) is not int:
            errors.append(f"{label} bytes must be an integer")
        elif record["bytes"] != actual_size:
            errors.append(f"{label} bytes is {record['bytes']}, actual file size is {actual_size}")
        actual_hash = _sha256(jar)
        declared_hash = record.get("sha256")
        if not isinstance(declared_hash, str) or not _SHA256.fullmatch(declared_hash):
            errors.append(f"{label} sha256 must be a lowercase 64-character hexadecimal digest")
        elif declared_hash != actual_hash:
            errors.append(f"{label} sha256 is {declared_hash}, actual digest is {actual_hash}")

    missing = sorted(set(expected_by_key) - seen)
    if missing:
        errors.append(
            "release manifest: missing artifact record(s): "
            + ", ".join(f"{loader} {minecraft}" for loader, minecraft in missing)
        )
    return errors


def _validate_summary(path: Path, artifact_count: int) -> list[str]:
    if not path.is_file():
        return [f"missing {SUMMARY_NAME}"]
    try:
        summary = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as error:
        return [f"{SUMMARY_NAME} is not valid UTF-8 ({error})"]
    if f"Artifacts: {artifact_count}" not in summary:
        return [f"{SUMMARY_NAME} does not report the expected artifact count ({artifact_count})"]
    return []


def verify_release_jars(
    root: Path = ROOT,
    release_directory: Path | None = None,
) -> tuple[VerifiedArtifact, ...]:
    """Validate the exact collected release set and return verified records.

    ``release_directory`` is optional for CI staging.  A normal invocation
    always validates the collector's canonical ``build/release`` directory.
    """

    state = validate_matrix(root)
    if state.errors:
        raise ReleaseJarVerificationError("matrix validation failed:\n" + "\n".join(state.errors))

    mod_id = state.root_properties.get("mod.id", "").strip()
    mod_version = state.root_properties.get("mod.version", "").strip()
    if not mod_id:
        raise ReleaseJarVerificationError("gradle.properties: mod.id is required for release verification")
    if not mod_version or any(character in mod_version for character in "/\\"):
        raise ReleaseJarVerificationError("gradle.properties: mod.version is missing or unsafe for archive verification")

    artifacts = declared_artifacts()
    release = release_directory or root / "build" / RELEASE_DIRECTORY_NAME
    if not release.is_absolute():
        release = root / release
    # ``resolve()`` follows links, which would make a linked release directory
    # look legitimate by the time it is checked below.  Keep the lexical path
    # so the collector/verifier contract remains symlink-free.
    release = release.absolute()
    if release.is_symlink() or not release.is_dir():
        raise ReleaseJarVerificationError(
            f"release directory is missing or not a real directory: {release} (run collectReleaseJars first)"
        )

    expected_names = {_archive_name(artifact, mod_version) for artifact in artifacts}
    allowed_names = expected_names | {MANIFEST_NAME, SUMMARY_NAME}
    errors: list[str] = []
    try:
        children = list(release.iterdir())
    except OSError as error:
        raise ReleaseJarVerificationError(f"could not inspect release directory {release}: {error}") from error
    for child in children:
        if child.name not in allowed_names:
            errors.append(f"release directory contains unexpected entry: {child.name}")
        elif child.is_symlink():
            errors.append(f"release directory entry must not be a symlink: {child.name}")
        elif not child.is_file():
            errors.append(f"release directory entry must be a file: {child.name}")

    errors.extend(_validate_manifest(release / MANIFEST_NAME, release, root, artifacts, mod_version))
    errors.extend(_validate_summary(release / SUMMARY_NAME, len(artifacts)))

    verified: list[VerifiedArtifact] = []
    for artifact in artifacts:
        file_name = _archive_name(artifact, mod_version)
        path = release / file_name
        if not path.is_file():
            errors.append(f"missing release jar: {file_name}")
            continue
        if path.is_symlink():
            errors.append(f"release jar must not be a symlink: {file_name}")
            continue
        profile = state.profiles.get(artifact.minecraft, {})
        jei_version = profile.get(f"deps.jei_{artifact.loader}", profile.get("deps.jei"))
        jei_available = jei_version != UNSUPPORTED
        resource_pack_format = int(profile["minecraft.resource_pack_format"])
        resource_pack_minor = int(profile["minecraft.resource_pack_minor"])
        errors.extend(
            _validate_archive(
                path,
                artifact,
                mod_id,
                mod_version,
                jei_available,
                resource_pack_format,
                resource_pack_minor,
            )
        )
        verified.append(
            VerifiedArtifact(
                loader=artifact.loader,
                minecraft=artifact.minecraft,
                file=file_name,
                bytes=path.stat().st_size,
                sha256=_sha256(path),
            )
        )

    if errors:
        raise ReleaseJarVerificationError("release jar verification failed:\n" + "\n".join(errors))
    if len(verified) != len(artifacts):
        raise ReleaseJarVerificationError(
            f"release jar verification saw {len(verified)} artifacts, expected {len(artifacts)}"
        )
    return tuple(verified)


def _write_synthetic_fabric_jar(
    path: Path,
    *,
    unresolved: bool = False,
    omit_mixin: bool = False,
    include_jei: bool = True,
) -> None:
    """Build a tiny valid archive for the command's dependency-free smoke test."""

    artifact = Artifact("fabric", "test-version")
    descriptor_version = "${version}" if unresolved else "test-mod-version"
    descriptor = {
        "schemaVersion": 1,
        "id": "test_mod",
        "version": descriptor_version,
        "name": "Test Mod",
        "depends": {"minecraft": artifact.minecraft},
        "mixins": [MIXIN_CONFIGURATION],
        "entrypoints": {"jei_mod_plugin": [JEI_PLUGIN_CLASS]} if include_jei else {},
    }
    mixin_config = {
        "required": True,
        "package": "example.mixin",
        "mixins": ["ExampleMixin"],
    }
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("fabric.mod.json", json.dumps(descriptor))
        archive.writestr(MIXIN_CONFIGURATION, json.dumps(mixin_config))
        if not omit_mixin:
            archive.writestr("example/mixin/ExampleMixin.class", b"not-a-real-class")
        if include_jei:
            archive.writestr(JEI_PLUGIN_CLASS_PATH, b"not-a-real-class")
        for service, providers in EXPECTED_SERVICE_PROVIDERS["fabric"].items():
            archive.writestr(f"META-INF/services/{service}", "\n".join(providers) + "\n")
            for provider in providers:
                archive.writestr(provider.replace(".", "/") + ".class", b"not-a-real-class")


def _run_self_test() -> int:
    """Exercise success plus two high-value failure paths without Gradle."""

    with tempfile.TemporaryDirectory(prefix="better-lore-release-verifier-") as temporary:
        directory = Path(temporary)
        valid = directory / "valid.jar"
        _write_synthetic_fabric_jar(valid)
        valid_errors = _validate_archive(valid, Artifact("fabric", "test-version"), "test_mod", "test-mod-version")
        if valid_errors:
            print("SELF-TEST ERROR: valid synthetic jar was rejected:\n" + "\n".join(valid_errors), file=sys.stderr)
            return 1

        no_jei = directory / "no-jei.jar"
        _write_synthetic_fabric_jar(no_jei, include_jei=False)
        no_jei_errors = _validate_archive(
            no_jei,
            Artifact("fabric", "test-version"),
            "test_mod",
            "test-mod-version",
            jei_available=False,
        )
        if no_jei_errors:
            print("SELF-TEST ERROR: JEI-disabled synthetic jar was rejected:\n" + "\n".join(no_jei_errors), file=sys.stderr)
            return 1

        unresolved = directory / "unresolved.jar"
        _write_synthetic_fabric_jar(unresolved, unresolved=True)
        unresolved_errors = _validate_archive(
            unresolved, Artifact("fabric", "test-version"), "test_mod", "test-mod-version"
        )
        if not any("unresolved Gradle token" in error for error in unresolved_errors):
            print("SELF-TEST ERROR: unresolved descriptor token was not reported", file=sys.stderr)
            return 1

        missing_mixin = directory / "missing-mixin.jar"
        _write_synthetic_fabric_jar(missing_mixin, omit_mixin=True)
        mixin_errors = _validate_archive(
            missing_mixin, Artifact("fabric", "test-version"), "test_mod", "test-mod-version"
        )
        if not any("declares mixin class" in error and "absent from the jar" in error for error in mixin_errors):
            print("SELF-TEST ERROR: missing mixin class was not reported", file=sys.stderr)
            return 1

    print("Synthetic release-jar verifier checks passed.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--release-dir",
        type=Path,
        help="Optional directory to verify instead of the canonical build/release output.",
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="Run a dependency-free synthetic archive smoke test.",
    )
    args = parser.parse_args()
    if args.self_test:
        return _run_self_test()

    try:
        records = verify_release_jars(release_directory=args.release_dir)
    except ReleaseJarVerificationError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print(f"Verified {len(records)} structurally valid release jar(s) in {ROOT / 'build' / RELEASE_DIRECTORY_NAME}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
