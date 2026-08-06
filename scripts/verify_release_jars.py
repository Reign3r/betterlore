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
from io import BytesIO
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

from release_matrix import (
    FABRIC_FAMILIES,
    FAMILIES_BY_LOADER,
    PublishedArtifact,
    fabric_inner_path,
    published_artifacts,
    validate_release_matrix,
    version_label,
)
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
_JEI_COMPATIBILITY_MIXIN_CLASS = (
    "com.reign.betterlore.mixin.compat.BetterLoreJeiPluginCompatibilityMixin"
)
_JEI_COMPATIBILITY_MIXIN_CLASS_PATH = (
    _JEI_COMPATIBILITY_MIXIN_CLASS.replace(".", "/") + ".class"
)
_BETTER_LORE_INTERNAL_PREFIX = "com/reign/betterlore/"
_JAVA_21_CLASSFILE_MAJOR = 65
_MIXIN_PLUGIN_CLASS = "com.reign.betterlore.compat.BetterLoreMixinPlugin"
_MIXIN_PLUGIN_CLASS_PATH = _MIXIN_PLUGIN_CLASS.replace(".", "/") + ".class"
_COMPATIBILITY_RUNTIME_CLASSES = frozenset(
    {
        "com/reign/betterlore/compat/CompatibilityRuntime.class",
        "com/reign/betterlore/compat/CompatibilityRuntime$Selection.class",
    }
)
_FML_BOOTSTRAP_CLASS = {
    "forge": "com/reign/betterlore/forge/BetterLoreForgeBootstrap.class",
    "neoforge": "com/reign/betterlore/neoforge/BetterLoreNeoForgeBootstrap.class",
}
_FML_IMPLEMENTATION_CLASS = {
    "forge": "forge/BetterLoreForgeMod.class",
    "neoforge": "neoforge/BetterLoreNeoForgeMod.class",
}
_RESOURCE_LOCATION_RETURN = "()Lnet/minecraft/resources/ResourceLocation;"
_IDENTIFIER_RETURN = "()Lnet/minecraft/resources/Identifier;"

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
_NETWORK_SERVICE_RESOURCES = frozenset(
    {
        f"META-INF/services/{SERVER_NETWORKING_SERVICE}",
        f"META-INF/services/{CLIENT_NETWORKING_SERVICE}",
    }
)

_UNRESOLVED_TOKEN = re.compile(r"\$\{[^}\r\n]+\}")
_QUALIFIED_CLASS = re.compile(
    r"^[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)+$"
)
_SHA256 = re.compile(r"^[0-9a-f]{64}$")
_FORBIDDEN_RUNTIME_ASSETS = frozenset(
    {
        "assets/better_lore/Banner.jpg",
        "assets/better_lore/Banner.png",
        "assets/better_lore/Banner_concept.png",
        "assets/better_lore/Banner_concept_result.jpg",
        "assets/better_lore/icon.png",
        "assets/better_lore/icon_2.png",
    }
)


class ReleaseJarVerificationError(RuntimeError):
    """Raised when a collected release directory is incomplete or malformed."""


@dataclass(frozen=True)
class VerifiedArtifact:
    loader: str
    minecraft_versions: tuple[str, ...]
    strategy: str
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
    minecraft_dependency: object | None = None,
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
            artifact.minecraft if minecraft_dependency is None else minecraft_dependency,
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
    minecraft_range: str | None = None,
    validate_jei_contract: bool = True,
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
            _require_equal(
                mod.get("logoFile"),
                "assets/better_lore/icon.jpg",
                "[[mods]].logoFile",
                resource,
                errors,
                archive_label,
            )
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
    expected_range = minecraft_range or f"[{artifact.minecraft}]"
    if not minecraft_dependencies:
        errors.append(f"{archive_label}: {resource} is missing its required minecraft dependency")
    elif not any(entry.get("versionRange") == expected_range for entry in minecraft_dependencies):
        actual_ranges = ", ".join(repr(entry.get("versionRange")) for entry in minecraft_dependencies)
        errors.append(
            f"{archive_label}: {resource} minecraft dependency versionRange is {actual_ranges}, "
            f"expected {expected_range!r}"
        )

    if validate_jei_contract:
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
    expected_providers: Mapping[str, tuple[str, ...]] | None = None,
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

    required = (
        EXPECTED_SERVICE_PROVIDERS[artifact.loader]
        if expected_providers is None
        else expected_providers
    )
    for service, expected in required.items():
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
    resource_pack_max_format: int | None = None,
    resource_pack_max_minor: int = 0,
    minecraft_dependency: object | None = None,
    minecraft_range: str | None = None,
    validate_jei_contract: bool = True,
    expected_service_providers: Mapping[str, tuple[str, ...]] | None = None,
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
            forbidden = sorted(names & _FORBIDDEN_RUNTIME_ASSETS)
            if forbidden:
                errors.append(
                    f"{archive_label}: archive contains non-runtime artwork: {', '.join(forbidden)}"
                )

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
                        descriptor,
                        artifact,
                        mod_id,
                        mod_version,
                        jei_available,
                        errors,
                        archive_label,
                        minecraft_dependency,
                    )
                else:
                    _validate_toml_descriptor(
                        descriptor,
                        artifact,
                        mod_id,
                        mod_version,
                        jei_available,
                        errors,
                        archive_label,
                        minecraft_range,
                        validate_jei_contract,
                    )

            if validate_jei_contract:
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
                        maximum_format = resource_pack_max_format or resource_pack_format
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
                            [maximum_format, resource_pack_max_minor],
                            "pack.max_format",
                            "pack.mcmeta",
                            errors,
                            archive_label,
                        )
                        if resource_pack_format < 65 <= maximum_format:
                            errors.append(
                                f"{archive_label}: pack.mcmeta crosses the format-65 "
                                "supported_formats compatibility boundary"
                            )
                        elif maximum_format < 65:
                            _require_equal(
                                pack.get("supported_formats"),
                                [resource_pack_format, maximum_format],
                                "pack.supported_formats",
                                "pack.mcmeta",
                                errors,
                                archive_label,
                            )
                        elif "supported_formats" in pack:
                            errors.append(
                                f"{archive_label}: pack.mcmeta pack.supported_formats "
                                "is forbidden starting with pack format 65"
                            )

            _validate_mixin_configuration(archive, names, errors, archive_label)
            _validate_service_providers(
                archive,
                names,
                artifact,
                errors,
                archive_label,
                expected_service_providers,
            )
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


def _jei_available(state, loader: str, minecraft: str) -> bool:
    profile = state.profiles[minecraft]
    version = profile.get(f"deps.jei_{loader}", profile.get("deps.jei"))
    return version != UNSUPPORTED


def _pack_bounds(state, versions: tuple[str, ...]) -> tuple[int, int, int, int]:
    first = state.profiles[versions[0]]
    last = state.profiles[versions[-1]]
    return (
        int(first["minecraft.resource_pack_format"]),
        int(first["minecraft.resource_pack_minor"]),
        int(last["minecraft.resource_pack_format"]),
        int(last["minecraft.resource_pack_minor"]),
    )


def _fabric_dependency_versions(value: object) -> tuple[str, ...]:
    if isinstance(value, str):
        return (value,)
    if isinstance(value, list) and all(isinstance(entry, str) for entry in value):
        return tuple(value)
    return ()


def _validate_fabric_bundle(
    path: Path,
    artifact: PublishedArtifact,
    state,
    mod_id: str,
    mod_version: str,
    temporary: Path,
) -> list[str]:
    errors: list[str] = []
    label = f"fabric bundle ({path.name})"
    try:
        with zipfile.ZipFile(path) as archive:
            infos = archive.infolist()
            names = {info.filename for info in infos}
            duplicates = sorted(
                name for name, count in Counter(info.filename for info in infos).items() if count > 1
            )
            if duplicates:
                errors.append(f"{label}: duplicate archive paths: {', '.join(duplicates)}")
            forbidden = sorted(names & _FORBIDDEN_RUNTIME_ASSETS)
            if forbidden:
                errors.append(f"{label}: contains non-runtime artwork: {', '.join(forbidden)}")
            descriptor_text = _read_archive_text(archive, "fabric.mod.json", errors, label)
            if descriptor_text is None:
                return errors
            try:
                descriptor = json.loads(descriptor_text)
            except json.JSONDecodeError as error:
                return errors + [f"{label}: fabric.mod.json is invalid JSON ({error})"]
            if not _is_mapping(descriptor):
                return errors + [f"{label}: fabric.mod.json must be an object"]
            _require_equal(descriptor.get("schemaVersion"), 1, "schemaVersion", "fabric.mod.json", errors, label)
            _require_equal(descriptor.get("id"), mod_id, "id", "fabric.mod.json", errors, label)
            _require_equal(str(descriptor.get("version")), mod_version, "version", "fabric.mod.json", errors, label)
            if "entrypoints" in descriptor or "mixins" in descriptor:
                errors.append(f"{label}: public carrier must not load Minecraft-linked code")
            dependencies = descriptor.get("depends")
            if not _is_mapping(dependencies):
                errors.append(f"{label}: depends must be an object")
            else:
                _require_equal(
                    dependencies.get("minecraft"),
                    list(artifact.versions),
                    "depends.minecraft",
                    "fabric.mod.json",
                    errors,
                    label,
                )
                if "better_lore_impl" not in dependencies:
                    errors.append(f"{label}: carrier does not require better_lore_impl")

            expected_paths = [fabric_inner_path(family) for family in FABRIC_FAMILIES]
            jars = descriptor.get("jars")
            actual_paths = []
            if isinstance(jars, list):
                actual_paths = [
                    entry.get("file")
                    for entry in jars
                    if _is_mapping(entry) and isinstance(entry.get("file"), str)
                ]
            _require_equal(actual_paths, expected_paths, "jars", "fabric.mod.json", errors, label)
            nested_names = sorted(
                name
                for name in names
                if name.startswith("META-INF/jars/") and name.endswith(".jar")
            )
            _require_equal(nested_names, sorted(expected_paths), "nested jars", "archive", errors, label)

            claimed_by_path: dict[str, tuple[str, ...]] = {}
            for family, nested_path in zip(FABRIC_FAMILIES, expected_paths):
                if nested_path not in names:
                    continue
                data = archive.read(nested_path)
                if len(data) > 1024 * 1024:
                    errors.append(f"{label}: {nested_path} exceeds the 1 MiB implementation budget")
                try:
                    with zipfile.ZipFile(BytesIO(data)) as nested:
                        nested_names = set(nested.namelist())
                        _validate_adapter_classfiles(
                            nested,
                            nested_names,
                            errors,
                            f"{label}: {nested_path}",
                        )
                        inner_descriptor = json.loads(nested.read("fabric.mod.json").decode("utf-8"))
                        manifest = nested.read("META-INF/MANIFEST.MF").decode("utf-8")
                except (KeyError, UnicodeDecodeError, json.JSONDecodeError, zipfile.BadZipFile) as error:
                    errors.append(f"{label}: invalid {nested_path} ({error})")
                    continue
                claimed = _fabric_dependency_versions(
                    inner_descriptor.get("depends", {}).get("minecraft")
                    if _is_mapping(inner_descriptor.get("depends"))
                    else None
                )
                claimed_by_path[nested_path] = claimed
                _require_equal(
                    claimed,
                    family,
                    "depends.minecraft",
                    f"{nested_path}!/fabric.mod.json",
                    errors,
                    label,
                )
                expected_namespace = "official" if family[0].startswith("26.") else "intermediary"
                namespace_match = re.search(
                    r"^Fabric-Mapping-Namespace:\s*(\S+)\s*$", manifest, re.MULTILINE
                )
                actual_namespace = namespace_match.group(1) if namespace_match else None
                _require_equal(
                    actual_namespace,
                    expected_namespace,
                    "Fabric-Mapping-Namespace",
                    f"{nested_path}!/META-INF/MANIFEST.MF",
                    errors,
                    label,
                )
                nested_file = temporary / f"fabric-{version_label(family)}.jar"
                nested_file.write_bytes(data)
                min_format, min_minor, max_format, max_minor = _pack_bounds(state, family)
                jei_values = {_jei_available(state, "fabric", version) for version in family}
                if len(jei_values) != 1:
                    errors.append(f"{label}: {nested_path} crosses a JEI availability boundary")
                    jei = True
                else:
                    jei = next(iter(jei_values))
                errors.extend(
                    _validate_archive(
                        nested_file,
                        Artifact("fabric", family[0]),
                        "better_lore_impl",
                        f"{mod_version}+mc.{version_label(family)}",
                        jei,
                        min_format,
                        min_minor,
                        max_format,
                        max_minor,
                        family[0] if len(family) == 1 else list(family),
                    )
                )

            for minecraft in artifact.versions:
                compatible = [
                    nested_path
                    for nested_path, claimed in claimed_by_path.items()
                    if minecraft in claimed
                ]
                if len(compatible) != 1:
                    errors.append(
                        f"{label}: {minecraft} matches {len(compatible)} nested candidates: {compatible}"
                    )
            unexpected_claims = sorted(
                {
                    version
                    for claimed in claimed_by_path.values()
                    for version in claimed
                    if version not in artifact.versions
                }
            )
            if unexpected_claims:
                errors.append(f"{label}: nested candidates claim unsupported versions {unexpected_claims}")
    except (OSError, zipfile.BadZipFile) as error:
        errors.append(f"{label}: invalid jar archive ({error})")
    if path.stat().st_size > 8 * 1024 * 1024:
        errors.append(f"{label}: bundle exceeds the 8 MiB storage budget")
    return errors


def _adapter_family_id(family: tuple[str, ...]) -> str:
    label = version_label(family).replace(".", "_").replace("-", "_")
    return "mc" + label


def _flat_adapter_families(
    artifact: PublishedArtifact,
    errors: list[str],
    archive_label: str,
) -> tuple[tuple[str, ...], ...]:
    families: list[tuple[str, ...]] = []
    for family in FAMILIES_BY_LOADER[artifact.loader]:
        selected = [version for version in family if version in artifact.versions]
        if not selected:
            continue
        if len(selected) != len(family):
            errors.append(
                f"{archive_label}: public adapter splits retained binary family {family!r}"
            )
            continue
        families.append(family)
    covered = tuple(version for family in families for version in family)
    if covered != artifact.versions:
        errors.append(
            f"{archive_label}: retained binary families cover {covered!r}, "
            f"expected {artifact.versions!r}"
        )
    return tuple(families)


def _classfile_layout(
    data: bytes,
) -> tuple[int, list[str | None], list[int], int]:
    """Return classfile major, UTF-8 constants, class-name indexes, and pool end."""

    if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("invalid Java classfile header")
    major = int.from_bytes(data[6:8], "big")
    constant_count = int.from_bytes(data[8:10], "big")
    utf8: list[str | None] = [None] * constant_count
    class_name_indexes: list[int] = []
    fixed_sizes = {
        3: 4,
        4: 4,
        5: 8,
        6: 8,
        7: 2,
        8: 2,
        9: 4,
        10: 4,
        11: 4,
        12: 4,
        15: 3,
        16: 2,
        17: 4,
        18: 4,
        19: 2,
        20: 2,
    }
    offset = 10
    index = 1
    while index < constant_count:
        if offset >= len(data):
            raise ValueError("truncated Java constant pool")
        tag = data[offset]
        offset += 1
        if tag == 1:
            if offset + 2 > len(data):
                raise ValueError("truncated Java UTF-8 constant")
            length = int.from_bytes(data[offset : offset + 2], "big")
            offset += 2
            if offset + length > len(data):
                raise ValueError("truncated Java UTF-8 payload")
            utf8[index] = data[offset : offset + length].decode("utf-8", errors="replace")
            offset += length
        else:
            size = fixed_sizes.get(tag)
            if size is None:
                raise ValueError(f"unsupported Java constant-pool tag {tag}")
            if offset + size > len(data):
                raise ValueError("truncated Java constant-pool entry")
            if tag == 7:
                class_name_indexes.append(
                    int.from_bytes(data[offset : offset + 2], "big")
                )
            offset += size
            if tag in (5, 6):
                index += 1
        index += 1
    return major, utf8, class_name_indexes, offset


def _classfile_structural_references(data: bytes) -> set[str]:
    """Return classes referenced by constants, descriptors, and signatures."""

    _, utf8, class_name_indexes, _ = _classfile_layout(data)
    references: set[str] = set()
    for name_index in class_name_indexes:
        if not 0 < name_index < len(utf8):
            raise ValueError("invalid Java class-name index")
        value = utf8[name_index]
        if value is None:
            raise ValueError("Java class name is not UTF-8")
        if value.startswith("["):
            references.update(re.findall(r"L([^;]+);", value))
        else:
            references.add(value)

    # Field/method descriptors, generic signatures, and annotation descriptors
    # normally live only in UTF-8 constants.  Including them catches the exact
    # failure mode where a type is relocated in executable bytecode but remains
    # at its old Better Lore name in a descriptor or signature.
    for value in utf8:
        if value is not None:
            references.update(re.findall(r"L([A-Za-z0-9_$/]+)(?=[;<])", value))
    return references


def _skip_classfile_member(data: bytes, offset: int) -> int:
    if offset + 8 > len(data):
        raise ValueError("truncated Java class member")
    attribute_count = int.from_bytes(data[offset + 6 : offset + 8], "big")
    offset += 8
    for _ in range(attribute_count):
        if offset + 6 > len(data):
            raise ValueError("truncated Java class attribute")
        length = int.from_bytes(data[offset + 2 : offset + 6], "big")
        offset += 6 + length
        if offset > len(data):
            raise ValueError("truncated Java class attribute payload")
    return offset


def _classfile_methods(data: bytes) -> tuple[int, set[tuple[str, str]]]:
    major, utf8, _, offset = _classfile_layout(data)
    if offset + 8 > len(data):
        raise ValueError("truncated Java class declaration")
    offset += 6  # access flags, this class, super class
    interface_count = int.from_bytes(data[offset : offset + 2], "big")
    offset += 2 + 2 * interface_count
    if offset + 2 > len(data):
        raise ValueError("truncated Java field table")
    field_count = int.from_bytes(data[offset : offset + 2], "big")
    offset += 2
    for _ in range(field_count):
        offset = _skip_classfile_member(data, offset)
    if offset + 2 > len(data):
        raise ValueError("truncated Java method table")
    method_count = int.from_bytes(data[offset : offset + 2], "big")
    offset += 2
    methods: set[tuple[str, str]] = set()
    for _ in range(method_count):
        if offset + 8 > len(data):
            raise ValueError("truncated Java method")
        name_index = int.from_bytes(data[offset + 2 : offset + 4], "big")
        descriptor_index = int.from_bytes(data[offset + 4 : offset + 6], "big")
        if not (0 < name_index < len(utf8)) or not (0 < descriptor_index < len(utf8)):
            raise ValueError("invalid Java method name or descriptor index")
        name = utf8[name_index]
        descriptor = utf8[descriptor_index]
        if name is None or descriptor is None:
            raise ValueError("Java method name or descriptor is not UTF-8")
        methods.add((name, descriptor))
        offset = _skip_classfile_member(data, offset)
    return major, methods


def _validate_better_lore_structural_references(
    archive: zipfile.ZipFile,
    names: set[str],
    errors: list[str],
    archive_label: str,
) -> None:
    """Reject dangling references between Better Lore classes in an adapter."""

    missing_by_source: list[tuple[str, tuple[str, ...]]] = []
    for class_path in sorted(name for name in names if name.endswith(".class")):
        try:
            references = _classfile_structural_references(archive.read(class_path))
        except (KeyError, ValueError):
            # The ordinary classfile validation emits the more direct parse
            # error, so avoid duplicating it here.
            continue
        missing = tuple(
            sorted(
                reference + ".class"
                for reference in references
                if reference.startswith(_BETTER_LORE_INTERNAL_PREFIX)
                and reference + ".class" not in names
            )
        )
        if missing:
            missing_by_source.append((class_path, missing))

    if missing_by_source:
        rendered = "; ".join(
            f"{source} -> {', '.join(missing)}"
            for source, missing in missing_by_source[:8]
        )
        errors.append(
            f"{archive_label}: dangling Better Lore structural class reference(s): "
            f"{rendered}"
        )


def _validate_adapter_classfiles(
    archive: zipfile.ZipFile,
    names: set[str],
    errors: list[str],
    archive_label: str,
) -> None:
    """Validate all adapter classfiles and their internal mod references."""

    _validate_better_lore_structural_references(
        archive, names, errors, archive_label
    )
    invalid_classfiles: list[str] = []
    too_new: list[tuple[str, int]] = []
    for class_path in sorted(name for name in names if name.endswith(".class")):
        try:
            major, _, _, _ = _classfile_layout(archive.read(class_path))
        except (KeyError, ValueError) as error:
            invalid_classfiles.append(f"{class_path} ({error})")
            continue
        if major > _JAVA_21_CLASSFILE_MAJOR:
            too_new.append((class_path, major))
    if invalid_classfiles:
        errors.append(
            f"{archive_label}: invalid classfile(s): "
            f"{', '.join(invalid_classfiles[:8])}"
        )
    if too_new:
        rendered = ", ".join(
            f"{class_path} (major {major})" for class_path, major in too_new[:8]
        )
        errors.append(
            f"{archive_label}: classfiles must target Java 21 (major <= "
            f"{_JAVA_21_CLASSFILE_MAJOR}): {rendered}"
        )


def _validate_universal_jei_mixin_hook(
    names: set[str],
    configured_mixin_classes: set[str],
    methods: set[tuple[str, str]],
    errors: list[str],
    archive_label: str,
) -> None:
    """Require the transformation hook that makes the dual JEI ABI safe."""

    required_methods = {
        ("getPluginUid", _RESOURCE_LOCATION_RETURN),
        ("getPluginUid", _IDENTIFIER_RETURN),
    }
    if not required_methods.issubset(methods):
        return
    if _JEI_COMPATIBILITY_MIXIN_CLASS_PATH not in names:
        errors.append(
            f"{archive_label}: universal dual-signature JEI plugin requires mixin class "
            f"{_JEI_COMPATIBILITY_MIXIN_CLASS_PATH}"
        )
    if _JEI_COMPATIBILITY_MIXIN_CLASS_PATH not in configured_mixin_classes:
        errors.append(
            f"{archive_label}: universal dual-signature JEI plugin requires configured "
            f"mixin hook {_JEI_COMPATIBILITY_MIXIN_CLASS}"
        )


def _generated_family_id(
    class_path: str,
    root_prefix: str,
    loader: str,
) -> str | None:
    if not class_path.startswith(root_prefix):
        return None
    relative = class_path[len(root_prefix) :]
    parts = relative.split("/")
    if len(parts) < 3 or parts[0] != loader or not parts[1]:
        return ""
    return parts[1]


def _validate_flat_adapter(
    path: Path,
    artifact: PublishedArtifact,
    state,
    mod_id: str,
    mod_version: str,
) -> list[str]:
    errors: list[str] = []
    label = f"{artifact.loader} flat adapter {artifact.label} ({path.name})"
    families = _flat_adapter_families(artifact, errors, label)
    expected_family_ids = {_adapter_family_id(family) for family in families}
    min_format, min_minor, max_format, max_minor = _pack_bounds(state, artifact.versions)
    jei_expected = any(
        _jei_available(state, artifact.loader, version) for version in artifact.versions
    )
    errors.extend(
        _validate_archive(
            path,
            Artifact(artifact.loader, artifact.anchor),
            mod_id,
            mod_version,
            jei_expected,
            min_format,
            min_minor,
            max_format,
            max_minor,
            minecraft_range=artifact.maven_range,
            validate_jei_contract=False,
            expected_service_providers={},
        )
    )

    try:
        with zipfile.ZipFile(path) as archive:
            names = set(archive.namelist())
            required_stable = {
                *_COMPATIBILITY_RUNTIME_CLASSES,
                _MIXIN_PLUGIN_CLASS_PATH,
                _FML_BOOTSTRAP_CLASS[artifact.loader],
            }
            missing_stable = sorted(required_stable - names)
            if missing_stable:
                errors.append(
                    f"{label}: missing stable adapter class(es): {', '.join(missing_stable)}"
                )
            _validate_adapter_classfiles(archive, names, errors, label)

            manifest_text = _read_archive_text(
                archive, "META-INF/MANIFEST.MF", errors, label
            )
            if manifest_text is not None:
                main_section = manifest_text.replace("\r\n", "\n").split("\n\n", 1)[0]
                if not re.search(
                    rf"^MixinConfigs:\s*{re.escape(MIXIN_CONFIGURATION)}\s*$",
                    main_section,
                    re.MULTILINE,
                ):
                    errors.append(
                        f"{label}: META-INF/MANIFEST.MF main section does not declare "
                        f"{MIXIN_CONFIGURATION}"
                    )

            present_network_services = sorted(names & _NETWORK_SERVICE_RESOURCES)
            if present_network_services:
                errors.append(
                    f"{label}: flat adapters must not contain networking ServiceLoader "
                    f"resources: {', '.join(present_network_services)}"
                )

            generated_root = "com/reign/betterlore/compat/generated/"
            loader_generated_root = generated_root + artifact.loader + "/"
            generated_classes = {
                name
                for name in names
                if name.startswith(generated_root) and name.endswith(".class")
            }
            generated_family_ids: set[str] = set()
            for class_path in sorted(generated_classes):
                family_id = _generated_family_id(
                    class_path, generated_root, artifact.loader
                )
                if family_id is None:
                    continue
                if not family_id:
                    errors.append(
                        f"{label}: generated class uses a foreign or malformed adapter path: "
                        f"{class_path}"
                    )
                    continue
                generated_family_ids.add(family_id)
            if generated_family_ids != expected_family_ids:
                errors.append(
                    f"{label}: generated implementation families are "
                    f"{sorted(generated_family_ids)}, expected {sorted(expected_family_ids)}"
                )

            implementation_suffix = _FML_IMPLEMENTATION_CLASS[artifact.loader]
            expected_implementations = {
                f"{loader_generated_root}{family_id}/{implementation_suffix}"
                for family_id in expected_family_ids
            }
            missing_implementations = sorted(expected_implementations - names)
            if missing_implementations:
                errors.append(
                    f"{label}: missing generated FML implementation(s): "
                    f"{', '.join(missing_implementations)}"
                )
            original_implementation = (
                f"com/reign/betterlore/{implementation_suffix}"
            )
            if original_implementation in names:
                errors.append(
                    f"{label}: unrelocated FML implementation remains at "
                    f"{original_implementation}"
                )

            mixin_text = _read_archive_text(
                archive, MIXIN_CONFIGURATION, errors, label
            )
            configured_mixin_classes: set[str] = set()
            configured_generated_mixins: set[str] = set()
            configured_family_ids: set[str] = set()
            if mixin_text is not None:
                try:
                    mixin_document = json.loads(mixin_text)
                except json.JSONDecodeError:
                    mixin_document = None
                if _is_mapping(mixin_document):
                    _require_equal(
                        mixin_document.get("plugin"),
                        _MIXIN_PLUGIN_CLASS,
                        "plugin",
                        MIXIN_CONFIGURATION,
                        errors,
                        label,
                    )
                    package = mixin_document.get("package")
                    declared_entries: list[str] = []
                    if isinstance(package, str):
                        for side in ("mixins", "client", "server"):
                            entries = mixin_document.get(side, [])
                            if not isinstance(entries, list):
                                continue
                            for entry in entries:
                                if not isinstance(entry, str):
                                    continue
                                class_name = _mixin_class_name(package, entry)
                                class_path = class_name.replace(".", "/") + ".class"
                                declared_entries.append(class_path)
                                configured_mixin_classes.add(class_path)
                                family_id = _generated_family_id(
                                    class_path,
                                    "com/reign/betterlore/mixin/generated/",
                                    artifact.loader,
                                )
                                if family_id is None:
                                    continue
                                configured_generated_mixins.add(class_path)
                                if not family_id:
                                    errors.append(
                                        f"{label}: mixin configuration uses a foreign or "
                                        f"malformed generated path: {class_path}"
                                    )
                                else:
                                    configured_family_ids.add(family_id)
                    if len(declared_entries) != len(set(declared_entries)):
                        errors.append(
                            f"{label}: {MIXIN_CONFIGURATION} declares duplicate mixin classes"
                        )

            generated_mixin_root = "com/reign/betterlore/mixin/generated/"
            generated_mixin_classes = {
                name
                for name in names
                if name.startswith(generated_mixin_root) and name.endswith(".class")
            }
            generated_mixin_family_ids: set[str] = set()
            for class_path in sorted(generated_mixin_classes):
                family_id = _generated_family_id(
                    class_path, generated_mixin_root, artifact.loader
                )
                if family_id is None:
                    continue
                if not family_id:
                    errors.append(
                        f"{label}: generated mixin uses a foreign or malformed path: {class_path}"
                    )
                else:
                    generated_mixin_family_ids.add(family_id)
            if generated_mixin_family_ids != expected_family_ids:
                errors.append(
                    f"{label}: generated mixin families are "
                    f"{sorted(generated_mixin_family_ids)}, expected "
                    f"{sorted(expected_family_ids)}"
                )
            if configured_family_ids != expected_family_ids:
                errors.append(
                    f"{label}: configured mixin families are "
                    f"{sorted(configured_family_ids)}, expected {sorted(expected_family_ids)}"
                )
            top_level_generated_mixins = {
                name
                for name in generated_mixin_classes
                if "$" not in name.rsplit("/", 1)[-1]
            }
            if top_level_generated_mixins != configured_generated_mixins:
                missing_from_config = sorted(
                    top_level_generated_mixins - configured_generated_mixins
                )
                missing_from_archive = sorted(
                    configured_generated_mixins - top_level_generated_mixins
                )
                if missing_from_config:
                    errors.append(
                        f"{label}: generated mixin class(es) absent from config: "
                        f"{', '.join(missing_from_config)}"
                    )
                if missing_from_archive:
                    errors.append(
                        f"{label}: configured generated mixin class(es) absent from archive: "
                        f"{', '.join(missing_from_archive)}"
                    )

            jei_paths = sorted(
                name for name in names if name.startswith(JEI_PLUGIN_CLASS_PATH[:-6])
                and name.endswith(".class")
            )
            if jei_expected and JEI_PLUGIN_CLASS_PATH not in names:
                errors.append(
                    f"{label}: at least one retained family supports JEI but "
                    f"{JEI_PLUGIN_CLASS_PATH} is absent"
                )
            elif not jei_expected and jei_paths:
                errors.append(
                    f"{label}: no retained family supports JEI but plugin class(es) are present: "
                    f"{', '.join(jei_paths)}"
                )

            if JEI_PLUGIN_CLASS_PATH in names:
                try:
                    _, methods = _classfile_methods(archive.read(JEI_PLUGIN_CLASS_PATH))
                except (KeyError, ValueError) as error:
                    if artifact.loader == "neoforge" and "1.21.9" in artifact.versions:
                        errors.append(
                            f"{label}: cannot inspect universal JEI plugin ({error})"
                        )
                else:
                    if artifact.loader == "neoforge" and "1.21.9" in artifact.versions:
                        for descriptor in (
                            _RESOURCE_LOCATION_RETURN,
                            _IDENTIFIER_RETURN,
                        ):
                            if ("getPluginUid", descriptor) not in methods:
                                errors.append(
                                    f"{label}: universal NeoForge JEI plugin lacks "
                                    f"getPluginUid{descriptor}"
                                )
                    _validate_universal_jei_mixin_hook(
                        names,
                        configured_mixin_classes,
                        methods,
                        errors,
                        label,
                    )
    except (OSError, zipfile.BadZipFile) as error:
        errors.append(f"{label}: invalid jar archive ({error})")

    if path.stat().st_size > 2 * 1024 * 1024:
        errors.append(f"{label}: flat adapter exceeds the 2 MiB storage budget")
    return errors


def _validate_compatibility_archive(
    path: Path,
    artifact: PublishedArtifact,
    state,
    mod_id: str,
    mod_version: str,
    temporary: Path,
) -> list[str]:
    if artifact.strategy == "fabric_bundle":
        return _validate_fabric_bundle(path, artifact, state, mod_id, mod_version, temporary)
    if artifact.strategy != "flat_adapter":
        return [
            f"{artifact.loader} {artifact.label} ({path.name}): unknown publication "
            f"strategy {artifact.strategy!r}"
        ]
    return _validate_flat_adapter(path, artifact, state, mod_id, mod_version)


def _validate_manifest_v2(
    path: Path,
    release: Path,
    root: Path,
    artifacts: tuple[PublishedArtifact, ...],
    mod_version: str,
) -> list[str]:
    errors: list[str] = []
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        return [f"{MANIFEST_NAME} is invalid ({error})"]
    if not _is_mapping(payload):
        return [f"{MANIFEST_NAME} must be an object"]
    _require_equal(payload.get("schema_version"), 2, "schema_version", MANIFEST_NAME, errors, "release manifest")
    _require_equal(payload.get("artifact_count"), len(artifacts), "artifact_count", MANIFEST_NAME, errors, "release manifest")
    _require_equal(payload.get("target_count"), len(declared_artifacts()), "target_count", MANIFEST_NAME, errors, "release manifest")

    records = payload.get("artifacts")
    if not isinstance(records, list):
        return errors + [f"{MANIFEST_NAME} artifacts must be an array"]
    expected_by_file = {artifact.file_name(mod_version): artifact for artifact in artifacts}
    seen_files: set[str] = set()
    for index, record in enumerate(records):
        label = f"{MANIFEST_NAME} artifacts[{index}]"
        if not _is_mapping(record):
            errors.append(f"{label} must be an object")
            continue
        file_name = record.get("file")
        artifact = expected_by_file.get(file_name) if isinstance(file_name, str) else None
        if artifact is None:
            errors.append(f"{label} names unexpected file {file_name!r}")
            continue
        if file_name in seen_files:
            errors.append(f"{label} duplicates {file_name}")
            continue
        seen_files.add(file_name)
        _require_equal(record.get("loader"), artifact.loader, "loader", label, errors, "release manifest")
        _require_equal(record.get("minecraft_versions"), list(artifact.versions), "minecraft_versions", label, errors, "release manifest")
        _require_equal(record.get("strategy"), artifact.strategy, "strategy", label, errors, "release manifest")
        jar = release / file_name
        if jar.is_file():
            _require_equal(record.get("bytes"), jar.stat().st_size, "bytes", label, errors, "release manifest")
            _require_equal(record.get("sha256"), _sha256(jar), "sha256", label, errors, "release manifest")
        proofs = record.get("verified_sources")
        if not isinstance(proofs, list) or len(proofs) != len(artifact.versions):
            errors.append(f"{label} verified_sources must contain one proof per supported version")
            continue
        for version, proof in zip(artifact.versions, proofs):
            if not _is_mapping(proof):
                errors.append(f"{label} has an invalid source proof")
                continue
            source = root / artifact.loader / "versions" / version / "build" / "libs" / _archive_name(
                Artifact(artifact.loader, version), mod_version
            )
            expected_path = source.relative_to(root).as_posix()
            _require_equal(proof.get("path"), expected_path, "path", label, errors, "source proof")
            if not source.is_file():
                errors.append(f"{label}: source proof file is missing: {expected_path}")
            elif source.is_symlink():
                errors.append(f"{label}: source proof file must not be a symlink: {expected_path}")
            else:
                _require_equal(proof.get("sha256"), _sha256(source), "sha256", label, errors, "source proof")

    missing_files = sorted(set(expected_by_file) - seen_files)
    if missing_files:
        errors.append("release manifest misses artifact(s): " + ", ".join(missing_files))

    targets = payload.get("targets")
    if not isinstance(targets, list):
        return errors + [f"{MANIFEST_NAME} targets must be an array"]
    actual_targets: dict[tuple[str, str], Mapping[str, Any]] = {}
    for record in targets:
        if not _is_mapping(record):
            errors.append("release manifest has a non-object target")
            continue
        key = (record.get("loader"), record.get("minecraft"))
        if not all(isinstance(value, str) for value in key):
            errors.append(f"release manifest has an invalid target key {key!r}")
            continue
        if key in actual_targets:
            errors.append(f"release manifest duplicates target {key[0]} {key[1]}")
        actual_targets[key] = record
    for compile_target in declared_artifacts():
        key = (compile_target.loader, compile_target.minecraft)
        record = actual_targets.get(key)
        if record is None:
            errors.append(f"release manifest misses target {key[0]} {key[1]}")
            continue
        artifact = next(
            candidate
            for candidate in artifacts
            if candidate.loader == key[0] and key[1] in candidate.versions
        )
        _require_equal(record.get("file"), artifact.file_name(mod_version), "file", "target", errors, "release manifest")
        expected_nested = None
        if key[0] == "fabric":
            family = next(family for family in FABRIC_FAMILIES if key[1] in family)
            expected_nested = fabric_inner_path(family)
        if expected_nested is None:
            if "nested_candidate" in record:
                errors.append(f"release manifest target {key[0]} {key[1]} unexpectedly names a nested candidate")
        else:
            _require_equal(record.get("nested_candidate"), expected_nested, "nested_candidate", "target", errors, "release manifest")
    unexpected_targets = sorted(set(actual_targets) - {(a.loader, a.minecraft) for a in declared_artifacts()})
    if unexpected_targets:
        errors.append(f"release manifest contains unsupported targets: {unexpected_targets}")
    return errors


def verify_release_jars(
    root: Path = ROOT,
    release_directory: Path | None = None,
) -> tuple[VerifiedArtifact, ...]:
    """Validate the declared compatibility release and all 52 target mappings."""

    state = validate_matrix(root)
    if state.errors:
        raise ReleaseJarVerificationError("matrix validation failed:\n" + "\n".join(state.errors))
    release_matrix_errors = validate_release_matrix()
    if release_matrix_errors:
        raise ReleaseJarVerificationError(
            "release matrix validation failed:\n" + "\n".join(release_matrix_errors)
        )

    mod_id = state.root_properties.get("mod.id", "").strip()
    mod_version = state.root_properties.get("mod.version", "").strip()
    if not mod_id:
        raise ReleaseJarVerificationError("gradle.properties: mod.id is required for release verification")
    if not mod_version or any(character in mod_version for character in "/\\"):
        raise ReleaseJarVerificationError("gradle.properties: mod.version is missing or unsafe for archive verification")

    artifacts = published_artifacts()
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

    expected_names = {artifact.file_name(mod_version) for artifact in artifacts}
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

    errors.extend(
        _validate_manifest_v2(release / MANIFEST_NAME, release, root, artifacts, mod_version)
    )
    errors.extend(_validate_summary(release / SUMMARY_NAME, len(artifacts)))

    verified: list[VerifiedArtifact] = []
    with tempfile.TemporaryDirectory(prefix="better-lore-compat-verifier-") as temporary_name:
        temporary = Path(temporary_name)
        for artifact in artifacts:
            file_name = artifact.file_name(mod_version)
            path = release / file_name
            if not path.is_file():
                errors.append(f"missing release jar: {file_name}")
                continue
            if path.is_symlink():
                errors.append(f"release jar must not be a symlink: {file_name}")
                continue
            errors.extend(
                _validate_compatibility_archive(
                    path, artifact, state, mod_id, mod_version, temporary
                )
            )
            verified.append(
                VerifiedArtifact(
                    loader=artifact.loader,
                    minecraft_versions=artifact.versions,
                    strategy=artifact.strategy,
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
        archive.writestr(
            "pack.mcmeta",
            json.dumps({"pack": {"description": "test", "pack_format": 1}}),
        )
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
