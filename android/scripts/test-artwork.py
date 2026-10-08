"""Resource contract tests; no device/UI-rendering claims."""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
A = '{http://schemas.android.com/apk/res/android}'
class ArtworkTest(unittest.TestCase):
    def test_adaptive_launcher_and_round_icon_are_declared(self):
        app = ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot().find('application')
        self.assertEqual('@mipmap/ic_launcher', app.get(A+'icon'))
        self.assertEqual('@mipmap/ic_launcher_round', app.get(A+'roundIcon'))
        for name in ['ic_launcher', 'ic_launcher_round']:
            icon = ET.parse(ROOT/f'app/src/main/res/mipmap-anydpi-v26/{name}.xml').getroot()
            self.assertEqual('adaptive-icon', icon.tag)
            self.assertEqual('@drawable/ic_launcher_foreground', icon.find('foreground').get(A+'drawable'))
    def test_equipment_has_four_distinct_original_vector_drawables(self):
        paths = []
        for name in ['bodyweight', 'dumbbell', 'barbell', 'machine']:
            file = ROOT/f'app/src/main/res/drawable/equipment_{name}.xml'
            self.assertTrue(file.exists(), str(file))
            vector = ET.parse(file).getroot()
            self.assertEqual('vector', vector.tag)
            self.assertGreaterEqual(len(vector.findall('path')), 6)
            paths.append(file.read_text())
        self.assertEqual(4, len(set(paths)))
if __name__ == '__main__': unittest.main()
