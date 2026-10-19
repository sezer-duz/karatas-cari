#!/usr/bin/env python3
"""Extract the meaningful Gradle failure section from a captured full build log.
This is diagnostic only and does not claim to fix the failure.
"""
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    sys.exit("Usage: extract_gradle_error.py PATH_TO_GRADLE_LOG")
text = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
lines = text.splitlines()
print("=== GRADLE HATA OZETI ===")
markers = [i for i, s in enumerate(lines) if s.strip().startswith("* What went wrong:")]
if markers:
    i = markers[0]
    result = []
    for line in lines[i:i + 65]:
        if line.strip() in ("* Try:", "* Exception is:", "* Get more help at https://help.gradle.org") and len(result) > 2:
            break
        result.append(line)
    print("\n".join(result))
else:
    hits = [i for i, s in enumerate(lines) if re.search(r"(^|\s)(error:|FAILURE:|Execution failed|Could not resolve|A failure occurred)", s, re.I)]
    if hits:
        i = hits[0]
        print("\n".join(lines[max(0, i-4): min(len(lines), i+40)]))
    else:
        print("Gradle hatasi logda ayristirilamadi; asagida son 60 satir var:")
        print("\n".join(lines[-60:]))
