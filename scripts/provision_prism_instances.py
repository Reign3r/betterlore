#!/usr/bin/env python3
"""Provision the complete Better Lore PrismLauncher compatibility matrix."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import zipfile

from release_matrix import MINECRAFT_VERSIONS, published_artifacts

ROOT = Path(__file__).resolve().parents[1]
LOADERS = {
    "Fabric": ("fabric", "net.fabricmc.fabric-loader", "deps.fabric_loader"),
    "NeoForge": ("neoforge", "net.neoforged", "deps.neoforge"),
    "Forge": ("forge", "net.minecraftforge", "deps.forge"),
}
# Retain the retired dependency prefix only to clean previously provisioned
# testing instances during full provisioning. It is no longer installed.
OWNED_MOD_PREFIXES = ("better-lore-", "fabric-api-", "placeholder-api-", "jei-")
TEST_MIN_MEMORY_MB = 1024
TEST_MAX_MEMORY_MB = 2048


class ProvisionError(RuntimeError):
    """Raised when an instance cannot be provisioned deterministically."""


def read_properties(path: Path) -> dict[str, str]:
    properties: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()
    return properties


def project_mod_version() -> str:
    version = read_properties(ROOT / "gradle.properties").get("mod.version", "").strip()
    if not version:
        raise ProvisionError(f"{ROOT / 'gradle.properties'}: missing mod.version")
    return version


def single_file(paths: list[Path], label: str) -> Path:
    files = sorted(path for path in paths if path.is_file())
    if len(files) != 1:
        rendered = ", ".join(str(path) for path in files) or "none"
        raise ProvisionError(f"{label}: expected exactly one file, found {rendered}")
    return files[0]


def cached_module_jar(
    cache_root: Path,
    group: str,
    artifact: str,
    version: str,
    filename: str | None = None,
) -> Path:
    module = cache_root / "modules-2" / "files-2.1" / group / artifact / version
    expected_name = filename or f"{artifact}-{version}.jar"
    return single_file(
        [path for path in module.glob(f"*/{expected_name}") if "sources" not in path.name],
        f"cached dependency {group}:{artifact}:{version}",
    )


def loader_version(minecraft: str, loader: str, raw_version: str) -> str:
    if loader != "Forge":
        return raw_version
    prefix = f"{minecraft}-"
    if not raw_version.startswith(prefix):
        raise ProvisionError(f"Forge {minecraft}: invalid dependency version {raw_version}")
    return raw_version[len(prefix) :]


def expected_instances() -> list[tuple[str, str, str, str, dict[str, str]]]:
    instances: list[tuple[str, str, str, str, dict[str, str]]] = []
    targets = {(artifact.loader, version) for artifact in published_artifacts() for version in artifact.versions}
    for minecraft in MINECRAFT_VERSIONS:
        properties = read_properties(ROOT / "versions" / minecraft / "gradle.properties")
        for display_loader, (artifact_loader, component_uid, property_name) in LOADERS.items():
            if (artifact_loader, minecraft) not in targets:
                continue
            instances.append(
                (
                    minecraft,
                    display_loader,
                    artifact_loader,
                    component_uid,
                    properties,
                )
            )
    if len(instances) != len(targets):
        raise ProvisionError(f"expected {len(targets)} supported instances, resolved {len(instances)}")
    return instances


def ensure_zip(path: Path) -> None:
    try:
        with zipfile.ZipFile(path) as archive:
            if not archive.namelist():
                raise ProvisionError(f"{path}: empty archive")
    except zipfile.BadZipFile as error:
        raise ProvisionError(f"{path}: invalid jar") from error


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def release_target_index(release: Path) -> dict[tuple[str, str], dict[str, str]]:
    manifest_path = release / "manifest.json"
    payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    if payload.get("schema_version") != 2:
        raise ProvisionError(f"{manifest_path}: expected schema_version 2")
    artifacts = payload.get("artifacts")
    targets = payload.get("targets")
    if not isinstance(artifacts, list) or not isinstance(targets, list):
        raise ProvisionError(f"{manifest_path}: artifacts and targets must be arrays")
    artifact_hashes: dict[str, str] = {}
    for record in artifacts:
        if not isinstance(record, dict):
            raise ProvisionError(f"{manifest_path}: invalid artifact record")
        file_name = record.get("file")
        expected_hash = record.get("sha256")
        if not isinstance(file_name, str) or not isinstance(expected_hash, str):
            raise ProvisionError(f"{manifest_path}: artifact file/hash is invalid")
        jar = release / file_name
        if not jar.is_file() or sha256(jar) != expected_hash:
            raise ProvisionError(f"{manifest_path}: artifact hash mismatch for {file_name}")
        artifact_hashes[file_name] = expected_hash

    index: dict[tuple[str, str], dict[str, str]] = {}
    for record in targets:
        if not isinstance(record, dict):
            raise ProvisionError(f"{manifest_path}: invalid target record")
        loader = record.get("loader")
        minecraft = record.get("minecraft")
        file_name = record.get("file")
        if not all(isinstance(value, str) for value in (loader, minecraft, file_name)):
            raise ProvisionError(f"{manifest_path}: invalid target mapping")
        if file_name not in artifact_hashes:
            raise ProvisionError(f"{manifest_path}: target references unknown artifact {file_name}")
        key = (loader, minecraft)
        if key in index:
            raise ProvisionError(f"{manifest_path}: duplicate target {loader} {minecraft}")
        index[key] = {key: value for key, value in record.items() if isinstance(value, str)}
    if len(index) != 52:
        raise ProvisionError(f"{manifest_path}: expected 52 targets, found {len(index)}")
    return index


def install_file(source: Path, destination: Path, *, hardlink: bool = False) -> None:
    if destination.exists() or destination.is_symlink():
        destination.unlink()
    if hardlink:
        try:
            os.link(source, destination)
            return
        except OSError:
            pass
    shutil.copy2(source, destination)


def atomic_write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f"{path.name}.better-lore-tmp")
    temporary.write_text(text, encoding="utf-8")
    os.replace(temporary, path)


def update_properties(path: Path, updates: dict[str, str]) -> None:
    if not path.is_file():
        raise ProvisionError(f"missing instance config: {path}")
    lines = path.read_text(encoding="utf-8").splitlines()
    remaining = dict(updates)
    seen: set[str] = set()
    normalized: list[str] = []
    current_section: str | None = None
    general_sections = 0
    general_insert_index: int | None = None
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("[") and stripped.endswith("]"):
            if current_section == "General" and general_insert_index is None:
                general_insert_index = len(normalized)
            current_section = stripped[1:-1]
            if current_section == "General":
                general_sections += 1
            normalized.append(line)
            continue
        if "=" not in line or line.lstrip().startswith("#"):
            normalized.append(line)
            continue
        key = line.split("=", 1)[0].strip()
        if key not in updates:
            normalized.append(line)
            continue
        if current_section != "General":
            continue
        if key in seen:
            continue
        seen.add(key)
        normalized.append(f"{key}={updates[key]}")
        remaining.pop(key)
    if current_section == "General" and general_insert_index is None:
        general_insert_index = len(normalized)
    if general_sections != 1 or general_insert_index is None:
        raise ProvisionError(
            f"{path}: expected exactly one [General] section, found {general_sections}"
        )
    normalized[general_insert_index:general_insert_index] = [
        f"{key}={value}" for key, value in remaining.items()
    ]
    atomic_write_text(path, "\n".join(normalized) + "\n")


def disable_fml_early_window(instance: Path) -> None:
    config = instance / "minecraft" / "config" / "fml.toml"
    if config.is_file():
        lines = config.read_text(encoding="utf-8").splitlines()
    else:
        lines = [
            "# The native Forge/NeoForge early splash is disabled for automated launch testing.",
        ]
    matches = 0
    for index, line in enumerate(lines):
        if line.strip().startswith("earlyWindowControl") and "=" in line:
            lines[index] = "earlyWindowControl = false"
            matches += 1
    if matches > 1:
        raise ProvisionError(f"{config}: duplicate earlyWindowControl settings")
    if matches == 0:
        lines.append("earlyWindowControl = false")
    atomic_write_text(config, "\n".join(lines) + "\n")


def lwjgl_version(meta_root: Path, minecraft: str) -> str:
    metadata = meta_root / "net.minecraft" / f"{minecraft}.json"
    try:
        payload = json.loads(metadata.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise ProvisionError(f"missing cached Minecraft metadata: {metadata}") from error
    for requirement in payload.get("requires", []):
        if requirement.get("uid") != "org.lwjgl3":
            continue
        version = requirement.get("equals") or requirement.get("suggests")
        if isinstance(version, str) and version:
            return version
    raise ProvisionError(f"{metadata}: no LWJGL 3 requirement")


def required_runtime_components(
    meta_root: Path, minecraft: str, display_loader: str
) -> list[dict[str, object]]:
    components: list[dict[str, object]] = [
        {
            "uid": "org.lwjgl3",
            "version": lwjgl_version(meta_root, minecraft),
        }
    ]
    if display_loader == "Fabric":
        components.append(
            {
                "uid": "net.fabricmc.intermediary",
                "version": minecraft,
            }
        )
    return components


def pin_offline_runtime_components(
    instance: Path, meta_root: Path, minecraft: str, display_loader: str
) -> None:
    pack_path = instance / "mmc-pack.json"
    try:
        pack = json.loads(pack_path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise ProvisionError(f"missing instance component pack: {pack_path}") from error
    components = pack.get("components")
    if not isinstance(components, list):
        raise ProvisionError(f"{pack_path}: components must be an array")
    by_uid = {
        component.get("uid"): component
        for component in components
        if isinstance(component, dict) and isinstance(component.get("uid"), str)
    }
    for required in reversed(
        required_runtime_components(meta_root, minecraft, display_loader)
    ):
        uid = str(required["uid"])
        component = by_uid.get(uid)
        if component is None:
            component = dict(required)
            components.insert(0, component)
            by_uid[uid] = component
        else:
            component["version"] = required["version"]
        component.pop("dependencyOnly", None)
    atomic_write_text(pack_path, json.dumps(pack, indent=4) + "\n")


def configure_test_runtime(instances_root: Path) -> tuple[int, int]:
    if not instances_root.is_dir():
        raise ProvisionError(f"missing Prism instances directory: {instances_root}")
    instance_count = 0
    fml_count = 0
    meta_root = instances_root.parent / "meta"
    for minecraft, display_loader, _, _, _ in expected_instances():
        instance = instances_root / f"{minecraft}_{display_loader}_Testing"
        if not instance.is_dir():
            raise ProvisionError(f"missing Prism testing instance: {instance}")
        update_properties(
            instance / "instance.cfg",
            {
                "OverrideMemory": "true",
                "MinMemAlloc": str(TEST_MIN_MEMORY_MB),
                "MaxMemAlloc": str(TEST_MAX_MEMORY_MB),
                "LowMemWarning": "false",
            },
        )
        pin_offline_runtime_components(
            instance, meta_root, minecraft, display_loader
        )
        if display_loader != "Fabric":
            disable_fml_early_window(instance)
            fml_count += 1
        instance_count += 1
    return instance_count, fml_count


def deploy_better_lore_only(instances_root: Path) -> tuple[int, int]:
    """Replace only Better Lore jars, preserving every other instance file."""

    release = ROOT / "build" / "release"
    if not release.is_dir():
        raise ProvisionError(f"missing release directory: {release}")
    if not instances_root.is_dir():
        raise ProvisionError(f"missing Prism instances directory: {instances_root}")
    release_targets = release_target_index(release)
    deployed_files: set[str] = set()
    instance_count = 0
    for minecraft, display_loader, artifact_loader, _, _ in expected_instances():
        instance_name = f"{minecraft}_{display_loader}_Testing"
        mods = instances_root / instance_name / "minecraft" / "mods"
        if not mods.is_dir():
            raise ProvisionError(f"{instance_name}: missing mods directory {mods}")
        mapping = release_targets.get((artifact_loader, minecraft))
        if mapping is None:
            raise ProvisionError(f"{instance_name}: missing release target mapping")
        source = release / mapping["file"]
        ensure_zip(source)
        destination = mods / source.name
        for existing in mods.iterdir():
            if (
                existing.is_file()
                and existing.name.lower().startswith("better-lore-")
                and existing != destination
            ):
                existing.unlink()
        install_file(source, destination, hardlink=True)
        if sha256(destination) != sha256(source):
            raise ProvisionError(f"{instance_name}: deployed Better Lore hash mismatch")
        installed = [
            path
            for path in mods.iterdir()
            if path.is_file() and path.name.lower().startswith("better-lore-")
        ]
        if installed != [destination]:
            raise ProvisionError(
                f"{instance_name}: expected only {destination.name}, found {[path.name for path in installed]}"
            )
        deployed_files.add(source.name)
        instance_count += 1
    return instance_count, len(deployed_files)


def provision(instances_root: Path, gradle_cache_root: Path) -> tuple[int, list[str]]:
    release = ROOT / "build" / "release"
    staged_jei = ROOT / "build" / "prism-testing-deps" / "jei"
    if not release.is_dir():
        raise ProvisionError(f"missing release directory: {release}")
    if not instances_root.is_dir():
        raise ProvisionError(f"missing Prism instances directory: {instances_root}")
    release_targets = release_target_index(release)
    mod_version = project_mod_version()

    jei_count = 0
    jei_gaps: list[str] = []
    for minecraft, display_loader, artifact_loader, component_uid, properties in expected_instances():
        instance_name = f"{minecraft}_{display_loader}_Testing"
        instance = instances_root / instance_name
        mods = instance / "minecraft" / "mods"
        mods.mkdir(parents=True, exist_ok=True)

        raw_loader_version = properties[LOADERS[display_loader][2]]
        resolved_loader_version = loader_version(minecraft, display_loader, raw_loader_version)
        release_target = release_targets.get((artifact_loader, minecraft))
        if release_target is None:
            raise ProvisionError(f"{instance_name}: missing release target mapping")
        better_lore = release / release_target["file"]
        if not better_lore.is_file():
            raise ProvisionError(f"{instance_name}: missing {better_lore.name}")

        expected_mods: list[tuple[Path, str]] = [(better_lore, better_lore.name)]
        notes = [
            f"Better Lore {mod_version} test instance",
            f"Minecraft: {minecraft}",
            f"Loader: {display_loader} {resolved_loader_version}",
            f"Compatibility artifact: {better_lore.name}",
        ]
        if "nested_candidate" in release_target:
            notes.append(f"Selected nested candidate: {release_target['nested_candidate']}")

        if display_loader == "Fabric":
            fabric_api_version = properties["deps.fabric_api"]
            fabric_api = cached_module_jar(
                gradle_cache_root,
                "net.fabricmc.fabric-api",
                "fabric-api",
                fabric_api_version,
            )
            expected_mods.append((fabric_api, fabric_api.name))
            notes.append(f"Fabric API: {fabric_api_version}")

        jei_candidates = list(staged_jei.glob(f"jei-{minecraft}-{artifact_loader}-*.jar"))
        if len(jei_candidates) > 1:
            raise ProvisionError(
                f"{instance_name}: multiple staged JEI runtimes: "
                + ", ".join(path.name for path in sorted(jei_candidates))
            )
        if jei_candidates:
            jei = jei_candidates[0]
            jei_version = jei.stem.removeprefix(f"jei-{minecraft}-{artifact_loader}-")
            expected_mods.append((jei, jei.name))
            notes.append(f"JEI: {jei_version}")
            jei_count += 1
        else:
            notes.append(
                f"JEI: not installed (no compatible {display_loader} runtime is published for Minecraft {minecraft})"
            )
            jei_gaps.append(f"{minecraft} {display_loader}")

        expected_names = {destination for _, destination in expected_mods}
        for existing in mods.iterdir():
            if not existing.is_file():
                continue
            if existing.name.lower().startswith(OWNED_MOD_PREFIXES) and existing.name not in expected_names:
                existing.unlink()
        for source, destination in expected_mods:
            ensure_zip(source)
            install_file(source, mods / destination, hardlink=source == better_lore)

        pack_components = required_runtime_components(
            instances_root.parent / "meta", minecraft, display_loader
        )
        pack_components.extend(
            [
                {
                    "important": True,
                    "uid": "net.minecraft",
                    "version": minecraft,
                },
                {
                    "uid": component_uid,
                    "version": resolved_loader_version,
                },
            ]
        )
        pack = {
            "components": pack_components,
            "formatVersion": 1,
        }
        (instance / "mmc-pack.json").write_text(
            json.dumps(pack, indent=4) + "\n", encoding="utf-8"
        )
        (instance / "instance.cfg").write_text(
            "[General]\n"
            "AutomaticJava=true\n"
            "ConfigVersion=1.3\n"
            "InstanceType=OneSix\n"
            "LowMemWarning=false\n"
            f"MaxMemAlloc={TEST_MAX_MEMORY_MB}\n"
            f"MinMemAlloc={TEST_MIN_MEMORY_MB}\n"
            "OverrideMemory=true\n"
            "iconKey=default\n"
            f"name={instance_name}\n",
            encoding="utf-8",
        )
        if display_loader != "Fabric":
            disable_fml_early_window(instance)
        (instance / "notes.txt").write_text("\n".join(notes) + "\n", encoding="utf-8")

        owned_installed = {
            path.name
            for path in mods.iterdir()
            if path.is_file() and path.name.lower().startswith(OWNED_MOD_PREFIXES)
        }
        if owned_installed != expected_names:
            raise ProvisionError(
                f"{instance_name}: owned mod set differs; expected {sorted(expected_names)}, "
                f"found {sorted(owned_installed)}"
            )

    return jei_count, jei_gaps


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--instances-root", type=Path, required=True)
    parser.add_argument(
        "--gradle-cache-root",
        type=Path,
        default=Path.home() / ".gradle" / "caches",
    )
    parser.add_argument(
        "--better-lore-only",
        action="store_true",
        help="Replace only Better Lore jars; do not edit instance metadata or dependencies.",
    )
    parser.add_argument(
        "--configure-test-runtime",
        action="store_true",
        help=(
            "Configure all testing instances for a 1024-2048 MiB heap and disable "
            "the optional native FML early splash that can block unattended tests."
        ),
    )
    arguments = parser.parse_args()

    try:
        if arguments.better_lore_only:
            instance_count, artifact_count = deploy_better_lore_only(
                arguments.instances_root.resolve()
            )
            print(
                f"Deployed {artifact_count} compatibility artifact(s) across "
                f"{instance_count} Prism testing instances"
            )
            if arguments.configure_test_runtime:
                configured, fml_configured = configure_test_runtime(
                    arguments.instances_root.resolve()
                )
                print(
                    f"Configured {configured} testing instance(s) for "
                    f"{TEST_MAX_MEMORY_MB} MiB launches; disabled the optional "
                    f"early splash in {fml_configured} FML instance(s)"
                )
            return 0
        if arguments.configure_test_runtime:
            configured, fml_configured = configure_test_runtime(
                arguments.instances_root.resolve()
            )
            print(
                f"Configured {configured} testing instance(s) for "
                f"{TEST_MAX_MEMORY_MB} MiB launches; disabled the optional "
                f"early splash in {fml_configured} FML instance(s)"
            )
            return 0
        jei_count, jei_gaps = provision(
            arguments.instances_root.resolve(), arguments.gradle_cache_root.resolve()
        )
    except (OSError, KeyError, ValueError, json.JSONDecodeError, ProvisionError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print(f"Provisioned 52 canonical PrismLauncher test instances in {arguments.instances_root}")
    print(f"Installed compatible JEI runtimes in {jei_count} instances")
    print(f"Documented {len(jei_gaps)} JEI publication gap(s): {', '.join(jei_gaps)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
