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
    PublishedArtifact,
    fabric_inner_path,
    published_artifacts,
    validate_release_matrix,
    version_label,
)
from verify_matrix import Artifact, ROOT, declared_artifacts, validate_matrix


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
    dependencies["placeholder-api"] = ">=" + _minimum_version(
        [state.profiles[version]["placeholder_api_version"] for version in family]
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
    """Validate all compile outputs and publish the 24 compatibility artifacts."""

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
            else:
                _build_range_artifact(state, artifact, selected, target)
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


def main() -> int:
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
