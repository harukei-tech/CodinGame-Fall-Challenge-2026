#!/usr/bin/env python3
"""STDERR_DIR に保存した標準エラーから、ロック中の待機を区間ごとに集計する。
使い方: python3 sim/lockstats.py [ディレクトリ]"""
import glob, os, re, sys
directory = sys.argv[1] if len(sys.argv) > 1 else "sim/results/stderr"
ranges = [(1, 20), (21, 40), (41, 60), (61, 100), (101, 200)]
sums = {r: [0, 0, 0, 0, 0, 0, 0] for r in ranges}
pattern_l = re.compile(r"L(\d+) locked=(\d+) waiting=(\d+) hubWaiting=(\d+) blocked=(\d+)(?: exposed=(\d+))?")
pattern_t = re.compile(r"T(\d+) score=\S+ units=(\d+)/")
for path in glob.glob(os.path.join(directory, "*-[01].txt")):
    units = {}
    text = open(path, encoding="utf8").read()
    for turn, mine in pattern_t.findall(text):
        units[int(turn)] = int(mine)
    for turn, locked, waiting, hub, blocked, exposed in pattern_l.findall(text):
        turn = int(turn)
        for r in ranges:
            if r[0] <= turn <= r[1]:
                row = sums[r]
                row[0] += 1
                for i, value in enumerate((locked, waiting, hub, blocked)):
                    row[i + 1] += int(value)
                row[5] += units.get(turn, 0)
                row[6] += int(exposed or 0)
print("turns      samples locked waiting hubWaiting blocked units exposed")
for r in ranges:
    row = sums[r]
    n = max(row[0], 1)
    print(f"{r[0]:>3}-{r[1]:<3} {row[0]:>9} {row[1]/n:6.1f} {row[2]/n:7.1f} {row[3]/n:10.1f} {row[4]/n:7.1f} {row[5]/n:5.1f} {row[6]/n:7.1f}")
