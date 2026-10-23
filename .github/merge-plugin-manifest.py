# Usage: merge-plugin-manifest.py <manifest.json of the new build> <repository manifest.json>
# Writes the new manifest into the repository one, keeping the repository's older versions after the new ones, so
# servers on older Jellyfin versions still find a version matching their ABI.
import json, os, sys

new_path, repository_path = sys.argv[1:]
with open(new_path) as f:
    manifest = json.load(f)
old_versions = []
if os.path.exists(repository_path):
    with open(repository_path) as f:
        old_versions = next(iter(json.load(f)), {}).get("versions", [])
new_versions = manifest[0]["versions"]
known = {v["version"] for v in new_versions}
new_versions += [v for v in old_versions if v["version"] not in known]
with open(repository_path, "w") as f:
    json.dump(manifest, f, indent=2)
