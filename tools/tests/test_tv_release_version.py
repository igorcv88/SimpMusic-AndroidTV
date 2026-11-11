import unittest

from tools.tv_release_version import plan_version


CATALOG = '''[versions]
# App version
version-name = "2.5.0"
version-code = "62"
android = "9.5.0"
'''


class TestReleaseVersion(unittest.TestCase):
    def test_bumps_patch_and_code_from_catalog(self):
        plan = plan_version(CATALOG, "fix(tv): focus", set())
        self.assertEqual((plan.tag, plan.version_code), ("v2.5.1", 63))
        self.assertIn('version-name = "2.5.1"', plan.updated_catalog)
        self.assertIn('version-code = "63"', plan.updated_catalog)
        self.assertIn('android = "9.5.0"', plan.updated_catalog)
        self.assertTrue(plan.needs_commit)

    def test_subsequent_release_increments(self):
        first = plan_version(CATALOG, "feat(tv): UI", set())
        second = plan_version(first.updated_catalog, "fix(tv): blur", {first.tag})
        self.assertEqual((second.tag, second.version_code), ("v2.5.2", 64))

    def test_resume_pending_bump_if_tag_absent(self):
        bumped = plan_version(CATALOG, "feat(tv): UI", set())
        pending = plan_version(bumped.updated_catalog, "chore(tv-release): bump to v2.5.1 [skip ci]", set())
        self.assertFalse(pending.needs_commit)
        self.assertEqual((pending.tag, pending.version_code), ("v2.5.1", 63))
        self.assertEqual(pending.updated_catalog, bumped.updated_catalog)

    def test_refuses_republishing_pending_tag(self):
        bumped = plan_version(CATALOG, "feat(tv): UI", set())
        with self.assertRaisesRegex(ValueError, "already tagged"):
            plan_version(bumped.updated_catalog, "chore(tv-release): bump to v2.5.1 [skip ci]", {"v2.5.1"})

    def test_refuses_catalog_behind_remote_tag(self):
        with self.assertRaisesRegex(ValueError, "newer than the version catalog"):
            plan_version(CATALOG, "feat(tv): UI", {"v2.5.2"})

    def test_keeps_minor_upgrade_and_bumps_patch(self):
        updated = CATALOG.replace('version-name = "2.5.0"', 'version-name = "2.6.0"')
        self.assertEqual(plan_version(updated, "feat(tv): new minor", {"v2.5.9"}).tag, "v2.6.1")

    def test_refuses_duplicate_catalog_values(self):
        with self.assertRaisesRegex(ValueError, "Expected one"):
            plan_version(CATALOG + 'version-code = "63"\n', "anything", set())

    def test_preamble_not_rewritten(self):
        c = '# version-name = "9.9.9"\n' + CATALOG
        p = plan_version(c, "feat(tv): something", set())
        self.assertEqual(p.updated_catalog.splitlines()[0], '# version-name = "9.9.9"')


if __name__ == "__main__":
    unittest.main()
