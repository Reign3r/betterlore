#!/usr/bin/env python3
"""Collect exactly the Better Lore release artifacts from Stonecutter outputs."""

from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import hashlib
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

from verify_matrix import Artifact, ROOT, declared_artifacts, validate_matrix


RELEASE_DIRECTORY_NAME = "release"
MANIFEST_NAME = "manifest.json"
SUMMARY_NAME = "SUMMARY.txt"
_DEVELOPMENT_SUFFIXES = ("-dev.jar", "_dev.jar", ".dev.jar")
_DOCUMENTATION_SUFFIXES = ("-sources.jar", "-javadoc.jar")


class CollectionError(RuntimeError):
    """Raised when the build outputs cannot prove a complete release set."""


@dataclass(frozen=True)
class CollectedArtifact:
    loader: str
    minecraft: str
    file: str
    source: str
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


def _release_manifest(records: list[CollectedArtifact], excluded_development_jars: list[str]) -> dict[str, object]:
    return {
        "schema_version": 1,
        "generated_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "artifact_count": len(records),
        "artifacts": [asdict(record) for record in records],
        "excluded_development_jars": excluded_development_jars,
    }


def _summary(records: list[CollectedArtifact], excluded_development_jars: list[str]) -> str:
    lines = [
        "Better Lore release artifact summary",
        f"Artifacts: {len(records)}",
        "",
    ]
    lines.extend(
        f"- {record.loader} {record.minecraft}: {record.file} ({record.bytes} bytes, sha256 {record.sha256})"
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
                    shutil.copy2(entry, target)
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
    """Validate and publish the complete stable-release artifact set."""

    state = validate_matrix(root)
    if state.errors:
        raise CollectionError("matrix validation failed:\n" + "\n".join(state.errors))

    mod_version = state.root_properties.get("mod.version", "").strip()
    mod_id = state.root_properties.get("mod.id", "").strip()
    if not mod_version or any(character in mod_version for character in "/\\"):
        raise CollectionError("gradle.properties: mod.version is missing or unsafe for an archive filename")
    if not mod_id:
        raise CollectionError("gradle.properties: mod.id is required for release metadata verification")

    selected: list[tuple[Artifact, Path]] = []
    excluded_development_jars: list[str] = []
    errors: list[str] = []
    for artifact in declared_artifacts():
        try:
            path, development_jars = _select_artifact(root, artifact, mod_version, mod_id)
        except CollectionError as error:
            errors.append(str(error))
            continue
        selected.append((artifact, path))
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
        for artifact, source in selected:
            target = staging / source.name
            shutil.copy2(source, target)
            records.append(
                CollectedArtifact(
                    loader=artifact.loader,
                    minecraft=artifact.minecraft,
                    file=target.name,
                    source=source.relative_to(root).as_posix(),
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

    print(f"Collected {len(records)} verified release jar(s) into {ROOT / 'build' / RELEASE_DIRECTORY_NAME}")
    print(f"Manifest: {ROOT / 'build' / RELEASE_DIRECTORY_NAME / MANIFEST_NAME}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
