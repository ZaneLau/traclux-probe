import importlib.util
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parent.parent


class CommunityDeliveryTests(unittest.TestCase):
    def test_initializer_creates_private_credentials_and_preserves_existing_instance(self):
        with tempfile.TemporaryDirectory(prefix="traclux-community-init-") as temp:
            root = Path(temp)
            (root / "scripts").mkdir()
            script = root / "scripts/community_init.py"
            shutil.copyfile(ROOT / "scripts/community_init.py", script)
            first = subprocess.run([sys.executable, str(script)], check=True, capture_output=True, text=True)
            env = root / ".env"
            original = env.read_bytes()
            values = dict(line.split("=", 1) for line in original.decode().splitlines() if line and not line.startswith("#"))
            self.assertEqual(stat.S_IMODE(env.stat().st_mode), 0o600)
            self.assertIn("@", values["COMMUNITY_USERNAME"])
            self.assertNotEqual(values["DB_PASSWORD"], values["COMMUNITY_PASSWORD"])
            self.assertGreaterEqual(len(values["JWT_SECRET"]), 32)
            self.assertNotIn(values["COMMUNITY_PASSWORD"], first.stdout)
            subprocess.run([sys.executable, str(script)], check=True, capture_output=True)
            self.assertEqual(env.read_bytes(), original)

    def test_export_contains_user_system_and_excludes_operations_console(self):
        spec = importlib.util.spec_from_file_location("community_boundary", ROOT / "scripts/check_community_boundary.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        self.assertEqual(module.check(ROOT), [])


if __name__ == "__main__":
    unittest.main()
