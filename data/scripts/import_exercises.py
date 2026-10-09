#!/usr/bin/env python3
"""Offline pinned-source metadata importer. Never fetches exercise media."""
import argparse
import hashlib
import json
from pathlib import Path
import re

COMMIT = "7455efae41b330c265e7cd4b78dfa848e7ce5ebd"
BLOB = "3cbb77b678c40d7342897d38e4c61e9502f829a7"
SIZE = 17391530
COUNT = 1324
REPO = "https://github.com/hasaneyldrm/exercises-dataset"
LANGUAGES = {"en", "es", "fr", "hi", "it", "ko", "pl", "ru", "tr", "zh"}
TREES = {"images": "a332ef2472bc14570c163ca96bc8cf59aae95ff0", "videos": "893775b129e3d25f488baaa87863c78eaf2f7eb5"}
LICENSES = {"LICENSE": "5bd07008eea0578bfa41a51a6e29edfd51a5c073", "NOTICE.md": "419857375bbb147cf0e32279cc4e923a99aaf5f7"}
MEDIA_POLICY = {"licensed": True, "authorization_basis": "User explicitly stated images and GIFs are authorized for Ready Gym", "permission_document_reviewed": False, "authorization_scope": "Ready Gym, per user declaration; no broader transferable permission asserted", "bundled": False, "hotlink_allowed": False, "status": "user_declared_ready_gym_authorization", "retain_attribution": "© Gym visual — https://gymvisual.com/", "source_resolution_constraint": "180x180", "terms_url": "https://gymvisual.com/content/3-terms-and-conditions-of-use"}

def build(source_dir, selection_path):
    source_dir = Path(source_dir)
    raw = (source_dir / "exercises.json").read_bytes()
    digest = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
    if len(raw) != SIZE or digest != BLOB:
        raise ValueError("Pinned JSON byte count or Git blob SHA-1 mismatch")
    records = json.loads(raw)
    if not isinstance(records, list) or len(records) != COUNT:
        raise ValueError("Expected 1324 source records")
    ids = [x["id"] for x in records]
    if any(not isinstance(x, str) or not re.fullmatch(r"[0-9]{4}", x) for x in ids) or len(set(ids)) != COUNT:
        raise ValueError("Invalid or duplicate source ID")
    if len({x["media_id"] for x in records}) != COUNT:
        raise ValueError("Duplicate media ID")
    tree_files = {}
    for directory, expected_sha in TREES.items():
        tree = json.loads((source_dir / (directory + "-tree.json")).read_bytes())
        if tree.get("sha") != expected_sha or tree.get("truncated") is not False:
            raise ValueError("Unverified or truncated media file tree")
        files = {directory + "/" + x["path"]: x for x in tree["tree"] if x["type"] == "blob"}
        if len(files) != COUNT:
            raise ValueError("Unexpected media file count")
        tree_files.update(files)
    for record in records:
        for key in ("name", "category", "body_part", "equipment", "target", "muscle_group", "media_id", "attribution"):
            if not isinstance(record.get(key), str) or not record[key]:
                raise ValueError("Missing nonempty text field: " + key)
        if not isinstance(record.get("secondary_muscles"), list) or any(not isinstance(x, str) for x in record["secondary_muscles"]):
            raise ValueError("Invalid secondary muscles")
        if set(record["instructions"]) != LANGUAGES or set(record["instruction_steps"]) != LANGUAGES:
            raise ValueError("Unexpected instruction languages")
        for lang in LANGUAGES:
            if not isinstance(record["instructions"][lang], str) or not record["instructions"][lang].strip():
                raise ValueError("Empty instruction")
            steps = record["instruction_steps"][lang]
            if not isinstance(steps, list) or not steps or any(not isinstance(x, str) or not x.strip() for x in steps):
                raise ValueError("Invalid instruction steps")
        for key, directory, extension in (("image", "images", "jpg"), ("gif_url", "videos", "gif")):
            expected = directory + "/" + record["id"] + "-" + record["media_id"] + "." + extension
            if record[key] != expected or expected not in tree_files:
                raise ValueError("Media path/ID does not match verified file tree")
    licenses = {}
    for name, expected_sha in LICENSES.items():
        content = (source_dir / name).read_bytes()
        sha = hashlib.sha1(b"blob " + str(len(content)).encode() + b"\0" + content).hexdigest()
        if sha != expected_sha:
            raise ValueError("Upstream license integrity mismatch")
        licenses[name] = {"git_blob_sha1": sha, "byte_count": len(content), "sha256": hashlib.sha256(content).hexdigest()}
    chosen = json.loads(Path(selection_path).read_bytes())
    requested = chosen["selected_source_ids"]
    if len(set(requested)) != len(requested) or not 20 <= len(requested) <= 50:
        raise ValueError("Expected 20-50 unique curated IDs")
    by_id = {x["id"]: x for x in records}
    if any(x not in by_id for x in requested):
        raise ValueError("Selected ID not present in pinned source")
    exported = []
    for record_id in requested:
        original = by_id[record_id]
        item = {k: original[k] for k in ("id", "name", "category", "body_part", "equipment", "target", "muscle_group", "secondary_muscles", "image", "gif_url", "media_id", "created_at", "attribution")}
        item["instructions"] = {lang: original["instructions"][lang] for lang in ("en", "zh")}
        item["instruction_steps"] = {lang: original["instruction_steps"][lang] for lang in ("en", "zh")}
        item["source_reference"] = {"repository": REPO, "commit": COMMIT, "json_path": "data/exercises.json", "json_blob_sha1": BLOB, "id": record_id}
        item["media_policy"] = dict(MEDIA_POLICY)
        item["quality_flags"] = chosen["quality_flags"].get(record_id, [])
        exported.append(item)
    mapped = []
    for entry in chosen["mappings"]:
        if entry["source_id"] not in requested:
            raise ValueError("Mapped ID not in curated catalog")
        original = by_id[entry["source_id"]]
        mapped.append({**entry, "source_name": original["name"], "media_id": original["media_id"], "image": original["image"], "gif_url": original["gif_url"], "attribution": original["attribution"], "runtime_enabled": False, "media_policy": dict(MEDIA_POLICY)})
    if len(mapped) != 6 or len({x["local_id"] for x in mapped}) != 6:
        raise ValueError("Expected exactly six local exercise mappings")
    index = [{"id": x["id"], "media_id": x["media_id"], "image": x["image"], "image_blob_sha1": tree_files[x["image"]]["sha"], "gif_url": x["gif_url"], "gif_blob_sha1": tree_files[x["gif_url"]]["sha"], "attribution": x["attribution"]} for x in records]
    manifest = {"schema_version": 1, "repository": REPO, "commit": COMMIT, "json_path": "data/exercises.json", "json_byte_count": len(raw), "json_git_blob_sha1": digest, "json_sha256": hashlib.sha256(raw).hexdigest(), "retrieved_from": "https://cdn.jsdelivr.net/gh/hasaneyldrm/exercises-dataset@" + COMMIT + "/data/exercises.json", "source_record_count": len(records), "unique_id_count": len(set(ids)), "unique_media_id_count": len({x["media_id"] for x in records}), "source_instruction_languages": sorted(LANGUAGES), "catalog_record_count": len(exported), "catalog_instruction_languages": ["en", "zh"], "mapping_count": len(mapped), "media_reference_count": len(index), "verified_media_tree_counts": {k: COUNT for k in TREES}, "verified_media_tree_sha1": TREES, "media_files_downloaded": 0, "full_source_in_repository": False, "metadata_license": "MIT (upstream media exception retained)", "media_policy": dict(MEDIA_POLICY), "upstream_license_artifacts": licenses, "selection_sha256": hashlib.sha256(Path(selection_path).read_bytes()).hexdigest(), "limitations": ["Curated subset, not full dataset integration", "Runtime Android/core/backend unchanged", "No clinical, technique, suitability or translation quality validation", "3211 name/instructions conflict blocks knee-push-up equivalence", "Media authorization is user-declared for Ready Gym; no permission document has been reviewed; no general transfer or hotlink rights asserted"]}
    selected_verification_path = source_dir / "selected-media-verification.json"
    verification = json.loads(selected_verification_path.read_bytes()) if selected_verification_path.exists() else {"media": []}
    verified = {x["id"]: x for x in verification["media"]}
    current = []
    visual_notes = {
        "3119": ("visual_match", "联系图可见无器械双脚深蹲，未见额外支撑。"),
        "3211": ("visual_match_instruction_conflict", "联系图可见膝盖支撑；上游中英文指导却写脚掌支撑。GIF可用于跪姿候选，不能直接采用矛盾指导。"),
        "3013": ("visual_match", "联系图可见双侧地面臀桥，无凳、无外加负重。"),
        "0276": ("visual_alternation_not_confirmed", "联系图可见仰卧腿屈伸，手臂交替和对侧配合不清楚；不声称完整交替死虫变式已视觉核实。"),
        "0293": ("visual_match", "联系图可见站姿双手各持哑铃同时俯身划船，未见凳子。"),
        "0027": ("visual_match", "联系图可见站姿双手握杠铃俯身划船，无额外支撑。"),
    }
    for entry in mapped:
        original = by_id[entry["source_id"]]
        evidence = {}
        files = {x["source_path"]: x for x in verified.get(original["id"], {}).get("files", []) if "failure" not in x}
        for key in ("gif_url", "image"):
            path = original[key]
            if path in files:
                item = files[path]
                local = Path(item["local_path"])
                content = local.read_bytes()
                actual_sha = hashlib.sha1(b"blob " + str(len(content)).encode() + b"\0" + content).hexdigest()
                if actual_sha != tree_files[path]["sha"] or item["git_blob_sha1"] != actual_sha:
                    raise ValueError("Selected media Git blob mismatch")
                evidence[key] = {k: item[k] for k in ("git_blob_sha1", "byte_count", "dimensions", "frames", "distinct_frames", "resolution_constraint_met")}
        status, note = visual_notes[original["id"]]
        current.append({**entry, "stable_id": entry["local_id"], "name": original["name"], "source_name_zh": None, "display_name_zh": entry["local_name_zh"], "display_name_zh_origin": "Existing Ready Gym label, not an upstream translation", "instructions": {lang: original["instructions"][lang] for lang in ("en", "zh")}, "instruction_steps": {lang: original["instruction_steps"][lang] for lang in ("en", "zh")}, "equipment": original["equipment"], "target": original["target"], "muscle_group": original["muscle_group"], "secondary_muscles": original["secondary_muscles"], "source_commit": COMMIT, "media_match_status": status if evidence else "media_not_fetched", "visual_review_note_zh": note if evidence else "未取得媒体", "visual_review_scope": "five sampled GIF frames only, not safety or technique validation", "local_gif_path": "source-media/current-plan/" + Path(original["gif_url"]).name if "gif_url" in evidence else None, "local_image_path": "source-media/current-plan/" + Path(original["image"]).name if "image" in evidence else None, "media_verification": evidence, "media_gap": status == "visual_alternation_not_confirmed", "text_gap": original["id"] == "3211"})
    manifest["media_files_downloaded_by_metadata_importer"] = manifest.pop("media_files_downloaded")
    manifest["selected_media_files_fetched"] = sum(len(x.get("files", [])) for x in verification["media"] if all("failure" not in f for f in x.get("files", [])))
    manifest["selected_media_verification_file"] = "source-media/current-plan-verification.json"
    result = {"source_manifest": manifest, "exercise-catalog": {"schema_version": 1, "coverage": "curated_subset", "source_commit": COMMIT, "records": exported}, "exercise-mapping": {"schema_version": 1, "source_commit": COMMIT, "runtime_enabled": False, "mappings": mapped}, "media-reference-index": {"schema_version": 1, "source_commit": COMMIT, "metadata_only": True, "media_policy": dict(MEDIA_POLICY), "records": index}, "current-plan-media-mapping": {"schema_version": 1, "source_commit": COMMIT, "runtime_enabled": False, "contact_sheet": "source-media/current-plan/contact-sheet.jpg", "authorization_basis": MEDIA_POLICY["authorization_basis"], "permission_document_reviewed": False, "mappings": current}}
    return result

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", required=True, type=Path)
    parser.add_argument("--output-dir", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    result = build(args.source_dir, args.output_dir / "selection.json")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    for name, content in result.items():
        (args.output_dir / (name + ".json")).write_text(json.dumps(content, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    license_dir = args.output_dir / "SOURCE"
    license_dir.mkdir(exist_ok=True)
    for name in LICENSES:
        (license_dir / name).write_bytes((args.source_dir / name).read_bytes())
    print(json.dumps({"verified_records": result["source_manifest"]["source_record_count"], "exported_records": len(result["exercise-catalog"]["records"]), "mappings": len(result["exercise-mapping"]["mappings"]), "media_references": len(result["media-reference-index"]["records"]), "media_downloaded_by_metadata_importer": 0, "selected_media_files_fetched": result["source_manifest"]["selected_media_files_fetched"]}, ensure_ascii=False))
