#!/usr/bin/env bash
# index-filter for git filter-branch: drop janko puzzles from one commit's index.
set -e
git rm -r --cached --ignore-unmatch -q puzzles/janko-shorties
F=core/src/commonMain/kotlin/org/bytefred/satchess/puzzle/ExamplePuzzles.kt
line=$(git ls-files -s -- "$F")
[ -z "$line" ] && exit 0
mode=$(echo "$line" | cut -d' ' -f1); sha=$(echo "$line" | cut -d' ' -f2)
new=$(git cat-file blob "$sha" | python3 -c '
import re,sys
s=sys.stdin.read()
s=re.sub(r"    ExampleSource\(\"[^\"]*\", \"\"\"\{\n  \"id\": \"janko-shorties/.*?\}\"\"\"\),?\n", "", s, flags=re.S)
sys.stdout.write(s)' | git hash-object -w --stdin)
git update-index --cacheinfo "$mode,$new,$F"
