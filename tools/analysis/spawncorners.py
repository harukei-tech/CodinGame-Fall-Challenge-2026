#!/usr/bin/env python3
"""SPAWNの生まれる角を集計する。行き止まりの角（隣の家がすべて同じ三角形の角）を選んだ割合と、次のターンにそこから動いたか。
使い方: python3 tools/analysis/spawncorners.py <logsのあるディレクトリ>"""
import re,glob,sys,os,collections
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__))); import replay as R
os.chdir(sys.argv[1])
C=collections.Counter()
for f in sorted(glob.glob('logs/*.log')):
    g=os.path.basename(f)[:-4]; t=open(f,encoding='utf8',errors='replace').read()
    me=0 if re.search(r'^  0 .*\(me,',t,re.M) else 1
    pts,adj,u,outs,log,T=R.load(g)
    for p,role in ((me,'me'),(1-me,'op')):
        o=outs[p]
        for i,line in enumerate(o[:60]):
            ph='T1-8' if i<8 else 'T9-20' if i<20 else 'T21-60'
            for a,b,c in re.findall(r'SPAWN (\d+) (\d+) (\d+)',line):
                a,b,c=int(a),int(b),int(c); tri={a,b,c}
                dead=lambda h:all(n in tri for n in adj[h])
                C[(role,ph,'spawns')]+=1
                if dead(a):
                    C[(role,ph,'deadEnd')]+=1
                    if not (dead(b) and dead(c)): C[(role,ph,'deadEndAvoidable')]+=1
                    nxt=o[i+1] if i+1<len(o) else ''
                    if re.search(rf'MOVE {a} \d+ \d+',nxt): C[(role,ph,'deadEndLeftNext')]+=1
for role in ('me','op'):
    for ph in ('T1-8','T9-20','T21-60'):
        n=C[(role,ph,'spawns')]; d=C[(role,ph,'deadEnd')]
        if n: print(role,ph,'spawns',n,'deadEnd %.1f%%'%(100*d/n),'avoidable',C[(role,ph,'deadEndAvoidable')],'left next turn %.0f%%'%(100*C[(role,ph,'deadEndLeftNext')]/max(d,1)))
