"""Offline asset contract. Pillow is tooling only, not an Android dependency."""
import hashlib
import json
from pathlib import Path
import unittest
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "app/src/main/assets/exercise-demos/squats-wensceslao.gif"

class ExerciseAssetContract(unittest.TestCase):
    def test_squat_is_unmodified_loop_with_actual_distinct_frames(self):
        self.assertTrue(ASSET.is_file(), "verified original squat GIF must be bundled locally")
        data = ASSET.read_bytes()
        self.assertEqual("a5d98e1abccc82eaf946bacf65af914c9def49f5e5de8d6e9018ab9f1d38cdaf", hashlib.sha256(data).hexdigest())
        with Image.open(ASSET) as image:
            self.assertEqual("GIF", image.format)
            self.assertEqual((640, 480), image.size)
            self.assertEqual(66, image.n_frames)
            self.assertEqual(0, image.info.get("loop"))
            durations, frames = [], set()
            for index in range(image.n_frames):
                image.seek(index)
                durations.append(image.info.get("duration", 0))
                frames.add(hashlib.sha256(image.convert("RGBA").tobytes()).hexdigest())
            self.assertTrue(all(duration > 0 for duration in durations))
            self.assertGreater(len(frames), 2)
            print(json.dumps({"asset": str(ASSET), "frames": image.n_frames,
                "durationMs": sum(durations), "distinctFrames": len(frames), "dimensions": image.size}))

if __name__ == "__main__":
    unittest.main(verbosity=2)
