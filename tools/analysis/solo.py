import sys,re,os,random,json,collections,glob
from multiprocessing import Pool
sys.path.insert(0,sys.argv[1]); import replay as R
os.chdir(sys.argv[2]); H=int(sys.argv[3]) if len(sys.argv)>3 else 7   # T1..H
def owned_set(adj,cnt):
    o=set()
    occ=lambda h:cnt.get(h,0)>0
    for h in adj:
        if occ(h) or (adj[h] and all(occ(n) for n in adj[h])): o.add(h)
    return o
def step(adj,tr,trc,state,dest,spawnc):
    # state=(units tuple, owned frozenset, locked frozenset)
    units,own,lock=state
    cnt=collections.Counter(dest)
    own2=set(own); lock2=set(lock)
    for x,c in spawnc.items():
        own2.discard(x); lock2.add(x); cnt[c]+=1
    # 解除は移動とSPAWNの後の配置で判定
    for x in list(lock2):
        if not any(cnt.get(h,0) for h in x): lock2.discard(x)
    o=owned_set(adj,cnt); cap=0
    for x in tr:
        if x in own2 or x in lock2: continue
        if x[0] in o and x[1] in o and x[2] in o: own2.add(x); cap+=1
    return (tuple(sorted(cnt.elements())),frozenset(own2),frozenset(lock2)),cap
def solve(g,seat,iters=250,beam=40,seed=1):
    random.seed(seed)
    pts,adj,u,outs,log,T=R.load(g); tr=R.tris(pts,adj)
    units=tuple(sorted(h for h in adj for _ in range(u[h][seat])))
    c0=collections.Counter(units); o0=owned_set(adj,c0)
    own0=frozenset(x for x in tr if all(h in o0 for h in x))
    B=[((units,own0,frozenset()),0)]
    opts={h:list(adj[h])+[h] for h in adj}
    for t in range(1,H+1):
        nxt={}
        for st,cum in B:
            us,own,lock=st
            for _ in range(iters):
                dest=[random.choice(opts[h]) for h in us]
                sp={x:random.choice(x) for x in own}
                s2,c=step(adj,tr,None,st,dest,sp)
                # 簡単な山登り: 1体ずつ変えて占領が増えるなら採用
                for k in range(len(us)*2):
                    i=random.randrange(len(us)); old=dest[i]; dest[i]=random.choice(opts[us[i]])
                    s3,c3=step(adj,tr,None,st,dest,sp)
                    if c3>=c: s2,c=s3,c3
                    else: dest[i]=old
                sc=len(s2[0])+len(s2[1])
                key=s2
                v=(sc,cum+c)
                if key not in nxt or nxt[key][0]<v: nxt[key]=(v,cum+c)
        top=sorted(nxt.items(),key=lambda kv:(kv[1][0],-len(kv[0][2])),reverse=True)[:beam]
        B=[(k,v[1]) for k,v in top]
    best=max(B,key=lambda b:(len(b[0][0])+len(b[0][1]),b[1]))
    return g,seat,len(best[0][0]),len(best[0][1]),best[1]
if __name__=='__main__':
    E=json.load(open(sys.argv[4])); rows=[r for r in E if str(H+1) in r['F']]
    random.seed(7); rows=random.sample(rows,min(int(sys.argv[5]),len(rows)))
    jobs=[]
    for r in rows:
        t=open(f"logs/{r['g']}.log",encoding='utf8',errors='replace').read()
        me=0 if re.search(r'^  0 .*\(me,',t,re.M) else 1
        jobs.append((r['g'],me))
    with Pool(max(1,os.cpu_count()-2)) as P: out=P.starmap(solve,jobs)
    m={r['g']:r for r in rows}; tot=[]
    for g,seat,nu,no,cum in out:
        a,b=m[g]['F'][str(H+1)]
        print(g,'W' if m[g]['win'] else 'L',f"solo units+tri {nu}+{no} cap {cum} | me {a['units']}+{a['ownedTri']} cap {a['cumCap']} | opp {b['units']}+{b['ownedTri']} cap {b['cumCap']}")
        tot.append((nu+no,cum,a['units']+a['ownedTri'],a['cumCap'],b['units']+b['ownedTri'],b['cumCap']))
    n=len(tot); print('MEAN units+tri solo/me/opp',[round(sum(x[i] for x in tot)/n,1) for i in (0,2,4)],'cap solo/me/opp',[round(sum(x[i] for x in tot)/n,1) for i in (1,3,5)])
