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
_SERVER_API_CLASS_PREFIX = "com/reign/betterlore/api/server/"
_SERVER_API_GENERATED_PREFIXES = (
    "com/reign/betterlore/compat/generated/",
    "com/reign/betterlore/mixin/generated/",
)
_SERVER_API_CLASS = _SERVER_API_CLASS_PREFIX + "BetterLoreServerApi.class"
_SERVER_API_FACADE_CLASS = _SERVER_API_CLASS_PREFIX + "BetterLoreServerApis.class"
_SERVER_API_HOLDER_CLASS = _SERVER_API_CLASS_PREFIX + "BetterLoreServerApis$Holder.class"
_SERVER_API_IMPLEMENTATION_CLASS = (
    "com/reign/betterlore/internal/serverapi/BetterLoreServerApiImpl.class"
)
_SERVER_API_VALIDATOR_CLASS = (
    "com/reign/betterlore/internal/serverapi/AnvilEditorDraftValidator.class"
)
_SERVER_API_IMPLEMENTATION_INTERNAL_NAME = _SERVER_API_IMPLEMENTATION_CLASS[:-6]
_SERVER_API_VALIDATOR_INTERNAL_NAME = _SERVER_API_VALIDATOR_CLASS[:-6]
_SERVER_API_SUBMISSION_INTERNAL_NAME = (
    "com/reign/betterlore/internal/serverapi/AnvilEditorDraftSubmission"
)
_SERVER_API_DRAFT_APPLIER_SUFFIX = "AnvilEditorDraftSubmission$DraftApplier"
_ANVIL_LORE_MENU_BRIDGE_INTERNAL_NAME = (
    "com/reign/betterlore/access/AnvilLoreMenuBridge"
)
_SERVER_DRAFT_APPLY_DESCRIPTOR = "(IZLjava/lang/String;ZLjava/lang/String;)Z"
_SERVER_API_VALIDATOR_METHOD = (
    "validate",
    "(Lcom/reign/betterlore/api/server/AnvilEditorDraft;)"
    "Lcom/reign/betterlore/api/server/AnvilEditorDraftValidation;",
)
_SERVER_API_REQUIRED_CLASSES = frozenset(
    {
        _SERVER_API_CLASS,
        _SERVER_API_FACADE_CLASS,
        _SERVER_API_CLASS_PREFIX + "AnvilEditorDraft.class",
        _SERVER_API_CLASS_PREFIX + "AnvilEditorDraftValidation.class",
        _SERVER_API_CLASS_PREFIX + "AnvilEditorDraftValidation$Status.class",
        _SERVER_API_CLASS_PREFIX + "AnvilEditorDraftResult.class",
        _SERVER_API_CLASS_PREFIX + "AnvilEditorDraftResult$Status.class",
        _SERVER_API_CLASS_PREFIX + "AnvilEditorSession.class",
    }
)
_SERVER_API_REQUIRED_METHODS: Mapping[str, frozenset[tuple[str, str]]] = {
    _SERVER_API_CLASS: frozenset(
        {
            ("apiVersion", "()I"),
            (
                "validateDraft",
                "(Lcom/reign/betterlore/api/server/AnvilEditorDraft;)"
                "Lcom/reign/betterlore/api/server/AnvilEditorDraftValidation;",
            ),
        }
    ),
    _SERVER_API_FACADE_CLASS: frozenset(
        {
            (
                "get",
                "()Lcom/reign/betterlore/api/server/BetterLoreServerApi;",
            ),
        }
    ),
    _SERVER_API_CLASS_PREFIX + "AnvilEditorDraft.class": frozenset(
        {
            ("<init>", "(IIZLjava/lang/String;ZLjava/lang/String;)V"),
            ("containerId", "()I"),
            ("sessionId", "()I"),
            ("nameEdited", "()Z"),
            ("rawNameMarkup", "()Ljava/lang/String;"),
            ("loreEdited", "()Z"),
            ("rawLoreMarkup", "()Ljava/lang/String;"),
            ("hasChanges", "()Z"),
        }
    ),
    _SERVER_API_CLASS_PREFIX + "AnvilEditorDraftValidation.class": frozenset(
        {
            (
                "<init>",
                "(Lcom/reign/betterlore/api/server/AnvilEditorDraftValidation$Status;"
                "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
                "Ljava/lang/String;)V",
            ),
            (
                "status",
                "()Lcom/reign/betterlore/api/server/AnvilEditorDraftValidation$Status;",
            ),
            ("nameErrorMessage", "()Ljava/lang/String;"),
            ("loreErrorMessage", "()Ljava/lang/String;"),
            ("normalizedNameMarkup", "()Ljava/lang/String;"),
            ("normalizedLoreMarkup", "()Ljava/lang/String;"),
            ("valid", "()Z"),
            ("message", "()Ljava/lang/String;"),
        }
    ),
    _SERVER_API_CLASS_PREFIX + "AnvilEditorDraftValidation$Status.class": frozenset(
        {
            (
                "values",
                "()[Lcom/reign/betterlore/api/server/AnvilEditorDraftValidation$Status;",
            ),
            (
                "valueOf",
                "(Ljava/lang/String;)"
                "Lcom/reign/betterlore/api/server/AnvilEditorDraftValidation$Status;",
            ),
        }
    ),
    _SERVER_API_CLASS_PREFIX + "AnvilEditorDraftResult.class": frozenset(
        {
            (
                "<init>",
                "(Lcom/reign/betterlore/api/server/AnvilEditorDraftResult$Status;"
                "Ljava/lang/String;)V",
            ),
            (
                "status",
                "()Lcom/reign/betterlore/api/server/AnvilEditorDraftResult$Status;",
            ),
            ("message", "()Ljava/lang/String;"),
            ("applied", "()Z"),
        }
    ),
    _SERVER_API_CLASS_PREFIX + "AnvilEditorDraftResult$Status.class": frozenset(
        {
            (
                "values",
                "()[Lcom/reign/betterlore/api/server/AnvilEditorDraftResult$Status;",
            ),
            (
                "valueOf",
                "(Ljava/lang/String;)"
                "Lcom/reign/betterlore/api/server/AnvilEditorDraftResult$Status;",
            ),
        }
    ),
    _SERVER_API_CLASS_PREFIX + "AnvilEditorSession.class": frozenset(
        {
            ("<init>", "(IILjava/lang/String;Ljava/lang/String;I)V"),
            ("containerId", "()I"),
            ("sessionId", "()I"),
            ("rawNameMarkup", "()Ljava/lang/String;"),
            ("rawLoreMarkup", "()Ljava/lang/String;"),
            ("loreEditLevelCost", "()I"),
        }
    ),
}
_SERVER_API_PLAYER_METHODS = (
    ("hasNativeEditor", "", "Z"),
    ("currentAnvilSession", "", "Ljava/util/Optional;"),
    (
        "submitDraft",
        "Lcom/reign/betterlore/api/server/AnvilEditorDraft;",
        "Lcom/reign/betterlore/api/server/AnvilEditorDraftResult;",
    ),
)
_SERVER_API_RECORD_CLASSES = frozenset(
    {
        _SERVER_API_CLASS_PREFIX + "AnvilEditorDraft.class",
        _SERVER_API_CLASS_PREFIX + "AnvilEditorDraftValidation.class",
        _SERVER_API_CLASS_PREFIX + "AnvilEditorDraftResult.class",
        _SERVER_API_CLASS_PREFIX + "AnvilEditorSession.class",
    }
)
_SERVER_API_ENUM_CONSTANTS: Mapping[str, tuple[str, ...]] = {
    _SERVER_API_CLASS_PREFIX + "AnvilEditorDraftValidation$Status.class": (
        "VALID",
        "MISSING_DRAFT",
        "NO_CHANGES",
        "INVALID_NAME",
        "INVALID_LORE",
        "INVALID_NAME_AND_LORE",
    ),
    _SERVER_API_CLASS_PREFIX + "AnvilEditorDraftResult$Status.class": (
        "APPLIED",
        "NO_CHANGES",
        "INVALID_DRAFT",
        "NO_ACTIVE_ANVIL",
        "WRONG_CONTAINER",
        "STALE_SESSION",
        "EMPTY_INPUT",
    ),
}

_ACC_PUBLIC = 0x0001
_ACC_PRIVATE = 0x0002
_ACC_PROTECTED = 0x0004
_ACC_STATIC = 0x0008
_ACC_FINAL = 0x0010
_ACC_INTERFACE = 0x0200
_ACC_ABSTRACT = 0x0400
_ACC_ANNOTATION = 0x2000
_ACC_ENUM = 0x4000
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
            carrier_server_api = sorted(
                name
                for name in names
                if name.startswith(_SERVER_API_CLASS_PREFIX) and name.endswith(".class")
            )
            carrier_server_api.extend(_generated_server_api_paths(names))
            if carrier_server_api:
                errors.append(
                    f"{label}: public carrier must remain code-free; server API classes "
                    "belong in the version-selected nested implementation(s): "
                    + ", ".join(sorted(set(carrier_server_api)))
                )
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
                        server_player_internal_name = (
                            "net/minecraft/server/level/ServerPlayer"
                            if family[0].startswith("26.")
                            else "net/minecraft/class_3222"
                        )
                        _validate_server_api_contract(
                            nested,
                            nested_names,
                            errors,
                            f"{label}: {nested_path}",
                            server_player_internal_name,
                        )
                        _validate_server_api_runtime_support(
                            nested,
                            nested_names,
                            errors,
                            f"{label}: {nested_path}",
                        )
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


@dataclass
class _ClassfilePool:
    major: int
    utf8: list[str | None]
    class_name_indexes: dict[int, int]
    integer_constants: dict[int, int]
    name_and_types: dict[int, tuple[int, int]]
    method_references: dict[int, tuple[int, int]]
    end: int


@dataclass(frozen=True)
class _ClassfileField:
    access_flags: int
    constant_value: object | None


@dataclass
class _ClassfileDeclaration:
    major: int
    access_flags: int
    this_class: str
    interfaces: tuple[str, ...]
    fields: dict[tuple[str, str], _ClassfileField]
    methods: dict[tuple[str, str], int]
    method_signatures: dict[tuple[str, str], str]
    attributes: frozenset[str]
    inner_class_access: dict[str, int]


def _classfile_pool(data: bytes) -> _ClassfilePool:
    if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("invalid Java classfile header")
    major = int.from_bytes(data[6:8], "big")
    constant_count = int.from_bytes(data[8:10], "big")
    utf8: list[str | None] = [None] * constant_count
    class_name_indexes: dict[int, int] = {}
    integer_constants: dict[int, int] = {}
    name_and_types: dict[int, tuple[int, int]] = {}
    method_references: dict[int, tuple[int, int]] = {}
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
            if tag == 3:
                integer_constants[index] = int.from_bytes(
                    data[offset : offset + 4], "big", signed=True
                )
            elif tag == 7:
                class_name_indexes[index] = int.from_bytes(
                    data[offset : offset + 2], "big"
                )
            elif tag in (10, 11):
                method_references[index] = (
                    int.from_bytes(data[offset : offset + 2], "big"),
                    int.from_bytes(data[offset + 2 : offset + 4], "big"),
                )
            elif tag == 12:
                name_and_types[index] = (
                    int.from_bytes(data[offset : offset + 2], "big"),
                    int.from_bytes(data[offset + 2 : offset + 4], "big"),
                )
            offset += size
            if tag in (5, 6):
                index += 1
        index += 1
    return _ClassfilePool(
        major,
        utf8,
        class_name_indexes,
        integer_constants,
        name_and_types,
        method_references,
        offset,
    )


def _pool_utf8(pool: _ClassfilePool, index: int, context: str) -> str:
    if not 0 < index < len(pool.utf8) or pool.utf8[index] is None:
        raise ValueError(f"invalid Java {context} UTF-8 index")
    return pool.utf8[index]  # type: ignore[return-value]


def _pool_class_name(pool: _ClassfilePool, index: int, context: str) -> str:
    name_index = pool.class_name_indexes.get(index)
    if name_index is None:
        raise ValueError(f"invalid Java {context} class index")
    return _pool_utf8(pool, name_index, context)


def _classfile_layout(
    data: bytes,
) -> tuple[int, list[str | None], list[int], int]:
    """Return classfile major, UTF-8 constants, class-name indexes, and pool end."""

    pool = _classfile_pool(data)
    return pool.major, pool.utf8, list(pool.class_name_indexes.values()), pool.end


def _read_classfile_attributes(
    data: bytes,
    offset: int,
    count: int,
    pool: _ClassfilePool,
) -> tuple[list[tuple[str, bytes]], int]:
    attributes: list[tuple[str, bytes]] = []
    for _ in range(count):
        if offset + 6 > len(data):
            raise ValueError("truncated Java class attribute")
        name = _pool_utf8(
            pool,
            int.from_bytes(data[offset : offset + 2], "big"),
            "attribute name",
        )
        length = int.from_bytes(data[offset + 2 : offset + 6], "big")
        offset += 6
        end = offset + length
        if end > len(data):
            raise ValueError("truncated Java class attribute payload")
        attributes.append((name, data[offset:end]))
        offset = end
    return attributes, offset


def _classfile_declaration(data: bytes) -> _ClassfileDeclaration:
    pool = _classfile_pool(data)
    offset = pool.end
    if offset + 8 > len(data):
        raise ValueError("truncated Java class declaration")
    access_flags = int.from_bytes(data[offset : offset + 2], "big")
    this_class = _pool_class_name(
        pool, int.from_bytes(data[offset + 2 : offset + 4], "big"), "this"
    )
    offset += 6  # access flags, this class, super class
    interface_count = int.from_bytes(data[offset : offset + 2], "big")
    offset += 2
    if offset + interface_count * 2 > len(data):
        raise ValueError("truncated Java interface table")
    interfaces = tuple(
        _pool_class_name(
            pool,
            int.from_bytes(data[offset + index * 2 : offset + index * 2 + 2], "big"),
            "interface",
        )
        for index in range(interface_count)
    )
    offset += interface_count * 2

    if offset + 2 > len(data):
        raise ValueError("truncated Java field table")
    field_count = int.from_bytes(data[offset : offset + 2], "big")
    offset += 2
    fields: dict[tuple[str, str], _ClassfileField] = {}
    for _ in range(field_count):
        if offset + 8 > len(data):
            raise ValueError("truncated Java field")
        member_access = int.from_bytes(data[offset : offset + 2], "big")
        name = _pool_utf8(
            pool, int.from_bytes(data[offset + 2 : offset + 4], "big"), "field name"
        )
        descriptor = _pool_utf8(
            pool,
            int.from_bytes(data[offset + 4 : offset + 6], "big"),
            "field descriptor",
        )
        attribute_count = int.from_bytes(data[offset + 6 : offset + 8], "big")
        member_attributes, offset = _read_classfile_attributes(
            data, offset + 8, attribute_count, pool
        )
        constant_value: object | None = None
        for attribute_name, payload in member_attributes:
            if attribute_name == "ConstantValue":
                if len(payload) != 2:
                    raise ValueError("invalid Java ConstantValue attribute")
                constant_value = pool.integer_constants.get(
                    int.from_bytes(payload, "big")
                )
        fields[(name, descriptor)] = _ClassfileField(member_access, constant_value)

    if offset + 2 > len(data):
        raise ValueError("truncated Java method table")
    method_count = int.from_bytes(data[offset : offset + 2], "big")
    offset += 2
    methods: dict[tuple[str, str], int] = {}
    method_signatures: dict[tuple[str, str], str] = {}
    for _ in range(method_count):
        if offset + 8 > len(data):
            raise ValueError("truncated Java method")
        member_access = int.from_bytes(data[offset : offset + 2], "big")
        name = _pool_utf8(
            pool, int.from_bytes(data[offset + 2 : offset + 4], "big"), "method name"
        )
        descriptor = _pool_utf8(
            pool,
            int.from_bytes(data[offset + 4 : offset + 6], "big"),
            "method descriptor",
        )
        attribute_count = int.from_bytes(data[offset + 6 : offset + 8], "big")
        member_attributes, offset = _read_classfile_attributes(
            data, offset + 8, attribute_count, pool
        )
        methods[(name, descriptor)] = member_access
        for attribute_name, payload in member_attributes:
            if attribute_name == "Signature":
                if len(payload) != 2:
                    raise ValueError("invalid Java method Signature attribute")
                method_signatures[(name, descriptor)] = _pool_utf8(
                    pool, int.from_bytes(payload, "big"), "method signature"
                )

    if offset + 2 > len(data):
        raise ValueError("truncated Java class attribute table")
    attribute_count = int.from_bytes(data[offset : offset + 2], "big")
    class_attributes, offset = _read_classfile_attributes(
        data, offset + 2, attribute_count, pool
    )
    if offset != len(data):
        raise ValueError("unexpected data after Java class attributes")
    inner_class_access: dict[str, int] = {}
    for attribute_name, payload in class_attributes:
        if attribute_name != "InnerClasses":
            continue
        if len(payload) < 2:
            raise ValueError("truncated Java InnerClasses attribute")
        count = int.from_bytes(payload[:2], "big")
        if len(payload) != 2 + count * 8:
            raise ValueError("invalid Java InnerClasses attribute")
        for index in range(count):
            entry = payload[2 + index * 8 : 10 + index * 8]
            inner_index = int.from_bytes(entry[:2], "big")
            if inner_index:
                inner_class_access[_pool_class_name(pool, inner_index, "inner")] = (
                    int.from_bytes(entry[6:8], "big")
                )
    return _ClassfileDeclaration(
        pool.major,
        access_flags,
        this_class,
        interfaces,
        fields,
        methods,
        method_signatures,
        frozenset(name for name, _ in class_attributes),
        inner_class_access,
    )


def _classfile_structural_references(data: bytes) -> set[str]:
    """Return classes referenced by constants, descriptors, and signatures."""

    pool = _classfile_pool(data)
    utf8 = pool.utf8
    references: set[str] = set()
    for class_index in pool.class_name_indexes:
        value = _pool_class_name(pool, class_index, "referenced")
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


def _classfile_method_table(data: bytes) -> tuple[int, dict[tuple[str, str], int]]:
    """Return classfile major and access flags keyed by method descriptor."""

    declaration = _classfile_declaration(data)
    return declaration.major, declaration.methods


def _classfile_methods(data: bytes) -> tuple[int, set[tuple[str, str]]]:
    major, methods = _classfile_method_table(data)
    return major, set(methods)


def _classfile_method_references(data: bytes) -> set[tuple[str, str, str]]:
    pool = _classfile_pool(data)
    references: set[tuple[str, str, str]] = set()
    for class_index, name_and_type_index in pool.method_references.values():
        name_and_type = pool.name_and_types.get(name_and_type_index)
        if name_and_type is None:
            raise ValueError("invalid Java method NameAndType index")
        references.add(
            (
                _pool_class_name(pool, class_index, "method owner"),
                _pool_utf8(pool, name_and_type[0], "method name"),
                _pool_utf8(pool, name_and_type[1], "method descriptor"),
            )
        )
    return references


def _generated_server_api_paths(names: set[str]) -> list[str]:
    return sorted(
        name
        for name in names
        if name.endswith(".class")
        and name.startswith(_SERVER_API_GENERATED_PREFIXES)
        and "/api/server/" in name
    )


def _missing_access_flags(actual: int, required: int) -> int:
    return required & ~actual


def _unexpected_access_flags(actual: int, forbidden: int) -> int:
    return actual & forbidden


def _server_api_method_flag_contract(class_path: str) -> tuple[int, int]:
    if class_path == _SERVER_API_CLASS:
        return _ACC_PUBLIC | _ACC_ABSTRACT, _ACC_STATIC
    if class_path == _SERVER_API_FACADE_CLASS:
        return _ACC_PUBLIC | _ACC_STATIC, _ACC_ABSTRACT
    if class_path in _SERVER_API_ENUM_CONSTANTS:
        return _ACC_PUBLIC | _ACC_STATIC, _ACC_ABSTRACT
    return _ACC_PUBLIC, _ACC_STATIC | _ACC_ABSTRACT


def _validate_public_server_api_declaration(
    class_path: str,
    declaration: _ClassfileDeclaration,
    errors: list[str],
    archive_label: str,
) -> None:
    expected_name = class_path[:-6]
    if declaration.this_class != expected_name:
        errors.append(
            f"{archive_label}: public server API entry {class_path} declares "
            f"{declaration.this_class}"
        )

    required = _ACC_PUBLIC
    forbidden = _ACC_ANNOTATION
    require_record_attribute = False
    if class_path == _SERVER_API_CLASS:
        required |= _ACC_INTERFACE | _ACC_ABSTRACT
        forbidden |= _ACC_FINAL | _ACC_ENUM
    elif class_path in _SERVER_API_RECORD_CLASSES:
        required |= _ACC_FINAL
        forbidden |= _ACC_INTERFACE | _ACC_ABSTRACT | _ACC_ENUM
        require_record_attribute = True
    elif class_path in _SERVER_API_ENUM_CONSTANTS:
        required |= _ACC_FINAL | _ACC_ENUM
        forbidden |= _ACC_INTERFACE | _ACC_ABSTRACT
    else:
        required |= _ACC_FINAL
        forbidden |= _ACC_INTERFACE | _ACC_ABSTRACT | _ACC_ENUM

    missing = _missing_access_flags(declaration.access_flags, required)
    unexpected = _unexpected_access_flags(declaration.access_flags, forbidden)
    if missing or unexpected:
        errors.append(
            f"{archive_label}: public server API class {class_path} has invalid "
            f"class modifiers 0x{declaration.access_flags:04x} "
            f"(missing 0x{missing:04x}, forbidden 0x{unexpected:04x})"
        )
    if require_record_attribute and "Record" not in declaration.attributes:
        errors.append(
            f"{archive_label}: public server API record {class_path} lacks its "
            "Record class attribute"
        )


def _validate_server_api_contract(
    archive: zipfile.ZipFile,
    names: set[str],
    errors: list[str],
    archive_label: str,
    server_player_internal_name: str,
) -> None:
    """Require the stable v1 server API without assuming one mapping namespace."""

    generated_api = _generated_server_api_paths(names)
    if generated_api:
        errors.append(
            f"{archive_label}: public server API class(es) must not be relocated "
            "under compat/generated or mixin/generated: "
            + ", ".join(generated_api)
        )

    missing_classes = sorted(_SERVER_API_REQUIRED_CLASSES - names)
    if missing_classes:
        errors.append(
            f"{archive_label}: missing stable public server API class(es): "
            + ", ".join(missing_classes)
        )

    required_methods = dict(_SERVER_API_REQUIRED_METHODS)
    required_methods[_SERVER_API_CLASS] = frozenset(
        {
            *required_methods[_SERVER_API_CLASS],
            *(
                (
                    method_name,
                    f"(L{server_player_internal_name};{remaining_parameters})"
                    f"{return_descriptor}",
                )
                for method_name, remaining_parameters, return_descriptor
                in _SERVER_API_PLAYER_METHODS
            ),
        }
    )

    declarations: dict[str, _ClassfileDeclaration] = {}
    for class_path in sorted(_SERVER_API_REQUIRED_CLASSES):
        if class_path not in names:
            continue
        try:
            declaration = _classfile_declaration(archive.read(class_path))
        except (KeyError, ValueError) as error:
            errors.append(
                f"{archive_label}: cannot inspect public server API class "
                f"{class_path} ({error})"
            )
            continue
        declarations[class_path] = declaration
        if declaration.major > _JAVA_21_CLASSFILE_MAJOR:
            errors.append(
                f"{archive_label}: public server API class {class_path} uses classfile "
                f"major {declaration.major}, expected <= {_JAVA_21_CLASSFILE_MAJOR}"
            )
        _validate_public_server_api_declaration(
            class_path, declaration, errors, archive_label
        )

    for class_path, expected_methods in required_methods.items():
        declaration = declarations.get(class_path)
        if declaration is None:
            continue
        missing_methods = sorted(expected_methods - set(declaration.methods))
        if missing_methods:
            rendered = ", ".join(
                f"{name}{descriptor}" for name, descriptor in missing_methods
            )
            errors.append(
                f"{archive_label}: public server API class {class_path} lacks "
                f"required v1 method descriptor(s): {rendered}"
            )
        non_public = sorted(
            (name, descriptor)
            for name, descriptor in expected_methods
            if (name, descriptor) in declaration.methods
            and not declaration.methods[(name, descriptor)] & _ACC_PUBLIC
        )
        if non_public:
            rendered = ", ".join(
                f"{name}{descriptor}" for name, descriptor in non_public
            )
            errors.append(
                f"{archive_label}: public server API method(s) are not public: {rendered}"
            )
        required_flags, forbidden_flags = _server_api_method_flag_contract(class_path)
        wrong_flags = sorted(
            (name, descriptor, flags)
            for (name, descriptor), flags in declaration.methods.items()
            if (name, descriptor) in expected_methods
            and (
                _missing_access_flags(flags, required_flags)
                or _unexpected_access_flags(flags, forbidden_flags)
            )
        )
        if wrong_flags:
            rendered = ", ".join(
                f"{name}{descriptor}=0x{flags:04x}"
                for name, descriptor, flags in wrong_flags
            )
            errors.append(
                f"{archive_label}: public server API method(s) have invalid "
                f"static/instance modifiers: {rendered}"
            )

    api_declaration = declarations.get(_SERVER_API_CLASS)
    if api_declaration is not None:
        session_descriptor = (
            f"(L{server_player_internal_name};)Ljava/util/Optional;"
        )
        session_method = ("currentAnvilSession", session_descriptor)
        expected_signature = (
            f"(L{server_player_internal_name};)"
            "Ljava/util/Optional<"
            "Lcom/reign/betterlore/api/server/AnvilEditorSession;>;"
        )
        actual_signature = api_declaration.method_signatures.get(session_method)
        if actual_signature != expected_signature:
            errors.append(
                f"{archive_label}: currentAnvilSession generic Signature is "
                f"{actual_signature!r}, expected {expected_signature!r}"
            )

    if api_declaration is not None:
        api_version = api_declaration.fields.get(("API_VERSION", "I"))
        required_field_flags = _ACC_PUBLIC | _ACC_STATIC | _ACC_FINAL
        if api_version is None:
            errors.append(
                f"{archive_label}: {_SERVER_API_CLASS} lacks public API_VERSION:I"
            )
        else:
            missing = _missing_access_flags(
                api_version.access_flags, required_field_flags
            )
            forbidden = _unexpected_access_flags(
                api_version.access_flags, _ACC_PRIVATE | _ACC_PROTECTED
            )
            if missing or forbidden:
                errors.append(
                    f"{archive_label}: API_VERSION must be public static final "
                    f"(flags 0x{api_version.access_flags:04x})"
                )
            if api_version.constant_value != 1:
                errors.append(
                    f"{archive_label}: API_VERSION constant is "
                    f"{api_version.constant_value!r}, expected 1"
                )

    enum_field_flags = _ACC_PUBLIC | _ACC_STATIC | _ACC_FINAL | _ACC_ENUM
    for class_path, constants in _SERVER_API_ENUM_CONSTANTS.items():
        declaration = declarations.get(class_path)
        if declaration is None:
            continue
        descriptor = f"L{class_path[:-6]};"
        for constant in constants:
            field = declaration.fields.get((constant, descriptor))
            if field is None:
                errors.append(
                    f"{archive_label}: public server API enum {class_path} lacks "
                    f"constant field {constant}:{descriptor}"
                )
                continue
            missing = _missing_access_flags(field.access_flags, enum_field_flags)
            forbidden = _unexpected_access_flags(
                field.access_flags, _ACC_PRIVATE | _ACC_PROTECTED
            )
            if missing or forbidden:
                errors.append(
                    f"{archive_label}: public server API enum constant "
                    f"{class_path}#{constant} has invalid modifiers "
                    f"0x{field.access_flags:04x}"
                )

    # Holder is required executable support for the facade, not part of the
    # public ABI. Its visibility is recorded in InnerClasses rather than in
    # the nested classfile's top-level access_flags field.
    if _SERVER_API_HOLDER_CLASS not in names:
        errors.append(
            f"{archive_label}: missing private server API support class "
            f"{_SERVER_API_HOLDER_CLASS}"
        )
    else:
        try:
            holder_data = archive.read(_SERVER_API_HOLDER_CLASS)
            holder = _classfile_declaration(holder_data)
            holder_method_references = _classfile_method_references(holder_data)
            holder_pool = _classfile_pool(holder_data)
        except (KeyError, ValueError) as error:
            errors.append(
                f"{archive_label}: cannot inspect private server API support class "
                f"{_SERVER_API_HOLDER_CLASS} ({error})"
            )
        else:
            if holder.major > _JAVA_21_CLASSFILE_MAJOR:
                errors.append(
                    f"{archive_label}: private server API support class "
                    f"{_SERVER_API_HOLDER_CLASS} uses classfile major {holder.major}, "
                    f"expected <= {_JAVA_21_CLASSFILE_MAJOR}"
                )
            if holder.access_flags & (
                _ACC_PUBLIC
                | _ACC_PROTECTED
                | _ACC_INTERFACE
                | _ACC_ABSTRACT
                | _ACC_ENUM
                | _ACC_ANNOTATION
            ):
                errors.append(
                    f"{archive_label}: {_SERVER_API_HOLDER_CLASS} must remain "
                    "non-public concrete executable support"
                )
            holder_flags = holder.inner_class_access.get(
                _SERVER_API_HOLDER_CLASS[:-6]
            )
            required_holder_flags = _ACC_PRIVATE | _ACC_STATIC | _ACC_FINAL
            if holder_flags is None or _missing_access_flags(
                holder_flags, required_holder_flags
            ) or _unexpected_access_flags(holder_flags, _ACC_PUBLIC | _ACC_PROTECTED):
                errors.append(
                    f"{archive_label}: {_SERVER_API_HOLDER_CLASS} must remain "
                    "private static final executable support"
                )
            instance = holder.fields.get(
                (
                    "INSTANCE",
                    "Lcom/reign/betterlore/api/server/BetterLoreServerApi;",
                )
            )
            required_instance_flags = _ACC_PRIVATE | _ACC_STATIC | _ACC_FINAL
            if instance is None or _missing_access_flags(
                instance.access_flags, required_instance_flags
            ):
                errors.append(
                    f"{archive_label}: private server API Holder lacks its "
                    "private static final INSTANCE field"
                )
            clinit_flags = holder.methods.get(("<clinit>", "()V"))
            if clinit_flags is None or not clinit_flags & _ACC_STATIC:
                errors.append(
                    f"{archive_label}: private server API Holder lacks its "
                    "static initializer"
                )
            instantiate_reference = (
                "com/reign/betterlore/compat/CompatibilityRuntime",
                "instantiate",
                "(Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
            )
            if instantiate_reference not in holder_method_references:
                errors.append(
                    f"{archive_label}: private server API Holder does not invoke "
                    "CompatibilityRuntime.instantiate(String, Class)"
                )
            implementation_name = _SERVER_API_IMPLEMENTATION_INTERNAL_NAME.replace(
                "/", "."
            )
            if implementation_name not in holder_pool.utf8:
                errors.append(
                    f"{archive_label}: private server API Holder does not name "
                    f"runtime implementation {implementation_name}"
                )


def _generated_runtime_class_path(
    loader: str,
    family_id: str,
    root_path: str,
) -> str:
    relative = root_path[len(_BETTER_LORE_INTERNAL_PREFIX) :]
    return (
        f"com/reign/betterlore/compat/generated/{loader}/{family_id}/{relative}"
    )


def _is_server_api_validator_reference(internal_name: str) -> bool:
    return internal_name == _SERVER_API_VALIDATOR_INTERNAL_NAME or (
        internal_name.startswith("com/reign/betterlore/compat/generated/")
        and internal_name.endswith(
            "/internal/serverapi/AnvilEditorDraftValidator"
        )
    )


def _is_server_api_submission_reference(internal_name: str) -> bool:
    return internal_name == _SERVER_API_SUBMISSION_INTERNAL_NAME or (
        internal_name.startswith("com/reign/betterlore/compat/generated/")
        and internal_name.endswith(
            "/internal/serverapi/AnvilEditorDraftSubmission"
        )
    )


def _validate_server_api_runtime_support(
    archive: zipfile.ZipFile,
    names: set[str],
    errors: list[str],
    archive_label: str,
    *,
    loader: str | None = None,
    family_ids: tuple[str, ...] = (),
) -> None:
    """Validate the runtime selected behind the stable, unrelocated facade."""

    selected: list[tuple[str, str]] = []
    if loader is None:
        selected.append(("exact implementation", _SERVER_API_IMPLEMENTATION_CLASS))
    else:
        for family_id in family_ids:
            generated = _generated_runtime_class_path(
                loader, family_id, _SERVER_API_IMPLEMENTATION_CLASS
            )
            if generated in names:
                selected.append((f"{loader}/{family_id}", generated))
            elif _SERVER_API_IMPLEMENTATION_CLASS in names:
                selected.append(
                    (f"{loader}/{family_id} root fallback", _SERVER_API_IMPLEMENTATION_CLASS)
                )
            else:
                errors.append(
                    f"{archive_label}: no server API implementation is selectable for "
                    f"{loader}/{family_id}; expected {generated} or "
                    f"{_SERVER_API_IMPLEMENTATION_CLASS}"
                )

    validated: set[str] = set()
    for selection_label, implementation_path in selected:
        if implementation_path in validated:
            continue
        validated.add(implementation_path)
        if implementation_path not in names:
            errors.append(
                f"{archive_label}: missing server API {selection_label}: "
                f"{implementation_path}"
            )
            continue
        try:
            implementation_data = archive.read(implementation_path)
            implementation = _classfile_declaration(implementation_data)
            implementation_references = _classfile_structural_references(
                implementation_data
            )
            implementation_method_references = _classfile_method_references(
                implementation_data
            )
        except (KeyError, ValueError) as error:
            errors.append(
                f"{archive_label}: cannot inspect server API {selection_label} "
                f"{implementation_path} ({error})"
            )
            continue

        expected_name = implementation_path[:-6]
        if implementation.this_class != expected_name:
            errors.append(
                f"{archive_label}: server API implementation entry "
                f"{implementation_path} declares {implementation.this_class}"
            )
        required_class_flags = _ACC_PUBLIC
        forbidden_class_flags = _ACC_INTERFACE | _ACC_ABSTRACT | _ACC_ENUM | _ACC_ANNOTATION
        if _missing_access_flags(
            implementation.access_flags, required_class_flags
        ) or _unexpected_access_flags(
            implementation.access_flags, forbidden_class_flags
        ):
            errors.append(
                f"{archive_label}: server API implementation {implementation_path} "
                f"is not a public concrete class (flags "
                f"0x{implementation.access_flags:04x})"
            )
        constructor_flags = implementation.methods.get(("<init>", "()V"))
        if constructor_flags is None or not constructor_flags & _ACC_PUBLIC or (
            constructor_flags & (_ACC_STATIC | _ACC_ABSTRACT)
        ):
            errors.append(
                f"{archive_label}: server API implementation {implementation_path} "
                "lacks a public instance no-argument constructor"
            )
        if _SERVER_API_CLASS[:-6] not in implementation.interfaces:
            errors.append(
                f"{archive_label}: server API implementation {implementation_path} "
                f"does not directly implement {_SERVER_API_CLASS[:-6]}"
            )

        missing_runtime_references = sorted(
            reference + ".class"
            for reference in implementation_references
            if reference.startswith(_BETTER_LORE_INTERNAL_PREFIX)
            and reference + ".class" not in names
        )
        if missing_runtime_references:
            errors.append(
                f"{archive_label}: server API implementation {implementation_path} "
                "has missing runtime support reference(s): "
                + ", ".join(missing_runtime_references)
            )

        validator_calls = sorted(
            reference
            for reference in implementation_method_references
            if _is_server_api_validator_reference(reference[0])
            and reference[1:] == _SERVER_API_VALIDATOR_METHOD
        )
        if len(validator_calls) != 1:
            errors.append(
                f"{archive_label}: server API implementation {implementation_path} "
                "must invoke exactly one selected AnvilEditorDraftValidator.validate "
                f"method; found {validator_calls!r}"
            )
            continue

        validator_internal_name = validator_calls[0][0]
        validator_path = validator_internal_name + ".class"
        if validator_path not in names:
            errors.append(
                f"{archive_label}: server API implementation {implementation_path} "
                f"calls missing validator {validator_path}"
            )
            continue
        try:
            validator = _classfile_declaration(archive.read(validator_path))
        except (KeyError, ValueError) as error:
            errors.append(
                f"{archive_label}: cannot inspect server API validator "
                f"{validator_path} ({error})"
            )
            continue
        if validator.this_class != validator_internal_name:
            errors.append(
                f"{archive_label}: server API validator entry {validator_path} "
                f"declares {validator.this_class}"
            )
        if validator.access_flags & (
            _ACC_INTERFACE | _ACC_ABSTRACT | _ACC_ENUM | _ACC_ANNOTATION
        ):
            errors.append(
                f"{archive_label}: server API validator {validator_path} is not "
                f"a concrete class (flags 0x{validator.access_flags:04x})"
            )
        implementation_package = implementation.this_class.rsplit("/", 1)[0]
        validator_package = validator.this_class.rsplit("/", 1)[0]
        cross_package = implementation_package != validator_package
        if cross_package and not validator.access_flags & _ACC_PUBLIC:
            errors.append(
                f"{archive_label}: cross-relocated server API validator "
                f"{validator_path} is not public to {implementation_path}"
            )
        validator_method_flags = validator.methods.get(_SERVER_API_VALIDATOR_METHOD)
        required_validator_flags = _ACC_STATIC | (_ACC_PUBLIC if cross_package else 0)
        forbidden_validator_flags = _ACC_PRIVATE | _ACC_ABSTRACT
        if validator_method_flags is None:
            errors.append(
                f"{archive_label}: server API validator {validator_path} lacks "
                f"{_SERVER_API_VALIDATOR_METHOD[0]}"
                f"{_SERVER_API_VALIDATOR_METHOD[1]}"
            )
        elif _missing_access_flags(
            validator_method_flags, required_validator_flags
        ) or _unexpected_access_flags(
            validator_method_flags, forbidden_validator_flags
        ):
            errors.append(
                f"{archive_label}: server API validator {validator_path} is not "
                f"callable from {implementation_path} (method flags "
                f"0x{validator_method_flags:04x})"
            )

        submission_calls = sorted(
            reference
            for reference in implementation_method_references
            if _is_server_api_submission_reference(reference[0])
            and reference[1] == "route"
        )
        valid_submission_calls = [
            reference
            for reference in submission_calls
            if reference[2]
            == (
                "(Lcom/reign/betterlore/api/server/AnvilEditorDraft;"
                "Lcom/reign/betterlore/api/server/AnvilEditorDraftValidation;"
                f"ZIIZL{reference[0]}$DraftApplier;)"
                "Lcom/reign/betterlore/api/server/AnvilEditorDraftResult;"
            )
        ]
        if len(valid_submission_calls) != 1:
            errors.append(
                f"{archive_label}: server API implementation {implementation_path} "
                "must call the selected public AnvilEditorDraftSubmission.route; "
                f"found {submission_calls!r}"
            )
        else:
            submission_internal_name = valid_submission_calls[0][0]
            submission_path = submission_internal_name + ".class"
            draft_applier_internal_name = (
                submission_internal_name + "$DraftApplier"
            )
            draft_applier_path = draft_applier_internal_name + ".class"
            if submission_path not in names:
                errors.append(
                    f"{archive_label}: server API implementation "
                    f"{implementation_path} calls missing submission router "
                    f"{submission_path}"
                )
            else:
                try:
                    submission = _classfile_declaration(
                        archive.read(submission_path)
                    )
                except (KeyError, ValueError) as error:
                    errors.append(
                        f"{archive_label}: cannot inspect server API submission "
                        f"router {submission_path} ({error})"
                    )
                else:
                    submission_forbidden = (
                        _ACC_INTERFACE | _ACC_ABSTRACT | _ACC_ENUM | _ACC_ANNOTATION
                    )
                    if not submission.access_flags & _ACC_PUBLIC or (
                        submission.access_flags & submission_forbidden
                    ):
                        errors.append(
                            f"{archive_label}: server API submission router "
                            f"{submission_path} must remain a public concrete class"
                        )
                    route_method = ("route", valid_submission_calls[0][2])
                    route_flags = submission.methods.get(route_method)
                    required_route_flags = _ACC_PUBLIC | _ACC_STATIC
                    if route_flags is None or _missing_access_flags(
                        route_flags, required_route_flags
                    ) or _unexpected_access_flags(
                        route_flags, _ACC_PRIVATE | _ACC_PROTECTED | _ACC_ABSTRACT
                    ):
                        errors.append(
                            f"{archive_label}: server API submission router "
                            f"{submission_path} lacks callable public static "
                            f"route{route_method[1]}"
                        )

            if draft_applier_path not in names:
                errors.append(
                    f"{archive_label}: server API submission router lacks public "
                    f"DraftApplier support {draft_applier_path}"
                )
            else:
                try:
                    draft_applier = _classfile_declaration(
                        archive.read(draft_applier_path)
                    )
                except (KeyError, ValueError) as error:
                    errors.append(
                        f"{archive_label}: cannot inspect server API DraftApplier "
                        f"{draft_applier_path} ({error})"
                    )
                else:
                    required_applier_class_flags = (
                        _ACC_PUBLIC | _ACC_INTERFACE | _ACC_ABSTRACT
                    )
                    if _missing_access_flags(
                        draft_applier.access_flags,
                        required_applier_class_flags,
                    ) or _unexpected_access_flags(
                        draft_applier.access_flags,
                        _ACC_ENUM | _ACC_ANNOTATION,
                    ):
                        errors.append(
                            f"{archive_label}: server API DraftApplier "
                            f"{draft_applier_path} must remain a public interface"
                        )
                    apply_flags = draft_applier.methods.get(
                        ("apply", _SERVER_DRAFT_APPLY_DESCRIPTOR)
                    )
                    required_apply_flags = _ACC_PUBLIC | _ACC_ABSTRACT
                    if apply_flags is None or _missing_access_flags(
                        apply_flags, required_apply_flags
                    ) or _unexpected_access_flags(apply_flags, _ACC_STATIC):
                        errors.append(
                            f"{archive_label}: server API DraftApplier "
                            f"{draft_applier_path} lacks public instance "
                            f"apply{_SERVER_DRAFT_APPLY_DESCRIPTOR}"
                        )

        bridge_call = (
            _ANVIL_LORE_MENU_BRIDGE_INTERNAL_NAME,
            "betterLore$handleServerDraft",
            _SERVER_DRAFT_APPLY_DESCRIPTOR,
        )
        if bridge_call not in implementation_method_references:
            errors.append(
                f"{archive_label}: server API implementation {implementation_path} "
                "does not link the canonical public "
                "AnvilLoreMenuBridge.betterLore$handleServerDraft member"
            )
        bridge_path = _ANVIL_LORE_MENU_BRIDGE_INTERNAL_NAME + ".class"
        if bridge_path not in names:
            errors.append(
                f"{archive_label}: server API implementation {implementation_path} "
                f"requires missing bridge {bridge_path}"
            )
        else:
            try:
                bridge = _classfile_declaration(archive.read(bridge_path))
            except (KeyError, ValueError) as error:
                errors.append(
                    f"{archive_label}: cannot inspect server API bridge "
                    f"{bridge_path} ({error})"
                )
            else:
                required_bridge_flags = _ACC_PUBLIC | _ACC_INTERFACE | _ACC_ABSTRACT
                if _missing_access_flags(
                    bridge.access_flags, required_bridge_flags
                ) or _unexpected_access_flags(
                    bridge.access_flags, _ACC_ENUM | _ACC_ANNOTATION
                ):
                    errors.append(
                        f"{archive_label}: server API bridge {bridge_path} must "
                        "remain a public interface"
                    )
                bridge_method_flags = bridge.methods.get(bridge_call[1:])
                required_bridge_method_flags = _ACC_PUBLIC | _ACC_ABSTRACT
                if bridge_method_flags is None or _missing_access_flags(
                    bridge_method_flags, required_bridge_method_flags
                ) or _unexpected_access_flags(
                    bridge_method_flags, _ACC_STATIC
                ):
                    errors.append(
                        f"{archive_label}: server API bridge {bridge_path} lacks "
                        "public instance betterLore$handleServerDraft"
                        f"{_SERVER_DRAFT_APPLY_DESCRIPTOR}"
                    )


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
            _validate_server_api_contract(
                archive,
                names,
                errors,
                label,
                "net/minecraft/server/level/ServerPlayer",
            )
            _validate_server_api_runtime_support(
                archive,
                names,
                errors,
                label,
                loader=artifact.loader,
                family_ids=tuple(sorted(expected_family_ids)),
            )
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


def _synthetic_classfile(
    internal_name: str,
    methods: set[tuple[str, str]],
    *,
    major: int = _JAVA_21_CLASSFILE_MAJOR,
    non_public: tuple[str, str] | None = None,
    class_access: int = _ACC_PUBLIC | 0x0020,
    interfaces: tuple[str, ...] = (),
    fields: Mapping[tuple[str, str], tuple[int, int | None]] | None = None,
    method_access: Mapping[tuple[str, str], int] | None = None,
    method_signatures: Mapping[tuple[str, str], str] | None = None,
    record: bool = False,
    inner_class: tuple[str, str, int] | None = None,
    references: tuple[str, ...] = (),
    method_references: tuple[tuple[str, str, str], ...] = (),
    utf8_constants: tuple[str, ...] = (),
) -> bytes:
    """Build the small classfile subset consumed by the dependency-free parser."""

    constants: list[bytes] = []
    utf8_indexes: dict[str, int] = {}
    class_indexes: dict[str, int] = {}

    def add_utf8(value: str) -> int:
        existing = utf8_indexes.get(value)
        if existing is not None:
            return existing
        encoded = value.encode("utf-8")
        constants.append(b"\x01" + len(encoded).to_bytes(2, "big") + encoded)
        index = len(constants)
        utf8_indexes[value] = index
        return index

    def add_class(value: str) -> int:
        existing = class_indexes.get(value)
        if existing is not None:
            return existing
        name_index = add_utf8(value)
        constants.append(b"\x07" + name_index.to_bytes(2, "big"))
        index = len(constants)
        class_indexes[value] = index
        return index

    def add_integer(value: int) -> int:
        constants.append(b"\x03" + value.to_bytes(4, "big", signed=True))
        return len(constants)

    def add_name_and_type(name: str, descriptor: str) -> int:
        name_index = add_utf8(name)
        descriptor_index = add_utf8(descriptor)
        constants.append(
            b"\x0c"
            + name_index.to_bytes(2, "big")
            + descriptor_index.to_bytes(2, "big")
        )
        return len(constants)

    def add_method_reference(owner: str, name: str, descriptor: str) -> None:
        owner_index = add_class(owner)
        name_and_type_index = add_name_and_type(name, descriptor)
        constants.append(
            b"\x0a"
            + owner_index.to_bytes(2, "big")
            + name_and_type_index.to_bytes(2, "big")
        )

    this_class = add_class(internal_name)
    super_class = add_class("java/lang/Object")
    interface_indexes = [add_class(interface) for interface in interfaces]
    for reference in references:
        add_class(reference)
    for owner, name, descriptor in method_references:
        add_method_reference(owner, name, descriptor)
    for value in utf8_constants:
        add_utf8(value)

    field_entries: list[tuple[int, int, int, int | None]] = []
    for (name, descriptor), (access, constant) in sorted((fields or {}).items()):
        name_index = add_utf8(name)
        descriptor_index = add_utf8(descriptor)
        constant_index = add_integer(constant) if constant is not None else None
        field_entries.append((access, name_index, descriptor_index, constant_index))
    constant_value_name = add_utf8("ConstantValue") if any(
        entry[3] is not None for entry in field_entries
    ) else None

    method_entries: list[tuple[int, int, int, int | None]] = []
    for name, descriptor in sorted(methods):
        name_index = add_utf8(name)
        descriptor_index = add_utf8(descriptor)
        access = (method_access or {}).get((name, descriptor), _ACC_PUBLIC)
        if (name, descriptor) == non_public:
            access = _ACC_PRIVATE
        signature = (method_signatures or {}).get((name, descriptor))
        signature_index = add_utf8(signature) if signature is not None else None
        method_entries.append((access, name_index, descriptor_index, signature_index))
    signature_name = add_utf8("Signature") if any(
        entry[3] is not None for entry in method_entries
    ) else None

    class_attributes: list[tuple[int, bytes]] = []
    if record:
        class_attributes.append((add_utf8("Record"), b"\x00\x00"))
    if inner_class is not None:
        outer_name, simple_name, flags = inner_class
        payload = (
            b"\x00\x01"
            + this_class.to_bytes(2, "big")
            + add_class(outer_name).to_bytes(2, "big")
            + add_utf8(simple_name).to_bytes(2, "big")
            + flags.to_bytes(2, "big")
        )
        class_attributes.append((add_utf8("InnerClasses"), payload))

    output = bytearray(b"\xca\xfe\xba\xbe\x00\x00")
    output.extend(major.to_bytes(2, "big"))
    output.extend((len(constants) + 1).to_bytes(2, "big"))
    for constant in constants:
        output.extend(constant)
    output.extend(class_access.to_bytes(2, "big"))
    output.extend(this_class.to_bytes(2, "big"))
    output.extend(super_class.to_bytes(2, "big"))
    output.extend(len(interface_indexes).to_bytes(2, "big"))
    for interface_index in interface_indexes:
        output.extend(interface_index.to_bytes(2, "big"))
    output.extend(len(field_entries).to_bytes(2, "big"))
    for access, name_index, descriptor_index, constant_index in field_entries:
        output.extend(access.to_bytes(2, "big"))
        output.extend(name_index.to_bytes(2, "big"))
        output.extend(descriptor_index.to_bytes(2, "big"))
        output.extend((1 if constant_index is not None else 0).to_bytes(2, "big"))
        if constant_index is not None:
            output.extend(constant_value_name.to_bytes(2, "big"))  # type: ignore[union-attr]
            output.extend(b"\x00\x00\x00\x02")
            output.extend(constant_index.to_bytes(2, "big"))
    output.extend(len(method_entries).to_bytes(2, "big"))
    for access, name_index, descriptor_index, signature_index in method_entries:
        output.extend(access.to_bytes(2, "big"))
        output.extend(name_index.to_bytes(2, "big"))
        output.extend(descriptor_index.to_bytes(2, "big"))
        output.extend((1 if signature_index is not None else 0).to_bytes(2, "big"))
        if signature_index is not None:
            output.extend(signature_name.to_bytes(2, "big"))  # type: ignore[union-attr]
            output.extend(b"\x00\x00\x00\x02")
            output.extend(signature_index.to_bytes(2, "big"))
    output.extend(len(class_attributes).to_bytes(2, "big"))
    for name_index, payload in class_attributes:
        output.extend(name_index.to_bytes(2, "big"))
        output.extend(len(payload).to_bytes(4, "big"))
        output.extend(payload)
    return bytes(output)


def _synthetic_server_api_methods(
    server_player_internal_name: str,
) -> dict[str, set[tuple[str, str]]]:
    methods = {
        path: set(required) for path, required in _SERVER_API_REQUIRED_METHODS.items()
    }
    methods[_SERVER_API_CLASS].update(
        (
            method_name,
            f"(L{server_player_internal_name};{remaining_parameters})"
            f"{return_descriptor}",
        )
        for method_name, remaining_parameters, return_descriptor in _SERVER_API_PLAYER_METHODS
    )
    return methods


def _write_synthetic_server_api_jar(
    path: Path,
    server_player_internal_name: str,
    *,
    omit: str | None = None,
    relocated: str | None = None,
    too_new: str | None = None,
    missing_method: tuple[str, tuple[str, str]] | None = None,
    non_public: tuple[str, tuple[str, str]] | None = None,
    class_access_override: tuple[str, int] | None = None,
    method_access_override: tuple[str, tuple[str, str], int] | None = None,
    api_version_flags: int = _ACC_PUBLIC | _ACC_STATIC | _ACC_FINAL,
    api_version_value: int | None = 1,
    omit_field: tuple[str, str, str] | None = None,
    field_access_override: tuple[str, str, str, int] | None = None,
    session_signature: str | None = "default",
    holder_inner_flags: int = _ACC_PRIVATE | _ACC_STATIC | _ACC_FINAL,
    include_runtime: bool = True,
    implementation_path: str = _SERVER_API_IMPLEMENTATION_CLASS,
    implementation_constructor_flags: int = _ACC_PUBLIC,
    implementation_interfaces: tuple[str, ...] = (_SERVER_API_CLASS[:-6],),
    validator_path: str = _SERVER_API_VALIDATOR_CLASS,
    validator_call_path: str | None = None,
    validator_method_flags: int = _ACC_STATIC,
    omit_validator: bool = False,
    submission_path: str = _SERVER_API_SUBMISSION_INTERNAL_NAME + ".class",
    submission_route_flags: int = _ACC_PUBLIC | _ACC_STATIC,
    draft_applier_method_flags: int = _ACC_PUBLIC | _ACC_ABSTRACT,
    omit_submission: bool = False,
    bridge_method_flags: int = _ACC_PUBLIC | _ACC_ABSTRACT,
    omit_bridge: bool = False,
) -> None:
    methods_by_class = _synthetic_server_api_methods(server_player_internal_name)
    if missing_method is not None:
        methods_by_class[missing_method[0]].discard(missing_method[1])
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        for class_path in sorted(_SERVER_API_REQUIRED_CLASSES):
            if class_path == omit:
                continue
            methods = methods_by_class.get(class_path, set())
            if class_path == _SERVER_API_CLASS:
                class_access = _ACC_PUBLIC | _ACC_INTERFACE | _ACC_ABSTRACT
            elif class_path in _SERVER_API_RECORD_CLASSES:
                class_access = _ACC_PUBLIC | _ACC_FINAL | 0x0020
            elif class_path in _SERVER_API_ENUM_CONSTANTS:
                class_access = _ACC_PUBLIC | _ACC_FINAL | 0x0020 | _ACC_ENUM
            else:
                class_access = _ACC_PUBLIC | _ACC_FINAL | 0x0020
            if class_access_override is not None and class_access_override[0] == class_path:
                class_access = class_access_override[1]

            access_by_method: dict[tuple[str, str], int] = {}
            default_method_flags, _ = _server_api_method_flag_contract(class_path)
            for method in methods:
                access_by_method[method] = default_method_flags
            if method_access_override is not None and method_access_override[0] == class_path:
                access_by_method[method_access_override[1]] = method_access_override[2]

            class_fields: dict[tuple[str, str], tuple[int, int | None]] = {}
            if class_path == _SERVER_API_CLASS:
                class_fields[("API_VERSION", "I")] = (
                    api_version_flags,
                    api_version_value,
                )
            constants = _SERVER_API_ENUM_CONSTANTS.get(class_path)
            if constants is not None:
                descriptor = f"L{class_path[:-6]};"
                for constant in constants:
                    class_fields[(constant, descriptor)] = (
                        _ACC_PUBLIC | _ACC_STATIC | _ACC_FINAL | _ACC_ENUM,
                        None,
                    )
            if omit_field is not None and omit_field[0] == class_path:
                class_fields.pop((omit_field[1], omit_field[2]), None)
            if field_access_override is not None and field_access_override[0] == class_path:
                field_key = (field_access_override[1], field_access_override[2])
                if field_key in class_fields:
                    class_fields[field_key] = (
                        field_access_override[3],
                        class_fields[field_key][1],
                    )

            signatures: dict[tuple[str, str], str] = {}
            if class_path == _SERVER_API_CLASS and session_signature is not None:
                server_player_descriptor = (
                    f"L{server_player_internal_name};"
                )
                session_method = (
                    "currentAnvilSession",
                    f"({server_player_descriptor})Ljava/util/Optional;",
                )
                signatures[session_method] = (
                    f"({server_player_descriptor})"
                    "Ljava/util/Optional<"
                    "Lcom/reign/betterlore/api/server/AnvilEditorSession;>;"
                    if session_signature == "default"
                    else session_signature
                )
            archive.writestr(
                class_path,
                _synthetic_classfile(
                    class_path[:-6],
                    methods,
                    major=(
                        _JAVA_21_CLASSFILE_MAJOR + 1
                        if class_path == too_new
                        else _JAVA_21_CLASSFILE_MAJOR
                    ),
                    class_access=class_access,
                    fields=class_fields,
                    method_access=access_by_method,
                    method_signatures=signatures,
                    record=class_path in _SERVER_API_RECORD_CLASSES,
                    non_public=(
                        non_public[1]
                        if non_public is not None and non_public[0] == class_path
                        else None
                    ),
                ),
            )
        if _SERVER_API_HOLDER_CLASS != omit:
            holder_fields = {
                (
                    "INSTANCE",
                    "Lcom/reign/betterlore/api/server/BetterLoreServerApi;",
                ): (_ACC_PRIVATE | _ACC_STATIC | _ACC_FINAL, None)
            }
            if omit_field is not None and omit_field[0] == _SERVER_API_HOLDER_CLASS:
                holder_fields.pop((omit_field[1], omit_field[2]), None)
            holder_methods = {("<clinit>", "()V")}
            archive.writestr(
                _SERVER_API_HOLDER_CLASS,
                _synthetic_classfile(
                    _SERVER_API_HOLDER_CLASS[:-6],
                    holder_methods,
                    major=(
                        _JAVA_21_CLASSFILE_MAJOR + 1
                        if _SERVER_API_HOLDER_CLASS == too_new
                        else _JAVA_21_CLASSFILE_MAJOR
                    ),
                    class_access=_ACC_FINAL | 0x0020,
                    fields=holder_fields,
                    method_access={("<clinit>", "()V"): _ACC_STATIC},
                    inner_class=(
                        _SERVER_API_FACADE_CLASS[:-6],
                        "Holder",
                        holder_inner_flags,
                    ),
                    method_references=(
                        (
                            "com/reign/betterlore/compat/CompatibilityRuntime",
                            "instantiate",
                            "(Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
                        ),
                    ),
                    utf8_constants=(
                        _SERVER_API_IMPLEMENTATION_INTERNAL_NAME.replace("/", "."),
                    ),
                ),
            )
        if relocated is not None:
            archive.writestr(
                relocated,
                _synthetic_classfile(relocated[:-6], set()),
            )
        if include_runtime:
            call_path = validator_call_path or validator_path
            submission_internal_name = submission_path[:-6]
            draft_applier_internal_name = submission_internal_name + "$DraftApplier"
            submission_descriptor = (
                "(Lcom/reign/betterlore/api/server/AnvilEditorDraft;"
                "Lcom/reign/betterlore/api/server/AnvilEditorDraftValidation;"
                f"ZIIZL{draft_applier_internal_name};)"
                "Lcom/reign/betterlore/api/server/AnvilEditorDraftResult;"
            )
            implementation_methods = {("<init>", "()V")}
            archive.writestr(
                implementation_path,
                _synthetic_classfile(
                    implementation_path[:-6],
                    implementation_methods,
                    class_access=_ACC_PUBLIC | _ACC_FINAL | 0x0020,
                    interfaces=implementation_interfaces,
                    method_access={
                        ("<init>", "()V"): implementation_constructor_flags
                    },
                    references=(
                        call_path[:-6],
                        submission_internal_name,
                        draft_applier_internal_name,
                        _ANVIL_LORE_MENU_BRIDGE_INTERNAL_NAME,
                    ),
                    method_references=(
                        (
                            call_path[:-6],
                            _SERVER_API_VALIDATOR_METHOD[0],
                            _SERVER_API_VALIDATOR_METHOD[1],
                        ),
                        (
                            submission_internal_name,
                            "route",
                            submission_descriptor,
                        ),
                        (
                            _ANVIL_LORE_MENU_BRIDGE_INTERNAL_NAME,
                            "betterLore$handleServerDraft",
                            _SERVER_DRAFT_APPLY_DESCRIPTOR,
                        ),
                    ),
                ),
            )
            if not omit_validator:
                archive.writestr(
                    validator_path,
                    _synthetic_classfile(
                        validator_path[:-6],
                        {_SERVER_API_VALIDATOR_METHOD},
                        class_access=_ACC_FINAL | 0x0020,
                        method_access={
                            _SERVER_API_VALIDATOR_METHOD: validator_method_flags
                        },
                    ),
                )
            if not omit_submission:
                archive.writestr(
                    submission_path,
                    _synthetic_classfile(
                        submission_internal_name,
                        {("route", submission_descriptor)},
                        class_access=_ACC_PUBLIC | _ACC_FINAL | 0x0020,
                        method_access={
                            ("route", submission_descriptor): submission_route_flags
                        },
                    ),
                )
                archive.writestr(
                    draft_applier_internal_name + ".class",
                    _synthetic_classfile(
                        draft_applier_internal_name,
                        {("apply", _SERVER_DRAFT_APPLY_DESCRIPTOR)},
                        class_access=_ACC_PUBLIC | _ACC_INTERFACE | _ACC_ABSTRACT,
                        method_access={
                            (
                                "apply",
                                _SERVER_DRAFT_APPLY_DESCRIPTOR,
                            ): draft_applier_method_flags
                        },
                    ),
                )
            if not omit_bridge:
                archive.writestr(
                    _ANVIL_LORE_MENU_BRIDGE_INTERNAL_NAME + ".class",
                    _synthetic_classfile(
                        _ANVIL_LORE_MENU_BRIDGE_INTERNAL_NAME,
                        {
                            (
                                "betterLore$handleServerDraft",
                                _SERVER_DRAFT_APPLY_DESCRIPTOR,
                            )
                        },
                        class_access=_ACC_PUBLIC | _ACC_INTERFACE | _ACC_ABSTRACT,
                        method_access={
                            (
                                "betterLore$handleServerDraft",
                                _SERVER_DRAFT_APPLY_DESCRIPTOR,
                            ): bridge_method_flags
                        },
                    ),
                )


def _run_self_test() -> int:
    """Exercise release and server-API contract failures without Gradle."""

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

        def server_api_errors(
            path: Path,
            server_player: str,
            *,
            loader: str | None = None,
            family_ids: tuple[str, ...] = (),
        ) -> list[str]:
            api_errors: list[str] = []
            with zipfile.ZipFile(path) as archive:
                names = set(archive.namelist())
                _validate_server_api_contract(
                    archive,
                    names,
                    api_errors,
                    path.name,
                    server_player,
                )
                _validate_server_api_runtime_support(
                    archive,
                    names,
                    api_errors,
                    path.name,
                    loader=loader,
                    family_ids=family_ids,
                )
            return api_errors

        official_player = "net/minecraft/server/level/ServerPlayer"
        intermediary_player = "net/minecraft/class_3222"
        valid_api = directory / "valid-server-api.jar"
        _write_synthetic_server_api_jar(valid_api, official_player)
        valid_api_errors = server_api_errors(valid_api, official_player)
        if valid_api_errors:
            print(
                "SELF-TEST ERROR: valid server API was rejected:\n"
                + "\n".join(valid_api_errors),
                file=sys.stderr,
            )
            return 1

        valid_intermediary_api = directory / "valid-intermediary-server-api.jar"
        _write_synthetic_server_api_jar(valid_intermediary_api, intermediary_player)
        intermediary_api_errors = server_api_errors(
            valid_intermediary_api, intermediary_player
        )
        if intermediary_api_errors:
            print(
                "SELF-TEST ERROR: valid intermediary server API was rejected:\n"
                + "\n".join(intermediary_api_errors),
                file=sys.stderr,
            )
            return 1

        missing_api = directory / "missing-server-api.jar"
        _write_synthetic_server_api_jar(
            missing_api,
            official_player,
            omit=_SERVER_API_CLASS_PREFIX + "AnvilEditorSession.class",
        )
        if not any(
            "missing stable public server API class" in error
            for error in server_api_errors(missing_api, official_player)
        ):
            print("SELF-TEST ERROR: missing server API class was not reported", file=sys.stderr)
            return 1

        relocated_api = directory / "relocated-server-api.jar"
        _write_synthetic_server_api_jar(
            relocated_api,
            official_player,
            relocated=(
                "com/reign/betterlore/compat/generated/forge/mc_test/api/server/"
                "BetterLoreServerApi.class"
            ),
        )
        if not any(
            "must not be relocated" in error
            for error in server_api_errors(relocated_api, official_player)
        ):
            print("SELF-TEST ERROR: relocated server API was not reported", file=sys.stderr)
            return 1

        missing_descriptor_api = directory / "missing-server-api-method.jar"
        get_method = (
            "get",
            "()Lcom/reign/betterlore/api/server/BetterLoreServerApi;",
        )
        _write_synthetic_server_api_jar(
            missing_descriptor_api,
            official_player,
            missing_method=(
                _SERVER_API_CLASS_PREFIX + "BetterLoreServerApis.class",
                get_method,
            ),
        )
        if not any(
            "lacks required v1 method descriptor" in error
            for error in server_api_errors(missing_descriptor_api, official_player)
        ):
            print("SELF-TEST ERROR: missing server API method was not reported", file=sys.stderr)
            return 1

        non_public_api = directory / "non-public-server-api-method.jar"
        _write_synthetic_server_api_jar(
            non_public_api,
            official_player,
            non_public=(
                _SERVER_API_CLASS_PREFIX + "BetterLoreServerApis.class",
                get_method,
            ),
        )
        if not any(
            "server API method(s) are not public" in error
            for error in server_api_errors(non_public_api, official_player)
        ):
            print("SELF-TEST ERROR: non-public server API method was not reported", file=sys.stderr)
            return 1

        too_new_api = directory / "too-new-server-api.jar"
        _write_synthetic_server_api_jar(
            too_new_api,
            official_player,
            too_new=_SERVER_API_CLASS_PREFIX + "BetterLoreServerApis$Holder.class",
        )
        if not any(
            "classfile major" in error
            for error in server_api_errors(too_new_api, official_player)
        ):
            print("SELF-TEST ERROR: too-new server API class was not reported", file=sys.stderr)
            return 1

        invalid_interface = directory / "invalid-server-api-interface.jar"
        _write_synthetic_server_api_jar(
            invalid_interface,
            official_player,
            class_access_override=(
                _SERVER_API_CLASS,
                _ACC_PUBLIC | _ACC_ABSTRACT,
            ),
        )
        if not any(
            "invalid class modifiers" in error
            for error in server_api_errors(invalid_interface, official_player)
        ):
            print("SELF-TEST ERROR: invalid API interface modifiers were not reported", file=sys.stderr)
            return 1

        invalid_record = directory / "invalid-server-api-record.jar"
        _write_synthetic_server_api_jar(
            invalid_record,
            official_player,
            class_access_override=(
                _SERVER_API_CLASS_PREFIX + "AnvilEditorDraft.class",
                _ACC_PUBLIC | 0x0020,
            ),
        )
        if not any(
            "invalid class modifiers" in error
            for error in server_api_errors(invalid_record, official_player)
        ):
            print("SELF-TEST ERROR: invalid API record modifiers were not reported", file=sys.stderr)
            return 1

        instance_get = directory / "instance-server-api-get.jar"
        _write_synthetic_server_api_jar(
            instance_get,
            official_player,
            method_access_override=(
                _SERVER_API_FACADE_CLASS,
                get_method,
                _ACC_PUBLIC,
            ),
        )
        if not any(
            "invalid static/instance modifiers" in error
            for error in server_api_errors(instance_get, official_player)
        ):
            print("SELF-TEST ERROR: instance BetterLoreServerApis.get was not reported", file=sys.stderr)
            return 1

        bad_version_flags = directory / "bad-server-api-version-flags.jar"
        _write_synthetic_server_api_jar(
            bad_version_flags,
            official_player,
            api_version_flags=_ACC_PUBLIC | _ACC_FINAL,
        )
        if not any(
            "API_VERSION must be public static final" in error
            for error in server_api_errors(bad_version_flags, official_player)
        ):
            print("SELF-TEST ERROR: invalid API_VERSION modifiers were not reported", file=sys.stderr)
            return 1

        bad_version_value = directory / "bad-server-api-version-value.jar"
        _write_synthetic_server_api_jar(
            bad_version_value,
            official_player,
            api_version_value=2,
        )
        if not any(
            "API_VERSION constant" in error
            for error in server_api_errors(bad_version_value, official_player)
        ):
            print("SELF-TEST ERROR: invalid API_VERSION value was not reported", file=sys.stderr)
            return 1

        enum_path = _SERVER_API_CLASS_PREFIX + "AnvilEditorDraftResult$Status.class"
        missing_enum_constant = directory / "missing-server-api-enum-constant.jar"
        _write_synthetic_server_api_jar(
            missing_enum_constant,
            official_player,
            omit_field=(
                enum_path,
                "STALE_SESSION",
                f"L{enum_path[:-6]};",
            ),
        )
        if not any(
            "lacks constant field STALE_SESSION" in error
            for error in server_api_errors(missing_enum_constant, official_player)
        ):
            print("SELF-TEST ERROR: missing API enum constant was not reported", file=sys.stderr)
            return 1

        non_public_enum_constant = directory / "non-public-server-api-enum-constant.jar"
        _write_synthetic_server_api_jar(
            non_public_enum_constant,
            official_player,
            field_access_override=(
                enum_path,
                "STALE_SESSION",
                f"L{enum_path[:-6]};",
                _ACC_PRIVATE | _ACC_STATIC | _ACC_FINAL | _ACC_ENUM,
            ),
        )
        if not any(
            "enum constant" in error and "invalid modifiers" in error
            for error in server_api_errors(non_public_enum_constant, official_player)
        ):
            print("SELF-TEST ERROR: inaccessible API enum constant was not reported", file=sys.stderr)
            return 1

        public_holder = directory / "public-server-api-holder.jar"
        _write_synthetic_server_api_jar(
            public_holder,
            official_player,
            holder_inner_flags=_ACC_PUBLIC | _ACC_STATIC | _ACC_FINAL,
        )
        if not any(
            "must remain private static final" in error
            for error in server_api_errors(public_holder, official_player)
        ):
            print("SELF-TEST ERROR: public API Holder was not reported", file=sys.stderr)
            return 1

        wrong_signature = directory / "wrong-server-api-generic-signature.jar"
        _write_synthetic_server_api_jar(
            wrong_signature,
            official_player,
            session_signature=(
                f"(L{official_player};)Ljava/util/Optional<Ljava/lang/String;>;"
            ),
        )
        if not any(
            "currentAnvilSession generic Signature" in error
            for error in server_api_errors(wrong_signature, official_player)
        ):
            print("SELF-TEST ERROR: wrong currentAnvilSession generic payload was not reported", file=sys.stderr)
            return 1

        missing_implementation = directory / "missing-server-api-implementation.jar"
        _write_synthetic_server_api_jar(
            missing_implementation,
            official_player,
            include_runtime=False,
        )
        if not any(
            "missing server API exact implementation" in error
            for error in server_api_errors(missing_implementation, official_player)
        ):
            print("SELF-TEST ERROR: missing server API implementation was not reported", file=sys.stderr)
            return 1

        private_constructor = directory / "private-server-api-constructor.jar"
        _write_synthetic_server_api_jar(
            private_constructor,
            official_player,
            implementation_constructor_flags=_ACC_PRIVATE,
        )
        if not any(
            "lacks a public instance no-argument constructor" in error
            for error in server_api_errors(private_constructor, official_player)
        ):
            print("SELF-TEST ERROR: private server API constructor was not reported", file=sys.stderr)
            return 1

        wrong_contract = directory / "wrong-server-api-interface.jar"
        _write_synthetic_server_api_jar(
            wrong_contract,
            official_player,
            implementation_interfaces=(),
        )
        if not any(
            "does not directly implement" in error
            for error in server_api_errors(wrong_contract, official_player)
        ):
            print("SELF-TEST ERROR: wrong server API implementation contract was not reported", file=sys.stderr)
            return 1

        missing_validator = directory / "missing-server-api-validator.jar"
        _write_synthetic_server_api_jar(
            missing_validator,
            official_player,
            omit_validator=True,
        )
        if not any(
            "calls missing validator" in error
            for error in server_api_errors(missing_validator, official_player)
        ):
            print("SELF-TEST ERROR: missing server API validator was not reported", file=sys.stderr)
            return 1

        instance_validator = directory / "instance-server-api-validator.jar"
        _write_synthetic_server_api_jar(
            instance_validator,
            official_player,
            validator_method_flags=0,
        )
        if not any(
            "validator" in error and "is not callable" in error
            for error in server_api_errors(instance_validator, official_player)
        ):
            print("SELF-TEST ERROR: instance server API validator was not reported", file=sys.stderr)
            return 1

        missing_submission = directory / "missing-server-api-submission.jar"
        _write_synthetic_server_api_jar(
            missing_submission,
            official_player,
            omit_submission=True,
        )
        if not any(
            "calls missing submission router" in error
            for error in server_api_errors(missing_submission, official_player)
        ):
            print("SELF-TEST ERROR: missing submission router was not reported", file=sys.stderr)
            return 1

        private_route = directory / "private-server-api-route.jar"
        _write_synthetic_server_api_jar(
            private_route,
            official_player,
            submission_route_flags=_ACC_PRIVATE | _ACC_STATIC,
        )
        if not any(
            "lacks callable public static route" in error
            for error in server_api_errors(private_route, official_player)
        ):
            print("SELF-TEST ERROR: inaccessible submission route was not reported", file=sys.stderr)
            return 1

        private_applier = directory / "private-server-api-applier.jar"
        _write_synthetic_server_api_jar(
            private_applier,
            official_player,
            draft_applier_method_flags=_ACC_ABSTRACT,
        )
        if not any(
            "DraftApplier" in error and "lacks public instance apply" in error
            for error in server_api_errors(private_applier, official_player)
        ):
            print("SELF-TEST ERROR: inaccessible DraftApplier was not reported", file=sys.stderr)
            return 1

        private_bridge = directory / "private-server-api-bridge.jar"
        _write_synthetic_server_api_jar(
            private_bridge,
            official_player,
            bridge_method_flags=_ACC_ABSTRACT,
        )
        if not any(
            "bridge" in error and "lacks public instance" in error
            for error in server_api_errors(private_bridge, official_player)
        ):
            print("SELF-TEST ERROR: inaccessible anvil bridge member was not reported", file=sys.stderr)
            return 1

        generated_impl = _generated_runtime_class_path(
            "forge", "mc_test", _SERVER_API_IMPLEMENTATION_CLASS
        )
        generated_validator = _generated_runtime_class_path(
            "forge", "mc_test", _SERVER_API_VALIDATOR_CLASS
        )
        valid_generated = directory / "valid-generated-server-api.jar"
        _write_synthetic_server_api_jar(
            valid_generated,
            official_player,
            implementation_path=generated_impl,
            validator_path=generated_validator,
        )
        valid_generated_errors = server_api_errors(
            valid_generated,
            official_player,
            loader="forge",
            family_ids=("mc_test",),
        )
        if valid_generated_errors:
            print(
                "SELF-TEST ERROR: valid generated server API was rejected:\n"
                + "\n".join(valid_generated_errors),
                file=sys.stderr,
            )
            return 1

        inaccessible_validator = directory / "inaccessible-server-api-validator.jar"
        _write_synthetic_server_api_jar(
            inaccessible_validator,
            official_player,
            implementation_path=generated_impl,
            validator_path=_SERVER_API_VALIDATOR_CLASS,
        )
        if not any(
            "cross-relocated server API validator" in error and "is not public" in error
            for error in server_api_errors(
                inaccessible_validator,
                official_player,
                loader="forge",
                family_ids=("mc_test",),
            )
        ):
            print("SELF-TEST ERROR: inaccessible cross-relocated validator was not reported", file=sys.stderr)
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
