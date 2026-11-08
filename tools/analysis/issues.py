import sys,re,glob,os,collections,statistics as st
sys.path.insert(0,sys.argv[1]); import replay as R
os.chdir(sys.argv[2]); MAXT=40
def bfs(adj,s):
    d={x:0 for x in s}; q=list(d)
    for c in q:
        for n in adj[c]:
            if n not in d: d[n]=d[c]+1; q.append(n)
    return d
def surround(adj,u):
    v=[x[:] for x in u]; adv=[{h for h in adj if u[h][i]>u[h][1-i]} for i in (0,1)]; dead=[]
    for i in (0,1):
        for h in adj:
            if u[h][i]>0 and adj[h] and all(n in adv[1-i] for n in adj[h]): dead.append((h,i,u[h][i])); v[h][i]=0
    return v,dead
ph=lambda t:'T6-10' if t<=10 else 'T11-15' if t<=15 else 'T16-20' if t<=20 else 'T21-30' if t<=30 else 'T31-40'
C=collections.defaultdict(collections.Counter); stack=collections.defaultdict(collections.Counter); lockage=collections.defaultdict(collections.Counter); att=collections.Counter(); cov=collections.Counter()
for f in sorted(glob.glob('logs/*.log')):
    g=os.path.basename(f)[:-4]; t=open(f,encoding='utf8',errors='replace').read()
    me=0 if re.search(r'^  0 .*\(me,',t,re.M) else 1
    sc=re.search(r'scores: 0=(-?\d+) 1=(-?\d+)',t); s=[int(sc[1]),int(sc[2])]; win=s[me]>s[1-me]; res='W' if win else 'L'
    pts,adj,u,outs,log,T=R.load(g); tr=R.tris(pts,adj)
    own={x:None for x in tr}; can={x:[True,True] for x in tr}
    for x in tr:
        for i in (0,1):
            if all(R.owner_of(h,adj,u)==i for h in x): own[x]=i; can[x]=[i!=0,i!=1]
    TT={int(a):tuple(map(int,b)) for a,*b in re.findall(r'\bT(\d+) score=\S+ units=\S+ tri=(\d+)/(\d+)/(\d+)',t)}
    lockstart=[{},{}]; stayed=collections.Counter()
    for turn in range(1,MAXT+1):
        if turn>min(len(outs[0]),len(outs[1])): break
        cmds=[[c.strip() for c in outs[p][turn-1].split(';')] for p in (0,1)]
        if turn in T:
            a=(sum(x[me] for x in u),sum(x[1-me] for x in u)); b2=(sum(own[x]==me for x in tr),sum(own[x]==1-me for x in tr))
            if a!=T[turn] or (turn in TT and b2!=TT[turn][:2]): cov['mis']+=1; break
        if any('_PATH' in c for p in (0,1) for c in cmds[p]): cov['path']+=1; break
        cov['turns'+ph(turn)]+=(turn>=6)
        P=ph(turn); K=(P,res)
        init=dict(own); rem=[x[:] for x in u]
        startU=[x[:] for x in u]
        # 攻撃可能な塊と実際の攻撃
        for p in (0,1):
            q=1-p
            if turn>=6:
                for x in tr:
                    if own[x]==q and all(u[h][q]>=1 for h in x):
                        tg={n for h in x for n in adj[h]}
                        for h in tg:
                            if u[h][p]>=4: att[('exposed',p==me)]+=1
        for p in (0,1):
            for c in cmds[p]:
                w=c.split()
                if w and w[0]=='ATTACK':
                    tt=tuple(sorted(map(int,w[1:4]))); tg=int(w[4])
                    if tt in own and own[tt]==p and all(rem[h][p]>=1 for h in tt):
                        if turn>=6: att[('hit',1-p==me)]+=(u[tg][1-p]>=4); att[('killed',1-p==me)]+=u[tg][1-p]; att[('attacks',p==me)]+=1
                        for h in tt: u[h][p]-=1; rem[h][p]-=1
                        own[tt]=None; can[tt][p]=False; u[tg][1-p]=0; rem[tg][1-p]=0
        moved=collections.Counter()
        for p in (0,1):
            mv=[]
            for c in cmds[p]:
                w=c.split()
                if w and w[0]=='MOVE':
                    a,b,n=map(int,w[1:4]); n=min(n,rem[a][p])
                    if n>0 and a!=b:
                        d=bfs(adj,[b]); nx=next((x for x in sorted(adj[a]) if d.get(x,99)<d.get(a,99)),None)
                        if nx is not None: rem[a][p]-=n; mv.append((a,nx,n))
            for a,b,n in mv: u[a][p]-=n; u[b][p]+=n
        for p in (0,1):
            for c in cmds[p]:
                w=c.split()
                if w and w[0]=='SPAWN':
                    hs=list(map(int,w[1:4])); tt=tuple(sorted(hs))
                    if tt in own and own[tt]==p and init[tt]==p:
                        own[tt]=None; can[tt][p]=False; u[hs[0]][p]+=1; lockstart[p][tt]=turn
        def upd():
            for x in tr:
                for i in (0,1):
                    if own[x] is not None: can[x][i]=own[x]!=i
                    else:
                        if not can[x][i] and all(u[h][i]==0 for h in x):
                            can[x][i]=True
                            if x in lockstart[i]:
                                age=turn-lockstart[i].pop(x); lockage[('me' if i==me else 'op',res)][min(age,8)]+=1
        upd(); pre=[x[:] for x in u]
        if turn>=6:
            for p,role in ((me,'me'),(1-me,'op')):
                q=1-p; KK=(P,res,role); c=C[KK]; c['turns']+=1
                mine=[h for h in adj if pre[h][p]>0]; his=[h for h in adj if pre[h][q]>0]
                if not mine: continue
                dm=bfs(adj,mine); dh=bfs(adj,his) if his else {}
                tot=sum(pre[h][p] for h in adj); c['units']+=tot
                # スタック
                for h in mine:
                    stack[(P,res,role)][min(pre[h][p],6)]+=pre[h][p]
                # 孤立: 他の味方から2歩以内にいない家
                for h in mine:
                    others=[n for n in adj[h] if pre[n][p]>0]
                    two=any(pre[m][p]>0 for n in adj[h] for m in adj[n] if m!=h)
                    if not others and not two: c['isolated']+=pre[h][p]
                # 閉域: 占領できる三角形へ、敵が多数の家を通らずに届かないユニット
                free={h for x in tr if own[x]!=p and can[x][p] for h in x}
                blocked={h for h in adj if pre[h][q]>pre[h][p]}
                src=[h for h in free if h not in blocked]
                adj2={h:[n for n in adj[h] if n not in blocked] for h in adj}
                dd=bfs(adj2,src) if src else {}
                far=[h for h in mine if dd.get(h,99)>2]
                c['noTargetNear']+=sum(pre[h][p] for h in far)
                c['closed']+=sum(pre[h][p] for h in mine if h not in dd)
                # ロック
                lk=[x for x in tr if own[x]!=p and not can[x][p]]
                c['locked']+=len(lk)
                c['lockOneCorner']+=sum(sum(pre[h][p]>0 for h in x)==1 for x in lk)
                # 停滞: そのターン動かなかった数
                c['stayed']+=sum(max(0,startU[h][p]-(rem[h][p]<startU[h][p])*0) and rem[h][p] for h in adj)
        u[:],dead=surround(adj,u)
        if turn>=6:
            for h,i,n in dead:
                role='me' if i==me else 'op'; c=C[(P,res,role)]
                c['surrDead']+=n
                mine=[x for x in adj if pre[x][i]>0 and x!=h]
                dmin=min((bfs(adj,[h]).get(x,99) for x in mine),default=99)
                c['surrIsolated']+=n if dmin>=2 else 0
                his=[x for x in adj if startU[x][1-i]>0]; ms=[x for x in adj if startU[x][i]>0]
                d1=bfs(adj,ms)[h] if ms else 9; 
                c['surrStayed']+=n if startU[h][i]>=n else 0
                c['surrN%d'%min(n,4)]+=1
        upd()
        for x in tr:
            for i in (0,1):
                if can[x][i] and all(R.owner_of(h,adj,u)==i for h in x): own[x]=i
print({k:v for k,v in cov.items()})
print('phase res role turns | units/turn | surrDead/game-phase isolatedDead stayedDead | isolated% noTargetNear% closed% stayed% | locked oneCorner')
for k in sorted(C):
    c=C[k]; n=max(c['turns'],1); uu=max(c['units'],1)
    print(k[0],k[1],k[2],n,f"| {uu/n:5.1f} | dead/turn {c['surrDead']/n:.2f} isoDead {100*c['surrIsolated']/max(1,c['surrDead']):.0f}% stay {100*c['surrStayed']/max(1,c['surrDead']):.0f}% n1..4 {[c['surrN%d'%i] for i in (1,2,3,4)]} | iso {100*c['isolated']/uu:.0f}% far {100*c['noTargetNear']/uu:.0f}% closed {100*c['closed']/uu:.0f}% stay {100*c['stayed']/uu:.0f}% | lock {c['locked']/n:.1f} one {c['lockOneCorner']/n:.1f}")
print('\nshare of units by stack size 1,2,3,4,5,6+')
for k in sorted(stack):
    if k[1]!='L': continue
    s=stack[k]; tt=sum(s.values()); print(k,[round(100*s[i]/tt) for i in range(1,7)])
print('\nlock age at release'); 
for k in sorted(lockage):
    s=lockage[k]; tt=sum(s.values()); print(k,tt,'mean %.2f'%(sum(a*b for a,b in s.items())/tt),[round(100*s[i]/tt) for i in range(1,9)])
print('\nattack',dict(att))
