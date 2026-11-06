#!/usr/bin/env python3
"""標準エラーやログから、包囲で失ったユニットと、それを予測できていたかを集計する。
使い方: python3 sim/surroundstats.py <ファイル...>"""
import re, sys
pattern = re.compile(r"S(\d+) lost=(\d+) predicted=(\d+) riskBefore=(\d+) skipped=(\d+)")
events = lost = predicted = risk = skipped = 0
files = 0
for path in sys.argv[1:]:
    files += 1
    for turn, l, p, r, s in pattern.findall(open(path, encoding="utf8", errors="replace").read()):
        events += 1; lost += int(l); predicted += int(p); risk += int(r); skipped += int(s)
print(f"files={files} events={events} lost={lost} predicted={predicted} ({100*predicted/max(lost,1):.0f}%) "
      f"avgRiskAtLoss={risk/max(events,1):.1f} skipped={skipped} lost/file={lost/max(files,1):.1f}")
