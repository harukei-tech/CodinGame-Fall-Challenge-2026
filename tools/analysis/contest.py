import sys,re,glob,os,collections,statistics as st
sys.path.insert(0,sys.argv[1]); import replay as R
os.chdir(sys.argv[2])
def bfs(adj,s):
    d={s:0}; q=[s]
    for c in q:
        for n in adj[c]:
            if n not in d: d[n]=d[c]+1; q.append(n)
    return d
def surround(adj,u):
    v=[x[:] for x in u]
    adv=[{h for h in adj if u[h][i]>u[h][1-i]} for i in (0,1)]
    for i in (0,1):
        for h in adj:
            if u[h][i]>0 and adj[h] and all(n in adv[1-i] for n in adj[h]): v[h][i]=0
    return v
def capt(adj,tr,u,own,can,p):
    o={h:R.owner_of(h,adj,u) for h in adj}
    return {t for t in tr if own[t]!=p and can[t][p] and all(o[h]==p for h in t)}
A=collections.defaultdict(collections.Counter)   # (phase,result,role)->counters
need=collections.defaultdict(collections.Counter)
arr=collections.defaultdict(lambda:[0,0])
cov=[0,0,0]
def phase(t): return 6 if t<=10 else 11 if t<=15 else 16
for f in sorted(glob.glob('logs/*.log')):
    g=os.path.basename(f)[:-4]
    t=open(f,encoding='utf8',errors='replace').read()
    me=0 if re.search(r'^  0 .*\(me,',t,re.M) else 1
    sc=re.search(r'scores: 0=(-?\d+) 1=(-?\d+)',t); s=[int(sc[1]),int(sc[2])]; win=s[me]>s[1-me]
    pts,adj,u,outs,log,T=R.load(g)
    tr=R.tris(pts,adj); own={x:None for x in tr}; can={x:[True,True] for x in tr}
    cov[0]+=1
    for x in tr:
        for i in (0,1):
            if all(R.owner_of(h,adj,u)==i for h in x): own[x]=i; can[x]=[i!=0,i!=1]
    TT={int(a):tuple(map(int,b)) for a,*b in re.findall(r'\bT(\d+) score=\S+ units=\S+ tri=(\d+)/(\d+)/(\d+)',t)}
    if TT.get(1,(0,0,len(tr)))[2]!=len(tr): print('TRICOUNT',g,len(tr),TT.get(1))
    for turn in range(1,21):
        if turn>min(len(outs[0]),len(outs[1])): break
        cmds=[[c.strip() for c in outs[p][turn-1].split(';')] for p in (0,1)]
        if any('_PATH' in c for p in (0,1) for c in cmds[p]): cov[2]+=1; break
        if turn in T:
            a=(sum(x[me] for x in u),sum(x[1-me] for x in u))
            b2=(sum(own[x]==me for x in tr),sum(own[x]==1-me for x in tr))
            if a!=T[turn] or (turn in TT and b2!=TT[turn][:2]):
                cov[1]+=1; print('MIS',g,'turn',turn,'units',a,T[turn],'tri',b2,TT.get(turn)); break
        init=dict(own); rem=[x[:] for x in u]
        for p in (0,1):
            for c in cmds[p]:
                w=c.split()
                if w and w[0]=='ATTACK':
                    tt=tuple(sorted(map(int,w[1:4]))); tg=int(w[4])
                    if tt in own and own[tt]==p and all(rem[h][p]>=1 for h in tt):
                        for h in tt: u[h][p]-=1; rem[h][p]-=1
                        own[tt]=None; can[tt][p]=False; u[tg][1-p]=0; rem[tg][1-p]=0
        start=[x[:] for x in u]
        for p in (0,1):
            mv=[]
            for c in cmds[p]:
                w=c.split()
                if w and w[0]=='MOVE':
                    a,b,n=map(int,w[1:4]); n=min(n,rem[a][p])
                    if n>0 and a!=b:
                        d=bfs(adj,b); nx=next((x for x in sorted(adj[a]) if d.get(x,99)<d.get(a,99)),None)
                        if nx is not None: rem[a][p]-=n; mv.append((a,nx,n))
            for a,b,n in mv: u[a][p]-=n; u[b][p]+=n
        sp=collections.Counter()
        for p in (0,1):
            for c in cmds[p]:
                w=c.split()
                if w and w[0]=='SPAWN':
                    hs=list(map(int,w[1:4])); tt=tuple(sorted(hs))
                    if tt in own and own[tt]==p and init[tt]==p:
                        own[tt]=None; can[tt][p]=False; u[hs[0]][p]+=1; sp[(hs[0],p)]+=1
        for x in tr:
            for i in (0,1):
                if own[x] is not None: can[x][i]=own[x]!=i
                else: can[x][i]=can[x][i] or all(u[h][i]==0 for h in x)
        pre=[x[:] for x in u]
        if turn>=6:
            for p,role in ((me,'me'),(1-me,'op')):
                q=1-p; key=(phase(turn),win,role); C=A[key]; C['turns']+=1
                # 敵が今ターン動かなかった場合と実際の比較
                cf=[x[:] for x in pre]
                for h in adj: cf[h][q]=start[h][q]+sp[(h,q)]
                cfS=surround(adj,cf); acS=surround(adj,pre)
                a=capt(adj,tr,cfS,own,can,p); b=capt(adj,tr,acS,own,can,p)
                C['actual']+=len(b); C['cf']+=len(a); C['disrupted']+=len(a-b)
                o={h:R.owner_of(h,adj,acS) for h in adj}
                for x in a-b:
                    blocked=[h for h in x if o[h]!=p]
                    tot=sum(max(1,acS[h][q]-acS[h][p]+1) for h in blocked)
                    need[key][min(tot,4)]+=1
                    need[key]['corners%d'%min(len(blocked),3)]+=1
                # 無駄ユニット: 敵と同居し多数でない
                tot=sum(x[p] for x in acS); w=sum(acS[h][p] for h in adj if 0<acS[h][p]<=acS[h][q])
                C['units']+=tot; C['wasted']+=w
                C['stackextra']+=sum(max(0,acS[h][p]-max(1,acS[h][q]+1)) for h in adj)
                # 敵がいない三角形
                reach=lambda h,i:start[h][i]>0 or any(start[n][i]>0 for n in adj[h])
                avail=[x for x in tr if own[x]!=p and init[x]!=p and can[x][p]]
                C['avail']+=len(avail)
                C['enemyOnCorner']+=sum(any(start[h][q]>0 for h in x) for x in avail)
                safe=[x for x in avail if not any(reach(h,q) for h in x)]
                C['safe']+=len(safe)
                C['safeReach']+=sum(all(reach(h,p) for h in x) for x in safe)
                C['safeTaken']+=sum(x in b for x in safe)
                # 敵の到達予測
                hot=set()
                for x in tr:
                    if own[x]!=q and can[x][q] and all(reach(h,q) for h in x): hot.update(x)
                for h in adj:
                    th=sum(start[n][q] for n in adj[h])
                    if th==0 or start[h][q]>0: continue
                    came=pre[h][q]-start[h][q]>0
                    arr[('hot' if h in hot else 'cold',role)][0]+=came; arr[('hot' if h in hot else 'cold',role)][1]+=1
        S=surround(adj,u); u[:]=S
        for x in tr:
            for i in (0,1):
                if own[x] is not None: can[x][i]=own[x]!=i
                else: can[x][i]=can[x][i] or all(u[h][i]==0 for h in x)
        for x in tr:
            for i in (0,1):
                if can[x][i] and all(R.owner_of(h,adj,u)==i for h in x): own[x]=i
print('games',cov[0],'stopped by mismatch',cov[1],'by PATH',cov[2])
print('phase res role turns | actual cf disrupted /turn | wasted% extra/turn | avail enemyOnCorner safe safeReach safeTaken /turn')
for k in sorted(A,key=str):
    C=A[k]; n=C['turns']
    print(k[0],'W' if k[1] else 'L',k[2],n,f"| {C['actual']/n:.2f} {C['cf']/n:.2f} {C['disrupted']/n:.2f} | {100*C['wasted']/max(1,C['units']):.0f}% {C['stackextra']/n:.2f} | {C['avail']/n:.1f} {C['enemyOnCorner']/n:.1f} {C['safe']/n:.2f} {C['safeReach']/n:.2f} {C['safeTaken']/n:.2f}")
print('\nextra units needed to save each disrupted triangle (me only)')
for k in sorted(need,key=str):
    if k[2]!='me': continue
    c=need[k]; n=sum(c[i] for i in (1,2,3,4))
    print(k[0],'W' if k[1] else 'L',n,{i:f'{100*c[i]/n:.0f}%' for i in (1,2,3,4)},'blocked corners',{j:f"{100*c['corners%d'%j]/n:.0f}%" for j in (1,2,3)})
print('\nenemy arrival on empty threatened house: role=whose enemy')
for k,v in sorted(arr.items()): print(k,f'{v[0]}/{v[1]} = {100*v[0]/v[1]:.1f}%')
