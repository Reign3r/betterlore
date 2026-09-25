"""Regression tests for access boundaries changed by flat-jar relocation."""

from io import BytesIO
from pathlib import Path
import unittest
import zipfile

from collect_release_jars import _variant_class_paths, _rewrite_class_names
from verify_release_jars import (
    _synthetic_classfile as classfile,
    _validate_better_lore_access,
)

PACKAGE = "com/reign/betterlore/lore/quicktext/"
GENERATED = "com/reign/betterlore/compat/generated/test/family/lore/quicktext/"
PUBLIC, PRIVATE, PROTECTED, STATIC = 1, 2, 4, 8


def access_errors(classes):
    buffer = BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        for name, data in classes.items():
            archive.writestr(name + ".class", data)
    errors = []
    with zipfile.ZipFile(buffer) as archive:
        _validate_better_lore_access(archive, set(archive.namelist()), errors, "fixture")
    return "\n".join(errors)


class RelocationAccessTest(unittest.TestCase):
    def test_public_facade_stays_shared_even_with_private_implementation_members(self):
        caller, facade, holder = (PACKAGE + name for name in ("Parser", "Facade", "Facade$Holder"))
        plugin = "com/reign/betterlore/compat/jei/BetterLoreJeiPlugin"
        archives = []
        for marker in ("one", "two"):
            classes = {
                caller: classfile(caller, set(), method_references=((facade, "create", "()I"),),
                                  utf8_constants=(marker,)),
                facade: classfile(facade, {("create", "()I"), ("<init>", "()V")},
                                  method_access={("create", "()I"): PUBLIC | STATIC, ("<init>", "()V"): PRIVATE},
                                  references=(holder,), nest_members=(holder,)),
                holder: classfile(holder, set(), class_access=0, nest_host=facade),
                plugin: classfile(plugin, set(), method_references=((facade, "create", "()I"),)),
            }
            archives.append(((marker,), Path(marker + ".jar"), {k + ".class": v for k, v in classes.items()}))
        self.assertEqual({caller + ".class"}, _variant_class_paths(archives, "forge"))

    def test_stable_package_dependencies_follow_variant_callers_to_fixed_point(self):
        caller, helper, leaf, nested, unrelated = (PACKAGE + name for name in (
            "Parser", "Colors", "ColorMath", "Colors$Mode", "Unrelated"))
        api = "com/reign/betterlore/api/server/StableApi"
        archives = []
        for marker in ("one", "two"):
            classes = {
                caller: classfile(caller, set(), method_references=((helper, "color", "()I"),),
                                  references=(api,), utf8_constants=(marker,)),
                # Public class with a package-private method needs relocation too.
                helper: classfile(helper, {("color", "()I")}, method_access={("color", "()I"): STATIC},
                                  references=(leaf,), nest_members=(nested,)),
                leaf: classfile(leaf, set(), class_access=0),
                nested: classfile(nested, set(), class_access=0, nest_host=helper),
                unrelated: classfile(unrelated, set()),
                api: classfile(api, set()),
            }
            archives.append(((marker,), Path(marker + ".jar"), {k + ".class": v for k, v in classes.items()}))
        selected = _variant_class_paths(archives, "forge")
        self.assertEqual({name + ".class" for name in (caller, helper, leaf, nested)}, selected)
        mapping = {name[:-6]: name[:-6].replace(PACKAGE, GENERATED) for name in selected}
        relocated = {mapping.get(k, k): _rewrite_class_names(v, mapping) for k, v in classes.items()}
        self.assertEqual("", access_errors(relocated))

    def test_rejects_stable_package_private_class_stranded_by_relocation(self):
        caller, helper = GENERATED + "Parser", PACKAGE + "Colors"
        errors = access_errors({
            caller: classfile(caller, set(), method_references=((helper, "color", "()I"),)),
            helper: classfile(helper, {("color", "()I")}, class_access=0),
        })
        self.assertIn("inaccessible class " + helper, errors)

    def test_rejects_package_private_method_constructor_and_field_on_public_class(self):
        caller, helper = GENERATED + "Parser", PACKAGE + "Colors"
        errors = access_errors({
            caller: classfile(caller, set(),
                              method_references=((helper, "color", "()I"), (helper, "<init>", "()V")),
                              field_references=((helper, "value", "I"),)),
            helper: classfile(helper, {("color", "()I"), ("<init>", "()V")},
                              method_access={("color", "()I"): STATIC, ("<init>", "()V"): 0},
                              fields={("value", "I"): (STATIC, None)}),
        })
        for member in ("Colors.color()I", "Colors.<init>()V", "Colors.valueI"):
            self.assertIn(member, errors)

    def test_resolves_inherited_member_access_against_declaring_class(self):
        caller, helper, base = GENERATED + "Parser", GENERATED + "Colors", PACKAGE + "Base"
        errors = access_errors({
            caller: classfile(caller, set(), method_references=((helper, "color", "()I"),),
                              field_references=((helper, "value", "I"),)),
            helper: classfile(helper, set(), super_name=base),
            base: classfile(base, {("color", "()I")}, method_access={("color", "()I"): STATIC},
                            fields={("value", "I"): (STATIC, None)}),
        })
        self.assertIn("Base.color()I", errors)
        self.assertIn("Base.valueI", errors)

    def test_allows_public_and_same_package_access(self):
        for caller in (PACKAGE + "Parser", GENERATED + "Parser"):
            for access in (PUBLIC, 0):
                helper = PACKAGE + "Colors"
                errors = access_errors({
                    caller: classfile(caller, set(), method_references=((helper, "color", "()I"),)),
                    helper: classfile(helper, {("color", "()I")}, method_access={("color", "()I"): access}),
                })
                self.assertEqual(bool(errors), caller.startswith(GENERATED) and access == 0)

    def test_protected_access_requires_same_package_or_subclass(self):
        caller, helper = GENERATED + "Parser", PACKAGE + "Colors"
        for parent in (helper, "java/lang/Object"):
            errors = access_errors({
                caller: classfile(caller, set(), super_name=parent,
                                  method_references=((helper, "color", "()I"),)),
                helper: classfile(helper, {("color", "()I")}, method_access={("color", "()I"): PROTECTED | STATIC}),
            })
            self.assertEqual(bool(errors), parent != helper)

    def test_private_access_requires_reciprocal_nest_membership_in_same_package(self):
        host = PACKAGE + "Colors"
        for member, listed in ((PACKAGE + "Colors$Mode", True),
                               (PACKAGE + "Colors$Mode", False),
                               (GENERATED + "Colors$Mode", True)):
            errors = access_errors({
                host: classfile(host, {("color", "()I")}, method_access={("color", "()I"): PRIVATE | STATIC},
                                nest_members=(member,) if listed else ()),
                member: classfile(member, set(), nest_host=host,
                                  method_references=((host, "color", "()I"),)),
            })
            self.assertEqual(bool(errors), not listed or member.startswith(GENERATED))

    def test_metadata_reference_to_inaccessible_class_is_not_executable_access(self):
        caller, helper = GENERATED + "Parser", PACKAGE + "Colors"
        self.assertEqual("", access_errors({
            caller: classfile(caller, set(), references=(helper,)),
            helper: classfile(helper, set(), class_access=0),
        }))


if __name__ == "__main__":
    unittest.main()
