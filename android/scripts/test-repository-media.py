"""Checks bundled repository media against reviewed original Git blobs."""
import hashlib
import json
from pathlib import Path
import unittest
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
MAPPING = REPO / "data/current-plan-media-mapping.json"

class RepositoryMediaContract(unittest.TestCase):
    def test_all_current_gifs_and_thumbnails_are_the_verified_original_files(self):
        mappings = json.loads(MAPPING.read_text())["mappings"]
        self.assertEqual(6, len(mappings))
        for item in mappings:
            with self.subTest(stable_id=item["stable_id"]):
                for kind, field in [("gif_url", "local_gif_path"), ("image", "local_image_path")]:
                    original = REPO / "data" / item[field]
                    bundled = ROOT / "app/src/main/assets/exercise-demos" / original.name
                    self.assertTrue(bundled.is_file())
                    payload = bundled.read_bytes()
                    self.assertEqual(original.read_bytes(), payload)
                    blob = hashlib.sha1(f"blob {len(payload)}\0".encode() + payload).hexdigest()
                    self.assertEqual(item["media_verification"][kind]["git_blob_sha1"], blob)
                    with Image.open(bundled) as image:
                        self.assertEqual((180, 180), image.size)
                        if kind == "gif_url":
                            self.assertGreater(image.n_frames, 1)
                            distinct = set()
                            for frame in range(image.n_frames):
                                image.seek(frame)
                                self.assertGreater(image.info.get("duration", 0), 0)
                                distinct.add(hashlib.sha256(image.convert("RGBA").tobytes()).digest())
                            self.assertGreater(len(distinct), 1)

if __name__ == "__main__":
    unittest.main(verbosity=2)
