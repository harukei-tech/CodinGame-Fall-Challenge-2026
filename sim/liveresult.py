#!/usr/bin/env python3
"""本番のテスト対戦ログをまとめて集計する。使い方: python3 sim/liveresult.py <試合ID...>"""
import re, sys
spawns = [0, 0]; wins = 0; t20 = []; lost_s = kill_s = lost_a = kill_a = 0; n = 0
for g in sys.argv[1:]:
    t = open(f"logs/{g}.log", encoding="utf8", errors="replace").read()
    n += 1
    m = re.search(r"scores: 0=(-?\d+) 1=(-?\d+)", t)
    wins += int(m.group(1)) > int(m.group(2))
    u = re.search(r"\bT20 score=\S+ units=(\d+)/(\d+)", t)
    if u: t20.append((int(u.group(1)), int(u.group(2))))
    for agent in (0, 1):
        outputs = re.findall(rf"frame \d+ agent {agent}\n(?:  stderr:.*\n(?:      .*\n)*)?  stdout: (.*)", t)
        spawns[agent] += sum(o.count("SPAWN") for o in outputs[:20])
    total = lambda p: sum(int(x) for x in re.findall(p, t))
    lost_s += total(r"\$1 surrounded (\d+)"); kill_s += total(r"\$0 surrounded (\d+)")
    lost_a += total(r"\$1 attacked (\d+)"); kill_a += total(r"\$0 attacked (\d+)")
d = max(n, 1); k = max(len(t20), 1)
print(f"games={n} wins={wins} T20units={sum(a for a,b in t20)/k:.1f}/{sum(b for a,b in t20)/k:.1f} "
      f"spawn20={spawns[0]/d:.1f}/{spawns[1]/d:.1f} surround lost/kill={lost_s/d:.1f}/{kill_s/d:.1f} attack lost/kill={lost_a/d:.1f}/{kill_a/d:.1f}")
