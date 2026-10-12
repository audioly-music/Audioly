import unittest
from release_version import next_version, parse_version


class ReleaseVersionTest(unittest.TestCase):
    def test_decimal_rollover(self):
        self.assertEqual(next_version("2.0.0"), "2.0.1")
        self.assertEqual(next_version("2.0.9"), "2.1.0")
        self.assertEqual(next_version("2.9.9"), "3.0.0")
        self.assertEqual(next_version("9.9.9"), "10.0.0")

    def test_explicit_bumps(self):
        self.assertEqual(next_version("2.9.4", "minor"), "3.0.0")
        self.assertEqual(next_version("2.4.5", "major"), "3.0.0")

    def test_reject_noncanonical_versions(self):
        for value in ("2.10.0", "2.0.10", "2.00.1", "2.0", "2.x.0", "v2.0.0"):
            with self.assertRaises(ValueError):
                parse_version(value)


if __name__ == "__main__":
    unittest.main()
