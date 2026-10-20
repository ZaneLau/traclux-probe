import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('workbench_package', Path(__file__).resolve().parents[1] / 'scripts/package_workbench_demo.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class StaticWorkbenchPackageTest(unittest.TestCase):
    def fixture(self, root, demo=True):
        source = root / 'frontend/.output/public'
        source.mkdir(parents=True)
        (root / 'docs/demo').mkdir(parents=True)
        (root / 'docs/demo/index.html').write_text('saved case walkthrough')
        mode = 'true' if demo else 'false'
        html = '<html><head></head><body><script>window.__NUXT__={config:{public:{demoMode:' + mode + '}}}</script></body></html>'
        for name in ['index.html', '200.html', '404.html']:
            (source / name).write_text(html)

    def test_rejects_a_normal_application_build(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.fixture(root, demo=False)
            with self.assertRaisesRegex(SystemExit, 'normal application'):
                module.package(root, root / 'output')

    def test_publishes_workbench_with_script_hashes_and_preserves_case(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.fixture(root)
            output = root / 'output'
            module.package(root, output)
            html = (output / 'demo/index.html').read_text()
            self.assertIn('Content-Security-Policy', html)
            self.assertIn("script-src 'self' 'sha256-", html)
            self.assertIn("connect-src 'self'", html)
            self.assertIn("form-action 'none'", html)
            self.assertEqual((output / 'demo/case-study/index.html').read_text(), 'saved case walkthrough')
            self.assertTrue((output / '.nojekyll').exists())
            with self.assertRaisesRegex(SystemExit, 'fresh output'):
                module.package(root, output)
