#!/usr/bin/env python3
"""Collect exactly the Better Lore release artifacts from Stonecutter outputs."""

from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import hashlib
from io import BytesIO
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile
import zipfile


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
from verify_matrix import Artifact, ROOT, declared_artifacts, validate_matrix
from verify_release_jars import _classfile_declaration, _classfile_member_references


RELEASE_DIRECTORY_NAME = "release"
MANIFEST_NAME = "manifest.json"
SUMMARY_NAME = "SUMMARY.txt"
_DEVELOPMENT_SUFFIXES = ("-dev.jar", "_dev.jar", ".dev.jar")
_DOCUMENTATION_SUFFIXES = ("-sources.jar", "-javadoc.jar")


class CollectionError(RuntimeError):
    """Raised when the build outputs cannot prove a complete release set."""


@dataclass(frozen=True)
class SourceProof:
    path: str
    sha256: str


@dataclass(frozen=True)
class CollectedArtifact:
    loader: str
    minecraft_versions: tuple[str, ...]
    strategy: str
    file: str
    verified_sources: tuple[SourceProof, ...]
    bytes: int
    sha256: str


def _archive_name(artifact: Artifact, mod_version: str) -> str:
    return f"better-lore-{artifact.loader}-{artifact.minecraft}-{mod_version}.jar"


def _library_directory(root: Path, artifact: Artifact) -> Path:
    return root / artifact.loader / "versions" / artifact.minecraft / "build" / "libs"


def _is_development_jar(name: str) -> bool:
    return name.lower().endswith(_DEVELOPMENT_SUFFIXES)


def _is_documentation_jar(name: str) -> bool:
    return name.lower().endswith(_DOCUMENTATION_SUFFIXES)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _toml_assignment(text: str, key: str) -> str | None:
    match = re.search(rf'^\s*{re.escape(key)}\s*=\s*"([^"]*)"\s*$', text, re.MULTILINE)
    return match.group(1) if match else None


def _validate_jar_metadata(path: Path, artifact: Artifact, mod_id: str, mod_version: str) -> None:
    """Reject an unrelated, malformed, or unprocessed candidate jar."""

    try:
        with zipfile.ZipFile(path) as archive:
            names = set(archive.namelist())
            if "META-INF/MANIFEST.MF" not in names:
                raise CollectionError(f"{path}: missing META-INF/MANIFEST.MF")

            if artifact.loader == "fabric":
                descriptor = "fabric.mod.json"
                if descriptor not in names:
                    raise CollectionError(f"{path}: missing {descriptor}")
                payload = json.loads(archive.read(descriptor).decode("utf-8"))
                if payload.get("id") != mod_id:
                    raise CollectionError(
                        f"{path}: fabric.mod.json id is '{payload.get('id')}', expected '{mod_id}'"
                    )
                if str(payload.get("version")) != mod_version:
                    raise CollectionError(
                        f"{path}: fabric.mod.json version is '{payload.get('version')}', expected '{mod_version}'"
                    )
                return

            descriptor = (
                "META-INF/mods.toml"
                if artifact.loader == "forge"
                else "META-INF/neoforge.mods.toml"
            )
            if descriptor not in names:
                raise CollectionError(f"{path}: missing {descriptor}")
            payload = archive.read(descriptor).decode("utf-8")
            actual_id = _toml_assignment(payload, "modId")
            actual_version = _toml_assignment(payload, "version")
            if actual_id != mod_id:
                raise CollectionError(f"{path}: {descriptor} modId is '{actual_id}', expected '{mod_id}'")
            if actual_version != mod_version:
                raise CollectionError(
                    f"{path}: {descriptor} version is '{actual_version}', expected '{mod_version}'"
                )
    except (OSError, UnicodeDecodeError, json.JSONDecodeError, zipfile.BadZipFile) as error:
        raise CollectionError(f"{path}: invalid release jar ({error})") from error


def _select_artifact(
    root: Path,
    artifact: Artifact,
    mod_version: str,
    mod_id: str,
) -> tuple[Path, tuple[str, ...]]:
    """Find one and only one remapped/reobfuscated jar for a target."""

    library_directory = _library_directory(root, artifact)
    expected_name = _archive_name(artifact, mod_version)
    expected_stem = expected_name[:-4]
    if not library_directory.is_dir():
        raise CollectionError(f"{artifact.loader} {artifact.minecraft}: missing output directory {library_directory}")

    jars = sorted(path for path in library_directory.glob("*.jar") if path.is_file())
    expected = library_directory / expected_name
    development_jars = tuple(path for path in jars if _is_development_jar(path.name))
    documentation_jars = tuple(path for path in jars if _is_documentation_jar(path.name))

    expected_development_names = {
        (expected_stem + suffix).lower() for suffix in _DEVELOPMENT_SUFFIXES
    }
    allowed_development = [
        path for path in development_jars if path.name.lower() in expected_development_names
    ]
    unexpected_development = [path for path in development_jars if path not in allowed_development]
    expected_documentation_names = {
        (expected_stem + suffix).lower() for suffix in _DOCUMENTATION_SUFFIXES
    }
    allowed_documentation = [
        path for path in documentation_jars if path.name.lower() in expected_documentation_names
    ]
    unexpected_documentation = [path for path in documentation_jars if path not in allowed_documentation]
    release_candidates = [
        path
        for path in jars
        if not _is_development_jar(path.name) and not _is_documentation_jar(path.name)
    ]
    unexpected_release = [path for path in release_candidates if path != expected]

    errors: list[str] = []
    if unexpected_development:
        errors.append(
            "unexpected development jar(s): "
            + ", ".join(path.name for path in unexpected_development)
        )
    if unexpected_documentation:
        errors.append(
            "unexpected documentation jar(s): "
            + ", ".join(path.name for path in unexpected_documentation)
        )
    if unexpected_release:
        errors.append(
            "duplicate or unexpected release jar(s): "
            + ", ".join(path.name for path in unexpected_release)
        )
    if not expected.is_file():
        if allowed_development:
            errors.append(
                "only development jar(s) were produced; the remapped/reobfuscated release jar "
                f"'{expected_name}' is missing"
            )
        else:
            errors.append(f"missing release jar '{expected_name}'")
    elif expected.is_symlink():
        errors.append(f"release jar '{expected_name}' must not be a symlink")

    if errors:
        raise CollectionError(f"{artifact.loader} {artifact.minecraft}: " + "; ".join(errors))

    _validate_jar_metadata(expected, artifact, mod_id, mod_version)
    return expected, tuple(path.name for path in allowed_development)


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
_MANIFEST = "META-INF/MANIFEST.MF"
_MOD_CLASS_PREFIX = "com/reign/betterlore/"
_SERVER_API_CLASS_PREFIX = "com/reign/betterlore/api/server/"
_GENERATED_CLASS_PREFIXES = (
    "com/reign/betterlore/compat/generated/",
    "com/reign/betterlore/mixin/generated/",
)
_JEI_PLUGIN_PREFIX = "com/reign/betterlore/compat/jei/BetterLoreJeiPlugin"
_NETWORK_SERVICE_NAMES = frozenset(
    {
        "META-INF/services/com.reign.betterlore.client.net.BetterLoreClientNetworkingPlatform",
        "META-INF/services/com.reign.betterlore.net.BetterLoreNetworkingPlatform",
    }
)
_SHARED_COMPATIBILITY_CLASSES = frozenset(
    {
        "com/reign/betterlore/compat/CompatibilityRuntime.class",
        "com/reign/betterlore/compat/CompatibilityRuntime$Selection.class",
        "com/reign/betterlore/compat/BetterLoreMixinPlugin.class",
        "com/reign/betterlore/mixin/compat/BetterLoreJeiPluginCompatibilityMixin.class",
        "com/reign/betterlore/fabric/BetterLoreFabricBootstrap.class",
        "com/reign/betterlore/fabric/BetterLoreFabricClientBootstrap.class",
        "com/reign/betterlore/forge/BetterLoreForgeBootstrap.class",
        "com/reign/betterlore/neoforge/BetterLoreNeoForgeBootstrap.class",
    }
)


def _zip_info(name: str, *, stored: bool = False) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
    info.create_system = 3
    info.external_attr = 0o100644 << 16
    return info


def _functional_payload(path: Path, loader: str) -> dict[str, str]:
    ignored = {_MANIFEST, "fabric.mod.json"} if loader == "fabric" else {
        _MANIFEST,
        "pack.mcmeta",
        "META-INF/mods.toml" if loader == "forge" else "META-INF/neoforge.mods.toml",
    }
    with zipfile.ZipFile(path) as archive:
        return {
            info.filename: hashlib.sha256(archive.read(info)).hexdigest()
            for info in archive.infolist()
            if not info.is_dir()
            and info.filename not in ignored
            and info.filename not in _FORBIDDEN_RUNTIME_ASSETS
        }


def _manifest_attribute(path: Path, key: str) -> str | None:
    with zipfile.ZipFile(path) as archive:
        try:
            text = archive.read(_MANIFEST).decode("utf-8")
        except KeyError:
            return None
    match = re.search(rf"^{re.escape(key)}:\s*(.+?)\s*$", text, re.MULTILINE)
    return match.group(1) if match else None


def _assert_family_payload(
    loader: str,
    versions: tuple[str, ...],
    selected: dict[tuple[str, str], Path],
) -> None:
    anchor = selected[(loader, versions[0])]
    expected = _functional_payload(anchor, loader)
    for version in versions[1:]:
        candidate = selected[(loader, version)]
        actual = _functional_payload(candidate, loader)
        if actual == expected:
            continue
        changed = sorted(
            name
            for name in set(expected) | set(actual)
            if expected.get(name) != actual.get(name)
        )
        rendered = ", ".join(changed[:8])
        if len(changed) > 8:
            rendered += ", ..."
        raise CollectionError(
            f"{loader} {version} cannot share {versions[0]}'s artifact; "
            f"functional archive entries differ: {rendered}"
        )

    if loader == "fabric":
        namespaces = {
            _manifest_attribute(selected[(loader, version)], "Fabric-Mapping-Namespace")
            for version in versions
        }
        if len(namespaces) != 1 or None in namespaces:
            raise CollectionError(
                f"fabric {version_label(versions)} has inconsistent mapping namespaces: {sorted(str(v) for v in namespaces)}"
            )


def _rewrite_archive(
    source: Path,
    replacements: dict[str, bytes],
    *,
    fabric_inner: bool = False,
) -> bytes:
    output = BytesIO()
    replaced: set[str] = set()
    with zipfile.ZipFile(source) as incoming, zipfile.ZipFile(
        output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9
    ) as outgoing:
        seen: set[str] = set()
        for info in incoming.infolist():
            name = info.filename
            if info.is_dir() or name in seen or name in _FORBIDDEN_RUNTIME_ASSETS:
                continue
            # The public Fabric container owns the one shared icon.  Preserve
            # every other implementation asset so future textures, models, or
            # data files cannot disappear merely because this is a nested jar.
            if fabric_inner and name == "assets/better_lore/icon.jpg":
                continue
            seen.add(name)
            data = replacements.get(name)
            if data is None:
                data = incoming.read(info)
            else:
                replaced.add(name)
            outgoing.writestr(_zip_info(name), data)
        missing = sorted(set(replacements) - replaced)
        if missing:
            raise CollectionError(
                f"{source}: cannot replace missing archive entries: {', '.join(missing)}"
            )
    return output.getvalue()


def _constant_pool_layout(data: bytes) -> tuple[list[str | None], list[int], int]:
    """Return UTF-8 values, CONSTANT_Class name indexes, and pool end offset."""

    if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
        raise CollectionError("invalid Java classfile")
    count = int.from_bytes(data[8:10], "big")
    utf8: list[str | None] = [None] * count
    class_names: list[int] = []
    offset = 10
    index = 1
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
    while index < count:
        if offset >= len(data):
            raise CollectionError("truncated Java constant pool")
        tag = data[offset]
        offset += 1
        if tag == 1:
            if offset + 2 > len(data):
                raise CollectionError("truncated Java UTF-8 constant")
            length = int.from_bytes(data[offset : offset + 2], "big")
            offset += 2
            raw = data[offset : offset + length]
            if len(raw) != length:
                raise CollectionError("truncated Java UTF-8 payload")
            utf8[index] = raw.decode("utf-8", errors="surrogateescape")
            offset += length
        else:
            size = fixed_sizes.get(tag)
            if size is None or offset + size > len(data):
                raise CollectionError(f"unsupported or truncated Java constant-pool tag {tag}")
            if tag == 7:
                class_names.append(int.from_bytes(data[offset : offset + 2], "big"))
            offset += size
            if tag in (5, 6):
                index += 1
        index += 1
    return utf8, class_names, offset


def _class_references(data: bytes) -> set[str]:
    utf8, class_names, _ = _constant_pool_layout(data)
    references: set[str] = set()
    for name_index in class_names:
        if not 0 < name_index < len(utf8):
            continue
        value = utf8[name_index]
        if value is None:
            continue
        if value.startswith("["):
            references.update(re.findall(r"L([^;]+);", value))
        else:
            references.add(value)

    # Method/field descriptors and generic signatures normally live only in
    # CONSTANT_Utf8 entries, not CONSTANT_Class entries.  They must participate
    # in the relocation closure too: otherwise an interface can remain in the
    # original package while its payload types move, or a relocated class can
    # lose access to a package-private implementation.  Stop at either a
    # descriptor terminator or a generic-type opener so nested signatures are
    # collected without swallowing their type arguments.
    for value in utf8:
        if value is None:
            continue
        references.update(
            re.findall(r"L([A-Za-z0-9_$/]+)(?=[;<])", value)
        )
    return references


def _rewrite_class_names(data: bytes, mapping: dict[str, str]) -> bytes:
    """Relocate class references by rewriting every matching UTF-8 constant."""

    _constant_pool_layout(data)
    count = int.from_bytes(data[8:10], "big")
    output = bytearray(data[:10])
    offset = 10
    index = 1
    replacements = tuple(
        (old, new, old.replace("/", "."), new.replace("/", "."))
        for old, new in sorted(mapping.items(), key=lambda entry: len(entry[0]), reverse=True)
    )
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
    while index < count:
        tag = data[offset]
        output.append(tag)
        offset += 1
        if tag == 1:
            length = int.from_bytes(data[offset : offset + 2], "big")
            offset += 2
            value = data[offset : offset + length].decode("utf-8", errors="surrogateescape")
            offset += length
            for old, new, dotted_old, dotted_new in replacements:
                value = value.replace(old, new).replace(dotted_old, dotted_new)
            encoded = value.encode("utf-8", errors="surrogateescape")
            if len(encoded) > 0xFFFF:
                raise CollectionError("relocated Java UTF-8 constant exceeds 65535 bytes")
            output.extend(len(encoded).to_bytes(2, "big"))
            output.extend(encoded)
        else:
            size = fixed_sizes[tag]
            output.extend(data[offset : offset + size])
            offset += size
            if tag in (5, 6):
                index += 1
        index += 1
    output.extend(data[offset:])
    return bytes(output)


def _adapter_family_id(family: tuple[str, ...]) -> str:
    label = version_label(family).replace(".", "_").replace("-", "_")
    return "mc" + label


def _relocated_internal_name(loader: str, family: tuple[str, ...], internal_name: str) -> str:
    family_id = _adapter_family_id(family)
    mixin_prefix = "com/reign/betterlore/mixin/"
    if internal_name.startswith(mixin_prefix):
        relative = internal_name[len(mixin_prefix) :]
        return f"{mixin_prefix}generated/{loader}/{family_id}/{relative}"
    if not internal_name.startswith(_MOD_CLASS_PREFIX):
        raise CollectionError(f"cannot relocate non-mod class {internal_name}")
    relative = internal_name[len(_MOD_CLASS_PREFIX) :]
    return (
        f"com/reign/betterlore/compat/generated/{loader}/{family_id}/{relative}"
    )


def _family_archives(
    artifact: PublishedArtifact,
    selected: dict[tuple[str, str], Path],
) -> list[tuple[tuple[str, ...], Path, dict[str, bytes]]]:
    _assert_flat_resource_payload(artifact, selected)
    families = [
        family
        for family in FAMILIES_BY_LOADER[artifact.loader]
        if all(version in artifact.versions for version in family)
    ]
    covered = tuple(version for family in families for version in family)
    if covered != artifact.versions:
        raise CollectionError(
            f"{artifact.loader} {artifact.label}: binary families cover {covered}, expected {artifact.versions}"
        )
    result: list[tuple[tuple[str, ...], Path, dict[str, bytes]]] = []
    for family in families:
        _assert_family_payload(artifact.loader, family, selected)
        source = selected[(artifact.loader, family[0])]
        with zipfile.ZipFile(source) as archive:
            classes = {
                info.filename: archive.read(info)
                for info in archive.infolist()
                if not info.is_dir()
                and info.filename.startswith(_MOD_CLASS_PREFIX)
                and info.filename.endswith(".class")
            }
        result.append((family, source, classes))
    return result


def _flat_resource_payload(path: Path, loader: str) -> dict[str, str]:
    """Hash resources that a flat adapter copies verbatim from its anchor."""

    descriptor = "META-INF/mods.toml" if loader == "forge" else "META-INF/neoforge.mods.toml"
    ignored = {
        _MANIFEST,
        "pack.mcmeta",
        descriptor,
        *_NETWORK_SERVICE_NAMES,
        *_FORBIDDEN_RUNTIME_ASSETS,
    }
    with zipfile.ZipFile(path) as archive:
        return {
            info.filename: hashlib.sha256(archive.read(info)).hexdigest()
            for info in archive.infolist()
            if not info.is_dir()
            and not info.filename.endswith(".class")
            and info.filename not in ignored
        }


def _assert_flat_resource_payload(
    artifact: PublishedArtifact,
    selected: dict[tuple[str, str], Path],
) -> None:
    """Reject family resources/configuration that the flat adapter cannot merge."""

    anchor = selected[(artifact.loader, artifact.versions[0])]
    expected = _flat_resource_payload(anchor, artifact.loader)
    for version in artifact.versions[1:]:
        candidate = selected[(artifact.loader, version)]
        actual = _flat_resource_payload(candidate, artifact.loader)
        if actual == expected:
            continue
        changed = sorted(
            name
            for name in set(expected) | set(actual)
            if expected.get(name) != actual.get(name)
        )
        rendered = ", ".join(changed[:8])
        if len(changed) > 8:
            rendered += ", ..."
        raise CollectionError(
            f"{artifact.loader} {artifact.label}: {version} has resources that differ "
            f"from {artifact.versions[0]} and cannot be copied safely: {rendered}"
        )


def _package_access_dependencies(classes: dict[str, bytes]) -> dict[str, set[str]]:
    """Find same-package relationships that must survive a caller's relocation.

    Public facades used by shared entrypoints (including JEI) must remain shared
    when their callers only use public members. Class/member declarations use
    the same classfile decoder as the independent post-packaging access check.
    """
    declarations = {path[:-6]: _classfile_declaration(data) for path, data in classes.items()}

    def resolve(owner: str, key: tuple[str, str], fields: bool, seen: set[str]):
        if owner in seen or owner not in declarations:
            return None
        seen.add(owner)
        declaration = declarations[owner]
        table = declaration.fields if fields else declaration.methods
        if key in table:
            value = table[key]
            return owner, value.access_flags if fields else value
        if key[0] == "<init>":
            return None
        parents = declaration.interfaces + (declaration.super_class,) if fields else (declaration.super_class,) + declaration.interfaces
        for parent in parents:
            resolved = resolve(parent, key, fields, seen) if parent else None
            if resolved is not None:
                return resolved
        return None

    dependencies: dict[str, set[str]] = {}
    for path, data in classes.items():
        package = path.rpartition("/")[0]
        required = {
            reference + ".class" for reference in _class_references(data)
            if reference in declarations and reference.rpartition("/")[0] == package
            and not declarations[reference].access_flags & 0x0001
        }
        for fields in (False, True):
            for owner, name, descriptor in _classfile_member_references(data, fields=fields):
                resolved = resolve(owner, (name, descriptor), fields, set())
                if resolved is not None:
                    declaring, access = resolved
                    if declaring.rpartition("/")[0] == package and not access & 0x0001:
                        required.add(declaring + ".class")
        dependencies[path] = required
    return dependencies


def _variant_class_paths(
    archives: list[tuple[tuple[str, ...], Path, dict[str, bytes]]],
    loader: str,
) -> set[str]:
    special = set(_SHARED_COMPATIBILITY_CLASSES)
    all_paths = set().union(*(set(classes) for _, _, classes in archives))
    protected_special = sorted(
        path for path in special if path.startswith(_SERVER_API_CLASS_PREFIX)
    )
    if protected_special:
        raise CollectionError(
            f"{loader}: public server API class(es) must not use the shared-class "
            "escape hatch: "
            + ", ".join(protected_special)
        )

    differing_server_api = sorted(
        path
        for path in all_paths
        if path.startswith(_SERVER_API_CLASS_PREFIX)
        and len(
            {
                hashlib.sha256(classes[path]).hexdigest() if path in classes else None
                for _, _, classes in archives
            }
        )
        > 1
    )
    if differing_server_api:
        raise CollectionError(
            f"{loader}: public server API class(es) differ or are missing between "
            "retained binary families: "
            + ", ".join(differing_server_api)
        )

    plugin_paths = {path for path in all_paths if path.startswith(_JEI_PLUGIN_PREFIX)}
    candidates = all_paths - special - plugin_paths
    variants = {
        path
        for path in candidates
        if len(
            {
                hashlib.sha256(classes[path]).hexdigest() if path in classes else None
                for _, _, classes in archives
            }
        )
        > 1
    }
    implementation_suffix = (
        "forge/BetterLoreForgeMod.class"
        if loader == "forge"
        else "neoforge/BetterLoreNeoForgeMod.class"
    )
    variants.update(path for path in candidates if path.endswith(implementation_suffix))

    references_by_path = {
        path: set().union(
            *(_class_references(classes[path]) for _, _, classes in archives if path in classes)
        )
        for path in candidates
    }
    package_dependencies_by_path: dict[str, set[str]] = {}
    for _, _, classes in archives:
        for path, dependencies in _package_access_dependencies(classes).items():
            package_dependencies_by_path.setdefault(path, set()).update(dependencies)
    changed = True
    while changed:
        changed = False
        variant_names = {path[:-6] for path in variants}
        for path in sorted(candidates - variants):
            references = references_by_path[path]
            if references & variant_names:
                variants.add(path)
                changed = True

        # Following only incoming edges strands package-private helpers such as
        # LegacyGradientColors. Also follow outgoing edges requiring package
        # access, without moving public-only facades consumed by shared code.
        package_dependencies = {
            dependency
            for path in variants
            for dependency in package_dependencies_by_path[path]
            if dependency in candidates - variants
        }
        if package_dependencies:
            variants.update(package_dependencies)
            changed = True

        outer_names = {path[:-6].split("$", 1)[0] for path in variants}
        nestmates = {
            path
            for path in candidates - variants
            if path[:-6].split("$", 1)[0] in outer_names
        }
        if nestmates:
            variants.update(nestmates)
            changed = True

    variant_server_api = sorted(
        path for path in variants if path.startswith(_SERVER_API_CLASS_PREFIX)
    )
    if variant_server_api:
        raise CollectionError(
            f"{loader}: public server API class(es) vary between retained binary "
            "families and would be relocated: "
            + ", ".join(variant_server_api)
        )
    return variants


def _generated_server_api_paths(paths: set[str]) -> list[str]:
    """Return public server API classes hidden below a generated adapter tree."""

    return sorted(
        path
        for path in paths
        if path.endswith(".class")
        and path.startswith(_GENERATED_CLASS_PREFIXES)
        and "/api/server/" in path
    )


def _mixin_configuration(
    source: Path,
    loader: str,
    archives: list[tuple[tuple[str, ...], Path, dict[str, bytes]]],
    variant_paths: set[str],
) -> bytes:
    with zipfile.ZipFile(source) as archive:
        descriptor = json.loads(archive.read("better_lore.mixins.json").decode("utf-8"))
    original_package = descriptor["package"]
    descriptor["plugin"] = "com.reign.betterlore.compat.BetterLoreMixinPlugin"
    for side in ("mixins", "client", "server"):
        entries = descriptor.get(side)
        if not isinstance(entries, list):
            continue
        rewritten: list[str] = []
        for entry in entries:
            internal = original_package.replace(".", "/") + "/" + entry.replace(".", "/")
            path = internal + ".class"
            if path not in variant_paths:
                rewritten.append(entry)
                continue
            for family, _, classes in archives:
                if path not in classes:
                    continue
                relocated = _relocated_internal_name(loader, family, internal)
                prefix = original_package.replace(".", "/") + "/"
                rewritten.append(relocated[len(prefix) :].replace("/", "."))
        descriptor[side] = rewritten
    return (json.dumps(descriptor, indent=2) + "\n").encode("utf-8")


def _manifest_with_mixin_config(source: Path) -> bytes:
    with zipfile.ZipFile(source) as archive:
        text = archive.read(_MANIFEST).decode("utf-8").replace("\r\n", "\n")
    lines = text.splitlines()
    separator = next((index for index, line in enumerate(lines) if not line), len(lines))
    main = [
        line
        for line in lines[:separator]
        if not line.startswith("MixinConfigs:")
    ]
    remainder = lines[separator + 1 :] if separator < len(lines) else []
    while remainder and not remainder[-1]:
        remainder.pop()

    main.append("MixinConfigs: better_lore.mixins.json")
    rewritten = main + [""]
    if remainder:
        rewritten.extend(remainder)
        rewritten.append("")
    return ("\r\n".join(rewritten) + "\r\n").encode("utf-8")


def _skip_member(data: bytes, offset: int) -> int:
    if offset + 8 > len(data):
        raise CollectionError("truncated Java class member")
    attribute_count = int.from_bytes(data[offset + 6 : offset + 8], "big")
    offset += 8
    for _ in range(attribute_count):
        if offset + 6 > len(data):
            raise CollectionError("truncated Java class attribute")
        length = int.from_bytes(data[offset + 2 : offset + 6], "big")
        offset += 6 + length
        if offset > len(data):
            raise CollectionError("truncated Java class attribute payload")
    return offset


def _add_identifier_jei_method(data: bytes) -> bytes:
    """Add JEI's post-1.21.10 return descriptor to the pre-rename plugin.

    Java source cannot declare two methods which differ only by return type,
    while JVM bytecode can. JEI changed IModPlugin#getPluginUid from
    ResourceLocation to Identifier without changing the interface name. This
    tiny bridge keeps one annotated plugin class valid on both sides.
    """

    utf8, _, pool_end = _constant_pool_layout(data)
    identifier_descriptor = "()Lnet/minecraft/resources/Identifier;"
    if identifier_descriptor in utf8:
        return data

    old_count = int.from_bytes(data[8:10], "big")
    additions: list[bytes] = []

    def add_utf8(value: str) -> int:
        encoded = value.encode("utf-8")
        index = old_count + len(additions)
        additions.append(b"\x01" + len(encoded).to_bytes(2, "big") + encoded)
        return index

    def add_class(name_index: int) -> int:
        index = old_count + len(additions)
        additions.append(b"\x07" + name_index.to_bytes(2, "big"))
        return index

    def add_string(value_index: int) -> int:
        index = old_count + len(additions)
        additions.append(b"\x08" + value_index.to_bytes(2, "big"))
        return index

    def add_name_and_type(name_index: int, descriptor_index: int) -> int:
        index = old_count + len(additions)
        additions.append(
            b"\x0c" + name_index.to_bytes(2, "big") + descriptor_index.to_bytes(2, "big")
        )
        return index

    def add_method_ref(class_index: int, name_and_type_index: int) -> int:
        index = old_count + len(additions)
        additions.append(
            b"\x0a" + class_index.to_bytes(2, "big") + name_and_type_index.to_bytes(2, "big")
        )
        return index

    method_name = add_utf8("getPluginUid")
    method_descriptor = add_utf8(identifier_descriptor)
    code_name = add_utf8("Code")
    identifier_name = add_utf8("net/minecraft/resources/Identifier")
    identifier_class = add_class(identifier_name)
    path_value = add_utf8("jei")
    path_string = add_string(path_value)
    factory_owner_name = add_utf8("com/reign/betterlore/net/NetworkIdentifiers")
    factory_owner = add_class(factory_owner_name)
    factory_name = add_utf8("create")
    factory_descriptor = add_utf8("(Ljava/lang/String;)Ljava/lang/Object;")
    factory_name_and_type = add_name_and_type(factory_name, factory_descriptor)
    factory_method = add_method_ref(factory_owner, factory_name_and_type)

    if path_string <= 0xFF:
        code = b"\x12" + bytes((path_string,))
    else:
        code = b"\x13" + path_string.to_bytes(2, "big")
    code += b"\xb8" + factory_method.to_bytes(2, "big")
    code += b"\xc0" + identifier_class.to_bytes(2, "big") + b"\xb0"
    code_body = (
        (1).to_bytes(2, "big")
        + (1).to_bytes(2, "big")
        + len(code).to_bytes(4, "big")
        + code
        + (0).to_bytes(2, "big")
        + (0).to_bytes(2, "big")
    )
    method = (
        (0x0001).to_bytes(2, "big")
        + method_name.to_bytes(2, "big")
        + method_descriptor.to_bytes(2, "big")
        + (1).to_bytes(2, "big")
        + code_name.to_bytes(2, "big")
        + len(code_body).to_bytes(4, "big")
        + code_body
    )

    new_count = old_count + len(additions)
    rebuilt = data[:8] + new_count.to_bytes(2, "big") + data[10:pool_end] + b"".join(additions) + data[pool_end:]
    _, _, new_pool_end = _constant_pool_layout(rebuilt)
    offset = new_pool_end + 6
    interface_count = int.from_bytes(rebuilt[offset : offset + 2], "big")
    offset += 2 + 2 * interface_count
    field_count = int.from_bytes(rebuilt[offset : offset + 2], "big")
    offset += 2
    for _ in range(field_count):
        offset = _skip_member(rebuilt, offset)
    method_count_offset = offset
    method_count = int.from_bytes(rebuilt[offset : offset + 2], "big")
    offset += 2
    for _ in range(method_count):
        offset = _skip_member(rebuilt, offset)
    return (
        rebuilt[:method_count_offset]
        + (method_count + 1).to_bytes(2, "big")
        + rebuilt[method_count_offset + 2 : offset]
        + method
        + rebuilt[offset:]
    )


def _universal_jei_classes(
    artifact: PublishedArtifact,
    archives: list[tuple[tuple[str, ...], Path, dict[str, bytes]]],
) -> dict[str, bytes]:
    candidates = [
        (family, classes)
        for family, _, classes in archives
        if _JEI_PLUGIN_PREFIX + ".class" in classes
    ]
    if not candidates:
        return {}

    if artifact.loader == "neoforge" and "1.21.9" in artifact.versions:
        selected_family, selected_classes = next(
            (family, classes)
            for family, classes in candidates
            if "1.21.9" in family
        )
    else:
        selected_family, selected_classes = candidates[-1]

    plugin = {
        path: data
        for path, data in selected_classes.items()
        if path.startswith(_JEI_PLUGIN_PREFIX)
    }
    outer_path = _JEI_PLUGIN_PREFIX + ".class"
    if artifact.loader == "neoforge" and "1.21.9" in artifact.versions:
        plugin[outer_path] = _add_identifier_jei_method(plugin[outer_path])
    return plugin


def _pack_metadata(state, versions: tuple[str, ...]) -> bytes:
    first = state.profiles[versions[0]]
    last = state.profiles[versions[-1]]
    minimum = [
        int(first["minecraft.resource_pack_format"]),
        int(first["minecraft.resource_pack_minor"]),
    ]
    maximum = [
        int(last["minecraft.resource_pack_format"]),
        int(last["minecraft.resource_pack_minor"]),
    ]
    if minimum[0] < 65 <= maximum[0]:
        raise CollectionError(
            f"Minecraft family {version_label(versions)} crosses the pack metadata "
            "format-65 boundary and cannot share one resource-pack declaration"
        )
    pack = {
        "description": "Better Lore resources",
        "pack_format": minimum[0],
        "min_format": minimum,
        "max_format": maximum,
    }
    # Minecraft rejects the legacy key outright starting with resource-pack
    # format 65.  Older versions still need it when one jar spans pack majors.
    if maximum[0] < 65:
        pack["supported_formats"] = [minimum[0], maximum[0]]
    document = {"pack": pack}
    return (json.dumps(document, indent=2) + "\n").encode("utf-8")


def _version_key(value: str) -> tuple[tuple[int, ...], int, tuple[tuple[int, object], ...]]:
    base = value.split("+", 1)[0]
    main, separator, prerelease = base.partition("-")
    numeric = tuple(int(part) for part in main.split("."))
    pre_key: list[tuple[int, object]] = []
    if separator:
        for part in prerelease.split("."):
            pre_key.append((0, int(part)) if part.isdigit() else (1, part))
    return numeric, 0 if separator else 1, tuple(pre_key)


def _minimum_version(values: list[str]) -> str:
    return min(values, key=_version_key)


def _fabric_manifest(source: Path) -> bytes:
    with zipfile.ZipFile(source) as archive:
        lines = archive.read(_MANIFEST).decode("utf-8").replace("\r\n", "\n").splitlines()
    filtered = [line for line in lines if not line.startswith("Fabric-Minecraft-Version:")]
    return ("\r\n".join(filtered).rstrip() + "\r\n\r\n").encode("utf-8")


def _fabric_candidate(
    state,
    family: tuple[str, ...],
    source: Path,
    mod_version: str,
) -> bytes:
    with zipfile.ZipFile(source) as archive:
        descriptor = json.loads(archive.read("fabric.mod.json").decode("utf-8"))
    descriptor["id"] = "better_lore_impl"
    descriptor["version"] = f"{mod_version}+mc.{version_label(family)}"
    descriptor["name"] = f"Better Lore implementation ({version_label(family)})"
    descriptor.pop("icon", None)
    dependencies = descriptor.setdefault("depends", {})
    dependencies["minecraft"] = family[0] if len(family) == 1 else list(family)
    dependencies["fabricloader"] = ">=" + _minimum_version(
        [state.profiles[version]["deps.fabric_loader"] for version in family]
    )
    dependencies["fabric-api"] = ">=" + _minimum_version(
        [state.profiles[version]["deps.fabric_api"] for version in family]
    )
    dependencies["java"] = ">=" + str(
        min(int(state.profiles[version]["java.version"]) for version in family)
    )
    replacements = {
        "fabric.mod.json": (json.dumps(descriptor, indent=2) + "\n").encode("utf-8"),
        "pack.mcmeta": _pack_metadata(state, family),
        _MANIFEST: _fabric_manifest(source),
    }
    return _rewrite_archive(source, replacements, fabric_inner=True)


def _build_fabric_bundle(
    state,
    artifact: PublishedArtifact,
    selected: dict[tuple[str, str], Path],
    target: Path,
    mod_id: str,
    mod_version: str,
) -> None:
    candidates: list[tuple[tuple[str, ...], str, bytes]] = []
    for family in FABRIC_FAMILIES:
        _assert_family_payload("fabric", family, selected)
        source = selected[("fabric", family[0])]
        path = fabric_inner_path(family)
        candidates.append((family, path, _fabric_candidate(state, family, source, mod_version)))

    first_source = selected[("fabric", artifact.versions[0])]
    with zipfile.ZipFile(first_source) as archive:
        original = json.loads(archive.read("fabric.mod.json").decode("utf-8"))
    descriptor = {
        "schemaVersion": 1,
        "id": mod_id,
        "version": mod_version,
        "name": original.get("name", "Better Lore"),
        "description": original.get("description", ""),
        "authors": original.get("authors", []),
        "license": original.get("license", "MIT"),
        "environment": "*",
        "depends": {
            "fabricloader": ">=" + _minimum_version(
                [state.profiles[version]["deps.fabric_loader"] for version in artifact.versions]
            ),
            "minecraft": list(artifact.versions),
            "java": ">=" + str(
                min(int(state.profiles[version]["java.version"]) for version in artifact.versions)
            ),
            "better_lore_impl": "*",
        },
        "jars": [{"file": path} for _, path, _ in candidates],
        "suggests": original.get("suggests", {}),
        "icon": "assets/better_lore/icon.jpg",
        "contact": original.get("contact", {}),
    }

    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        archive.writestr(_zip_info(_MANIFEST), b"Manifest-Version: 1.0\r\n\r\n")
        archive.writestr(
            _zip_info("fabric.mod.json"),
            (json.dumps(descriptor, indent=2) + "\n").encode("utf-8"),
        )
        archive.writestr(
            _zip_info("assets/better_lore/icon.jpg"),
            (ROOT / "common/src/main/resources/assets/better_lore/icon.jpg").read_bytes(),
        )
        archive.writestr(_zip_info("LICENSE"), (ROOT / "LICENSE").read_bytes())
        for _, path, data in candidates:
            # Nested jars are already compressed. Storing them avoids wasted
            # double-compression work during packaging and Loader discovery.
            archive.writestr(_zip_info(path, stored=True), data)


def _replace_minecraft_range(text: str, anchor: str, replacement: str, source: Path) -> str:
    pattern = re.compile(
        rf'(modId\s*=\s*"minecraft".*?versionRange\s*=\s*")\[{re.escape(anchor)}\](")',
        re.DOTALL,
    )
    updated, count = pattern.subn(rf"\g<1>{replacement}\g<2>", text, count=1)
    if count != 1:
        raise CollectionError(f"{source}: could not locate the exact Minecraft dependency range")
    return updated


def _build_range_artifact(
    state,
    artifact: PublishedArtifact,
    selected: dict[tuple[str, str], Path],
    target: Path,
) -> None:
    _assert_family_payload(artifact.loader, artifact.versions, selected)
    source = selected[(artifact.loader, artifact.anchor)]
    descriptor_name = (
        "META-INF/mods.toml"
        if artifact.loader == "forge"
        else "META-INF/neoforge.mods.toml"
    )
    with zipfile.ZipFile(source) as archive:
        descriptor = archive.read(descriptor_name).decode("utf-8")
    descriptor = _replace_minecraft_range(
        descriptor, artifact.anchor, artifact.maven_range, source
    )
    if 'logoFile = "assets/better_lore/icon.jpg"' not in descriptor:
        marker = 'displayURL = "https://github.com/Reign3r/betterlore"\n'
        if descriptor.count(marker) != 1:
            raise CollectionError(f"{source}: could not add the runtime icon declaration")
        descriptor = descriptor.replace(
            marker,
            marker + 'logoFile = "assets/better_lore/icon.jpg"\n',
            1,
        )
    data = _rewrite_archive(
        source,
        {
            descriptor_name: descriptor.encode("utf-8"),
            "pack.mcmeta": _pack_metadata(state, artifact.versions),
        },
    )
    target.write_bytes(data)


def _build_flat_adapter(
    state,
    artifact: PublishedArtifact,
    selected: dict[tuple[str, str], Path],
    target: Path,
) -> None:
    archives = _family_archives(artifact, selected)
    variant_paths = _variant_class_paths(archives, artifact.loader)
    source_paths = set().union(*(set(classes) for _, _, classes in archives))
    generated_source_api = _generated_server_api_paths(source_paths)
    if generated_source_api:
        raise CollectionError(
            f"{artifact.loader} {artifact.label}: public server API class(es) already "
            "exist under a generated adapter path: "
            + ", ".join(generated_source_api)
        )
    first_source = archives[0][1]
    descriptor_name = (
        "META-INF/mods.toml"
        if artifact.loader == "forge"
        else "META-INF/neoforge.mods.toml"
    )
    with zipfile.ZipFile(first_source) as archive:
        descriptor = archive.read(descriptor_name).decode("utf-8")
    descriptor = _replace_minecraft_range(
        descriptor, artifact.anchor, artifact.maven_range, first_source
    )
    if 'logoFile = "assets/better_lore/icon.jpg"' not in descriptor:
        marker = 'displayURL = "https://github.com/Reign3r/betterlore"\n'
        if descriptor.count(marker) != 1:
            raise CollectionError(f"{first_source}: could not add the runtime icon declaration")
        descriptor = descriptor.replace(
            marker,
            marker + 'logoFile = "assets/better_lore/icon.jpg"\n',
            1,
        )

    replacements = {
        descriptor_name: descriptor.encode("utf-8"),
        "pack.mcmeta": _pack_metadata(state, artifact.versions),
        "better_lore.mixins.json": _mixin_configuration(
            first_source, artifact.loader, archives, variant_paths
        ),
        _MANIFEST: _manifest_with_mixin_config(first_source),
    }
    ignored = {
        descriptor_name,
        "pack.mcmeta",
        "better_lore.mixins.json",
        _MANIFEST,
        *_NETWORK_SERVICE_NAMES,
    }
    output_entries: dict[str, bytes] = {}

    with zipfile.ZipFile(first_source) as source_archive:
        for info in source_archive.infolist():
            path = info.filename
            if info.is_dir() or path in ignored or path in _FORBIDDEN_RUNTIME_ASSETS:
                continue
            if path.startswith(_MOD_CLASS_PREFIX) and path.endswith(".class"):
                continue
            output_entries[path] = source_archive.read(info)
    output_entries.update(replacements)

    all_paths = set().union(*(set(classes) for _, _, classes in archives))
    plugin_paths = {path for path in all_paths if path.startswith(_JEI_PLUGIN_PREFIX)}
    shared_paths = all_paths - variant_paths - plugin_paths
    for path in sorted(shared_paths):
        bodies = [classes[path] for _, _, classes in archives if path in classes]
        if not bodies:
            continue
        if len(bodies) != len(archives):
            raise CollectionError(
                f"{artifact.loader} {artifact.label}: shared class {path} is absent from a binary family"
            )
        if path not in _SHARED_COMPATIBILITY_CLASSES and len(
            {hashlib.sha256(body).hexdigest() for body in bodies}
        ) != 1:
            raise CollectionError(
                f"{artifact.loader} {artifact.label}: non-identical class escaped adapter relocation: {path}"
            )
        output_entries[path] = bodies[0]

    for family, _, classes in archives:
        internal_mapping = {
            path[:-6]: _relocated_internal_name(artifact.loader, family, path[:-6])
            for path in variant_paths
            if path in classes
        }
        for path in sorted(variant_paths):
            data = classes.get(path)
            if data is None:
                continue
            relocated_internal = internal_mapping[path[:-6]]
            relocated_path = relocated_internal + ".class"
            if relocated_path in output_entries:
                raise CollectionError(
                    f"{artifact.loader} {artifact.label}: duplicate relocated class {relocated_path}"
                )
            output_entries[relocated_path] = _rewrite_class_names(data, internal_mapping)

    for path, data in _universal_jei_classes(artifact, archives).items():
        if path in output_entries:
            raise CollectionError(
                f"{artifact.loader} {artifact.label}: duplicate universal JEI class {path}"
            )
        output_entries[path] = data

    generated_output_api = _generated_server_api_paths(set(output_entries))
    if generated_output_api:
        raise CollectionError(
            f"{artifact.loader} {artifact.label}: public server API class(es) were "
            "relocated into a generated adapter path: "
            + ", ".join(generated_output_api)
        )

    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(output_entries):
            archive.writestr(_zip_info(path), output_entries[path])


def _source_proofs(
    root: Path,
    artifact: PublishedArtifact,
    selected: dict[tuple[str, str], Path],
) -> tuple[SourceProof, ...]:
    return tuple(
        SourceProof(
            path=selected[(artifact.loader, version)].relative_to(root).as_posix(),
            sha256=_sha256(selected[(artifact.loader, version)]),
        )
        for version in artifact.versions
    )


def _release_manifest(
    records: list[CollectedArtifact], excluded_development_jars: list[str]
) -> dict[str, object]:
    targets: list[dict[str, str]] = []
    by_loader_and_version = {
        (record.loader, version): record
        for record in records
        for version in record.minecraft_versions
    }
    for compile_target in declared_artifacts():
        record = by_loader_and_version[(compile_target.loader, compile_target.minecraft)]
        target = {
            "loader": compile_target.loader,
            "minecraft": compile_target.minecraft,
            "file": record.file,
        }
        if compile_target.loader == "fabric":
            family = next(
                family for family in FABRIC_FAMILIES if compile_target.minecraft in family
            )
            target["nested_candidate"] = fabric_inner_path(family)
        targets.append(target)
    return {
        "schema_version": 2,
        "generated_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "artifact_count": len(records),
        "target_count": len(targets),
        "artifacts": [asdict(record) for record in records],
        "targets": targets,
        "excluded_development_jars": excluded_development_jars,
    }


def _summary(records: list[CollectedArtifact], excluded_development_jars: list[str]) -> str:
    lines = [
        "Better Lore compatibility release summary",
        f"Artifacts: {len(records)}",
        f"Runtime targets: {sum(len(record.minecraft_versions) for record in records)}",
        "",
    ]
    lines.extend(
        f"- {record.loader} {version_label(record.minecraft_versions)}: {record.file} "
        f"({record.bytes} bytes, sha256 {record.sha256})"
        for record in records
    )
    if excluded_development_jars:
        lines.extend(("", "Excluded development intermediates:"))
        lines.extend(f"- {name}" for name in excluded_development_jars)
    return "\n".join(lines) + "\n"


def _publish(staging: Path, destination: Path) -> None:
    """Publish a fully prepared staging directory without retaining stale jars."""

    if destination.exists() or destination.is_symlink():
        if destination.is_symlink() or not destination.is_dir():
            raise CollectionError(f"release destination is not a directory: {destination}")

        # Windows can reject a directory rename even after the old destination
        # has been moved aside (for example when Explorer, an antivirus scanner,
        # or a preceding Gradle aggregate has inspected a release jar).  The
        # staging directory is already complete and validated at this point, so
        # update the existing directory in place there instead of relying on a
        # fragile rename of the whole directory.
        if os.name == "nt":
            staged_entries = {entry.name for entry in staging.iterdir()}
            for entry in staging.iterdir():
                target = destination / entry.name
                if entry.is_dir():
                    shutil.copytree(entry, target, dirs_exist_ok=True)
                else:
                    # The Prism provisioner may hardlink a published jar into
                    # several test instances.  Copying over that path would
                    # mutate every link in place.  Publish through a sibling
                    # temporary file and atomically replace only this directory
                    # entry, which deliberately breaks any existing hardlink.
                    descriptor, temporary_name = tempfile.mkstemp(
                        dir=destination,
                        prefix=f".{entry.name}.",
                        suffix=".tmp",
                    )
                    os.close(descriptor)
                    temporary = Path(temporary_name)
                    try:
                        shutil.copy2(entry, temporary)
                        os.replace(temporary, target)
                    finally:
                        temporary.unlink(missing_ok=True)
            for entry in destination.iterdir():
                if entry.name in staged_entries:
                    continue
                if entry.is_dir() and not entry.is_symlink():
                    shutil.rmtree(entry)
                else:
                    entry.unlink()
            shutil.rmtree(staging)
            return

        backup = destination.with_name(f".{destination.name}.previous")
        if backup.exists() or backup.is_symlink():
            raise CollectionError(f"stale release backup exists: {backup}")
        destination.replace(backup)
        try:
            staging.replace(destination)
        except OSError:
            backup.replace(destination)
            raise
        shutil.rmtree(backup)
    else:
        staging.replace(destination)


def collect_release_jars(root: Path = ROOT, destination: Path | None = None) -> list[CollectedArtifact]:
    """Validate all compile outputs and publish the five compatibility artifacts."""

    state = validate_matrix(root)
    if state.errors:
        raise CollectionError("matrix validation failed:\n" + "\n".join(state.errors))
    release_errors = validate_release_matrix()
    if release_errors:
        raise CollectionError("release matrix validation failed:\n" + "\n".join(release_errors))

    mod_version = state.root_properties.get("mod.version", "").strip()
    mod_id = state.root_properties.get("mod.id", "").strip()
    if not mod_version or any(character in mod_version for character in "/\\"):
        raise CollectionError("gradle.properties: mod.version is missing or unsafe for an archive filename")
    if not mod_id:
        raise CollectionError("gradle.properties: mod.id is required for release metadata verification")

    selected: dict[tuple[str, str], Path] = {}
    excluded_development_jars: list[str] = []
    errors: list[str] = []
    for artifact in declared_artifacts():
        try:
            path, development_jars = _select_artifact(root, artifact, mod_version, mod_id)
        except CollectionError as error:
            errors.append(str(error))
            continue
        selected[(artifact.loader, artifact.minecraft)] = path
        excluded_development_jars.extend(
            f"{artifact.loader}/{artifact.minecraft}/{name}" for name in development_jars
        )

    if errors:
        raise CollectionError("release collection failed:\n" + "\n".join(errors))
    if len(selected) != len(declared_artifacts()):
        raise CollectionError(
            f"release collection selected {len(selected)} artifacts, expected {len(declared_artifacts())}"
        )

    build_directory = root / "build"
    destination = destination or build_directory / RELEASE_DIRECTORY_NAME
    if not destination.is_absolute():
        destination = root / destination
    expected_destination = build_directory / RELEASE_DIRECTORY_NAME
    if destination != expected_destination:
        raise CollectionError(f"release destination must be {expected_destination}")
    build_directory.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=".release-staging-", dir=build_directory))

    try:
        records: list[CollectedArtifact] = []
        for artifact in published_artifacts():
            target = staging / artifact.file_name(mod_version)
            if artifact.strategy == "fabric_bundle":
                _build_fabric_bundle(
                    state, artifact, selected, target, mod_id, mod_version
                )
            elif artifact.strategy == "flat_adapter":
                _build_flat_adapter(state, artifact, selected, target)
            else:
                raise CollectionError(
                    f"unsupported release strategy {artifact.strategy!r} for {artifact.loader}"
                )
            records.append(
                CollectedArtifact(
                    loader=artifact.loader,
                    minecraft_versions=artifact.versions,
                    strategy=artifact.strategy,
                    file=target.name,
                    verified_sources=_source_proofs(root, artifact, selected),
                    bytes=target.stat().st_size,
                    sha256=_sha256(target),
                )
            )

        manifest = _release_manifest(records, excluded_development_jars)
        (staging / MANIFEST_NAME).write_text(
            json.dumps(manifest, indent=2, sort_keys=False) + "\n", encoding="utf-8"
        )
        (staging / SUMMARY_NAME).write_text(
            _summary(records, excluded_development_jars), encoding="utf-8"
        )
        _publish(staging, destination)
    except Exception:
        if staging.exists():
            shutil.rmtree(staging)
        raise

    return records


def _synthetic_reference_class(*references: str, marker: bytes = b"") -> bytes:
    """Build a declaration and constant pool for the relocation self-tests."""
    from verify_release_jars import _synthetic_classfile
    return _synthetic_classfile(
        _MOD_CLASS_PREFIX + "Synthetic", set(), references=references,
        utf8_constants=(marker.decode("utf-8"),),
    )


def _run_self_test() -> int:
    public_api = _SERVER_API_CLASS_PREFIX + "SyntheticApi.class"
    implementation = _MOD_CLASS_PREFIX + "internal/SyntheticImplementation"
    implementation_class = implementation + ".class"
    stable_api = _synthetic_reference_class()
    stable_archives = [
        (("one",), Path("one.jar"), {public_api: stable_api}),
        (("two",), Path("two.jar"), {public_api: stable_api}),
    ]
    if _variant_class_paths(stable_archives, "forge"):
        print("SELF-TEST ERROR: stable public API was classified as variant", file=sys.stderr)
        return 1

    differing_archives = [
        (
            ("one",),
            Path("one.jar"),
            {public_api: _synthetic_reference_class(marker=b"one")},
        ),
        (
            ("two",),
            Path("two.jar"),
            {public_api: _synthetic_reference_class(marker=b"two")},
        ),
    ]
    try:
        _variant_class_paths(differing_archives, "forge")
    except CollectionError:
        pass
    else:
        print("SELF-TEST ERROR: varying public API was not rejected", file=sys.stderr)
        return 1

    transitive_archives = [
        (
            ("one",),
            Path("one.jar"),
            {
                public_api: _synthetic_reference_class(implementation),
                implementation_class: _synthetic_reference_class(marker=b"one"),
            },
        ),
        (
            ("two",),
            Path("two.jar"),
            {
                public_api: _synthetic_reference_class(implementation),
                implementation_class: _synthetic_reference_class(marker=b"two"),
            },
        ),
    ]
    try:
        transitive_variants = _variant_class_paths(transitive_archives, "neoforge")
    except CollectionError:
        pass
    else:
        print(
            "SELF-TEST ERROR: public API depending on a variant class was not rejected; "
            f"variants={sorted(transitive_variants)!r}, "
            f"references={sorted(_class_references(transitive_archives[0][2][public_api]))!r}",
            file=sys.stderr,
        )
        return 1

    relocated = {
        "com/reign/betterlore/compat/generated/forge/mc_test/api/server/Api.class",
        "com/reign/betterlore/mixin/generated/forge/mc_test/api/server/MixinApi.class",
    }
    if _generated_server_api_paths(relocated) != sorted(relocated):
        print("SELF-TEST ERROR: generated public API path was not detected", file=sys.stderr)
        return 1

    print("Synthetic release-jar collector checks passed.")
    return 0


def main() -> int:
    if sys.argv[1:] == ["--self-test"]:
        return _run_self_test()
    if sys.argv[1:]:
        print(f"ERROR: unsupported argument(s): {' '.join(sys.argv[1:])}", file=sys.stderr)
        return 2
    try:
        records = collect_release_jars()
    except CollectionError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print(
        f"Collected {len(records)} compatibility jar(s) covering 52 runtime targets "
        f"into {ROOT / 'build' / RELEASE_DIRECTORY_NAME}"
    )
    print(f"Manifest: {ROOT / 'build' / RELEASE_DIRECTORY_NAME / MANIFEST_NAME}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
