import importlib.util
import json
import os
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "import_exercises.py"

class ImportContractTests(unittest.TestCase):
    def test_verified_source_exports_faithful_non_media_catalog(self):
        self.assertTrue(SCRIPT.exists(), "Missing pinned-source metadata importer")
        spec = importlib.util.spec_from_file_location("import_exercises", SCRIPT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        source = Path(os.environ.get("EXERCISES_SOURCE_DIR", os.environ["TMPDIR"] + "/exercises-dataset-pinned"))
        result = module.build(source, ROOT / "selection.json")
        self.assertEqual(1324, result["source_manifest"]["source_record_count"])
        self.assertEqual(1324, result["source_manifest"]["unique_id_count"])
        self.assertEqual(30, len(result["exercise-catalog"]["records"]))
        self.assertEqual(6, len(result["exercise-mapping"]["mappings"]))
        originals = {x["id"]: x for x in json.loads((source / "exercises.json").read_bytes())}
        for record in result["exercise-catalog"]["records"]:
            original = originals[record["id"]]
            for key in ("name", "equipment", "target", "muscle_group", "secondary_muscles", "image", "gif_url", "media_id", "attribution"):
                self.assertEqual(original[key], record[key])
            self.assertEqual(original["instructions"]["zh"], record["instructions"]["zh"])
            self.assertEqual("user_declared_ready_gym_authorization", record["media_policy"]["status"])
            self.assertTrue(record["media_policy"]["licensed"])
            self.assertFalse(record["media_policy"]["permission_document_reviewed"])
            self.assertFalse(record["media_policy"]["bundled"])
            self.assertFalse(record["media_policy"]["hotlink_allowed"])
        self.assertEqual(0, result["source_manifest"]["media_files_downloaded_by_metadata_importer"])

    def test_current_plan_mapping_preserves_specific_variants_and_evidence(self):
        self.assertTrue(SCRIPT.exists())
        spec = importlib.util.spec_from_file_location("import_exercises", SCRIPT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        source = Path(os.environ.get("EXERCISES_SOURCE_DIR", os.environ["TMPDIR"] + "/exercises-dataset-pinned"))
        result = module.build(source, ROOT / "selection.json")
        current = result.get("current-plan-media-mapping")
        self.assertIsNotNone(current, "Missing current-plan concrete source/media mapping")
        mappings = {x["stable_id"]: x for x in current["mappings"]}
        expected = {"rg-v1-bodyweight-squat": "3119", "rg-v1-knee-push-up": "3211", "rg-v1-glute-bridge": "3013", "rg-v1-dead-bug": "0276", "rg-v1-dumbbell-row": "0293", "rg-v1-barbell-row": "0027"}
        self.assertEqual(expected, {key: x["source_id"] for key, x in mappings.items()})
        self.assertEqual("visual_match_instruction_conflict", mappings["rg-v1-knee-push-up"]["media_match_status"])
        self.assertEqual("visual_alternation_not_confirmed", mappings["rg-v1-dead-bug"]["media_match_status"])
        self.assertEqual(14, result["source_manifest"]["selected_media_files_fetched"])
        for entry in mappings.values():
            self.assertIn("zh", entry["instructions"])
            self.assertIn("equipment", entry)
            self.assertIn("target", entry)
            self.assertEqual("© Gym visual — https://gymvisual.com/", entry["attribution"])
            self.assertEqual([180, 180], entry["media_verification"]["gif_url"]["dimensions"])
            self.assertEqual(12, entry["media_verification"]["gif_url"]["frames"])
            self.assertFalse(entry["runtime_enabled"])

    def test_altered_raw_source_is_rejected_before_parsing(self):
        import tempfile
        spec = importlib.util.spec_from_file_location("import_exercises", SCRIPT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory(dir=os.environ["TMPDIR"]) as directory:
            (Path(directory) / "exercises.json").write_bytes(b"[]")
            with self.assertRaisesRegex(ValueError, "Git blob SHA-1 mismatch"):
                module.build(directory, ROOT / "selection.json")

    def test_materialized_artifacts_have_exact_counts_and_media_hashes(self):
        import hashlib
        from PIL import Image
        manifest = json.loads((ROOT / "source_manifest.json").read_bytes())
        self.assertEqual(1324, manifest["source_record_count"])
        references = json.loads((ROOT / "media-reference-index.json").read_bytes())["records"]
        self.assertEqual(1324, len(references))
        self.assertEqual(1324, len({x["id"] for x in references}))
        reference_by_id = {x["id"]: x for x in references}
        verification = json.loads((ROOT / "source-media/current-plan-verification.json").read_bytes())
        files = [f for item in verification["media"] for f in item["files"]]
        self.assertEqual(14, len(files))
        for item in verification["media"]:
            for record in item["files"]:
                path = ROOT / "source-media/current-plan" / Path(record["source_path"]).name
                raw = path.read_bytes()
                sha = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
                key = "gif_blob_sha1" if path.suffix == ".gif" else "image_blob_sha1"
                self.assertEqual(reference_by_id[item["id"]][key], sha)
                with Image.open(path) as im:
                    self.assertEqual((180, 180), im.size)
                    if path.suffix == ".gif":
                        self.assertEqual(12, im.n_frames)
                        distinct = set()
                        for i in range(im.n_frames):
                            im.seek(i)
                            distinct.add(hashlib.sha256(im.convert("RGB").tobytes()).hexdigest())
                        self.assertEqual(10, len(distinct))
        for name in ("LICENSE", "NOTICE.md"):
            raw = (ROOT / "SOURCE" / name).read_bytes()
            sha = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
            self.assertEqual(manifest["upstream_license_artifacts"][name]["git_blob_sha1"], sha)

if __name__ == "__main__":
    unittest.main()
