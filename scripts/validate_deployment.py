"""Offline structural checks. Kubeconform supplies independent Kubernetes JSON-schema validation."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
compose=json.loads((ROOT/'deploy/compose.dev.json').read_text())
assert compose['name']=='medflow-demo-isolated'
dbs=[v for k,v in compose['services'].items() if k.endswith('-db')]
assert len(dbs)==12 and all(not db.get('ports') for db in dbs)
published=[p for s in compose['services'].values() for p in s.get('ports',[])]
assert set(published)=={'127.0.0.1:18080:8080','127.0.0.1:18761:8761','127.0.0.1:15173:80'}
assert all(v.get('environment','').startswith('DOCKER_') for v in compose['secrets'].values())
manifest=json.loads((ROOT/'deploy/k8s/resources.json').read_text())
assert manifest['kind']=='List'
items=manifest['items'];assert len(items)==59
assert all(r['metadata'].get('namespace')=='medflow-demo' for r in items if r['kind']!='Namespace')
for r in items:
    if r['kind'] in ('Deployment','StatefulSet'):
        assert r['spec']['selector']['matchLabels']==r['spec']['template']['metadata']['labels']
        for c in r['spec']['template']['spec']['containers']:
            assert c['resources']['limits']['memory'] and c['readinessProbe']
            for env in c.get('env',[]):
                if any(s in env['name'] for s in ('PASSWORD','JWT_SECRET','API_KEY')) and not env['name'].endswith('_FILE'):
                    assert 'secretKeyRef' in env.get('valueFrom',{}), 'Inline credential prohibited'
print('PASS isolated ports, 12 private database services, secret references, resource limits, selectors and readiness probes; 59 Kubernetes objects.')
