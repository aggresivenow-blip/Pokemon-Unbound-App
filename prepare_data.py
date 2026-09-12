#!/usr/bin/env python3
"""Fetch the versioned Unbound Field Guide dataset at build time.
The APK itself has no network dependency; this script runs only before compilation.
"""
import hashlib, json, pathlib, urllib.request, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'app' / 'src' / 'main' / 'assets' / 'data'
OUT.mkdir(parents=True, exist_ok=True)
URL = 'https://raw.githubusercontent.com/jimineybillybob1/pokemon-unbound-guide/master/data/unbound-data.json'
RAW = OUT / 'unbound-data.json'
MANIFEST = OUT / 'source-manifest.json'

req = urllib.request.Request(URL, headers={'User-Agent': 'UnboundDex-build/1.0'})
last = None
for attempt in range(1, 4):
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            data = r.read()
        if len(data) < 100_000:
            raise RuntimeError(f'dataset unexpectedly small: {len(data)} bytes')
        RAW.write_bytes(data)
        break
    except Exception as exc:
        last = exc
        if attempt == 3:
            print(f'ERROR: unable to fetch dataset: {exc}', file=sys.stderr)
            raise

sha = hashlib.sha256(data).hexdigest()
obj = json.loads(data)
counts = obj.get('sourceCounts', {}) if isinstance(obj, dict) else {}
MANIFEST.write_text(json.dumps({
    'source': URL,
    'sha256': sha,
    'bytes': len(data),
    'game': obj.get('game') if isinstance(obj, dict) else None,
    'version': obj.get('version') if isinstance(obj, dict) else None,
    'sourceCounts': counts,
    'generatedBy': 'UnboundDex/tools/prepare_data.py'
}, indent=2), encoding='utf-8')
print(f'Fetched {len(data):,} bytes; sha256={sha}')
print(json.dumps(counts, indent=2))
