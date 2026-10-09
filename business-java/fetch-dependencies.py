#!/usr/bin/env python3
"""One Maven Central acquisition; verify SHA-1 and ZIP, lock SHA-256."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import hashlib, json, urllib.request, zipfile
ROOT = Path(__file__).resolve().parent
ARTIFACTS = [
 ('com/fasterxml/jackson/core', 'jackson-core', '2.18.3'),
 ('com/fasterxml/jackson/core', 'jackson-annotations', '2.18.3'),
 ('com/fasterxml/jackson/core', 'jackson-databind', '2.18.3'),
 ('org/postgresql', 'postgresql', '42.7.5'),
 ('com/h2database', 'h2', '2.3.232'),
 ('org/junit/platform', 'junit-platform-console-standalone', '1.11.4'),
]

def fetch(a):
 group, name, version = a
 filename = f'{name}-{version}.jar'
 url = f'https://repo.maven.apache.org/maven2/{group}/{name}/{version}/{filename}'
 jar = urllib.request.urlopen(url, timeout=30).read()
 expected = urllib.request.urlopen(url + '.sha1', timeout=30).read().decode().strip().split()[0]
 assert hashlib.sha1(jar).hexdigest() == expected, f'upstream checksum mismatch: {filename}'
 folder = ROOT / 'lib'
 folder.mkdir(exist_ok=True)
 dest = folder / filename
 dest.write_bytes(jar)
 with zipfile.ZipFile(dest) as z:
  assert z.testzip() is None
  assert 'META-INF/MANIFEST.MF' in z.namelist()
 return {'file': filename, 'url': url, 'upstream_sha1': expected, 'sha256': hashlib.sha256(jar).hexdigest(), 'bytes': len(jar)}

if __name__ == '__main__':
 with ThreadPoolExecutor(max_workers=3) as pool:
  items = list(pool.map(fetch, ARTIFACTS))
 (ROOT / 'dependencies.lock.json').write_text(json.dumps(items, indent=2) + '\n')
 for item in items:
  print(f"VERIFIED {item['file']} SHA256={item['sha256']} bytes={item['bytes']}")
