"""Heuristic text scan of Git-visible files. Reports location/type only, never matching values."""
from pathlib import Path
import subprocess,re,sys
ROOT=Path(__file__).resolve().parent.parent
files=subprocess.check_output(['git','ls-files','-z','--cached','--others','--exclude-standard'],cwd=ROOT).decode().split('\0')
patterns={
 'Google API token':re.compile(r'AIza[0-9A-Za-z_-]{35}'),
 'GitHub token':re.compile(r'(?:ghp_|github_pat_)[0-9A-Za-z_]{30,}'),
 'AWS access-key ID':re.compile(r'AKIA[0-9A-Z]{16}'),
 'Private-key material':re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
 'Literal JWT':re.compile(r'eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{12,}'),
}
extensions={'.java','.js','.jsx','.json','.xml','.yml','.yaml','.md','.ps1','.py','.css','.sh','.properties'}
hits=[];count=0
for name in sorted(set(files)):
    if not name:continue
    p=ROOT/name
    if not p.is_file() or p.suffix not in extensions:continue
    count+=1
    for number,line in enumerate(p.read_text(encoding='utf-8',errors='replace').splitlines(),1):
        for label,pattern in patterns.items():
            if pattern.search(line):hits.append((name,number,label))
for name,line,label in hits:print(f'REVIEW {name}:{line}: {label}; value withheld')
print(f'Heuristic secret scan: {count} Git-visible text files, {len(hits)} potential matches. Ignored runtime files are excluded.')
sys.exit(bool(hits))
