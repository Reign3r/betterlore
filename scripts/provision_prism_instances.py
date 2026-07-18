#!/usr/bin/env python3
"""Provision the complete Better Lore PrismLauncher compatibility matrix."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import shutil
import sys
import zipfile


ROOT = Path(__file__).resolve().parents[1]
VERSIONS = (
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
LOADERS = {
    "Fabric": ("fabric", "net.fabricmc.fabric-loader", "deps.fabric_loader"),
    "NeoForge": ("neoforge", "net.neoforged", "deps.neoforge"),
    "Forge": ("forge", "net.minecraftforge", "deps.forge"),
}
OWNED_MOD_PREFIXES = ("better-lore-", "fabric-api-", "placeholder-api-", "jei-")


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


def fabric_mod_version(path: Path) -> str:
    with zipfile.ZipFile(path) as archive:
        payload = json.loads(archive.read("fabric.mod.json").decode("utf-8"))
    version = str(payload.get("version", "")).strip()
    if not version:
        raise ProvisionError(f"{path}: fabric.mod.json has no version")
    return version


def placeholder_jar(cache_root: Path, coordinate: str) -> tuple[Path, str]:
    if coordinate.startswith("maven.modrinth:"):
        group, artifact, version = coordinate.split(":", 2)
        source = cached_module_jar(cache_root, group, artifact, version)
        display_version = fabric_mod_version(source)
        return source, f"placeholder-api-{display_version}.jar"

    if coordinate.count(":") == 2:
        group, artifact, version = coordinate.split(":", 2)
    else:
        group, artifact, version = "eu.pb4", "placeholder-api", coordinate
    source = cached_module_jar(cache_root, group, artifact, version)
    return source, source.name


def loader_version(minecraft: str, loader: str, raw_version: str) -> str:
    if loader != "Forge":
        return raw_version
    prefix = f"{minecraft}-"
    if not raw_version.startswith(prefix):
        raise ProvisionError(f"Forge {minecraft}: invalid dependency version {raw_version}")
    return raw_version[len(prefix) :]


def expected_instances() -> list[tuple[str, str, str, str, dict[str, str]]]:
    instances: list[tuple[str, str, str, str, dict[str, str]]] = []
    for minecraft in VERSIONS:
        properties = read_properties(ROOT / "versions" / minecraft / "gradle.properties")
        for display_loader, (artifact_loader, component_uid, property_name) in LOADERS.items():
            raw_version = properties.get(property_name, "UNSUPPORTED")
            if raw_version == "UNSUPPORTED":
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
    if len(instances) != 52:
        raise ProvisionError(f"expected 52 supported instances, resolved {len(instances)}")
    return instances


def ensure_zip(path: Path) -> None:
    try:
        with zipfile.ZipFile(path) as archive:
            if not archive.namelist():
                raise ProvisionError(f"{path}: empty archive")
    except zipfile.BadZipFile as error:
        raise ProvisionError(f"{path}: invalid jar") from error


def provision(instances_root: Path, gradle_cache_root: Path) -> tuple[int, list[str]]:
    release = ROOT / "build" / "release"
    staged_jei = ROOT / "build" / "prism-testing-deps" / "jei"
    if not release.is_dir():
        raise ProvisionError(f"missing release directory: {release}")
    if not instances_root.is_dir():
        raise ProvisionError(f"missing Prism instances directory: {instances_root}")

    jei_count = 0
    jei_gaps: list[str] = []
    for minecraft, display_loader, artifact_loader, component_uid, properties in expected_instances():
        instance_name = f"{minecraft}_{display_loader}_Testing"
        instance = instances_root / instance_name
        mods = instance / "minecraft" / "mods"
        mods.mkdir(parents=True, exist_ok=True)

        raw_loader_version = properties[LOADERS[display_loader][2]]
        resolved_loader_version = loader_version(minecraft, display_loader, raw_loader_version)
        better_lore = release / f"better-lore-{artifact_loader}-{minecraft}-1.1.0.jar"
        if not better_lore.is_file():
            raise ProvisionError(f"{instance_name}: missing {better_lore.name}")

        expected_mods: list[tuple[Path, str]] = [(better_lore, better_lore.name)]
        notes = [
            "Better Lore 1.1.0 test instance",
            f"Minecraft: {minecraft}",
            f"Loader: {display_loader} {resolved_loader_version}",
        ]

        if display_loader == "Fabric":
            fabric_api_version = properties["deps.fabric_api"]
            fabric_api = cached_module_jar(
                gradle_cache_root,
                "net.fabricmc.fabric-api",
                "fabric-api",
                fabric_api_version,
            )
            placeholder, placeholder_name = placeholder_jar(
                gradle_cache_root, properties["deps.placeholder_api"]
            )
            expected_mods.extend(((fabric_api, fabric_api.name), (placeholder, placeholder_name)))
            notes.extend(
                (
                    f"Fabric API: {fabric_api_version}",
                    f"Placeholder API: {fabric_mod_version(placeholder)}",
                )
            )

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
            shutil.copy2(source, mods / destination)

        pack = {
            "components": [
                {
                    "important": True,
                    "uid": "net.minecraft",
                    "version": minecraft,
                },
                {
                    "uid": component_uid,
                    "version": resolved_loader_version,
                },
            ],
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
            "iconKey=default\n"
            f"name={instance_name}\n",
            encoding="utf-8",
        )
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
    arguments = parser.parse_args()

    try:
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
