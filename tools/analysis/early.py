import sys,re,glob,os,collections,statistics as st,json
sys.path.insert(0,sys.argv[1]); import replay as R
os.chdir(sys.argv[2]); MAXT=12
def bfs(adj,s):
    d={x:0 for x in (s if isinstance(s,(list,set)) else [s])}; q=list(d)
    for c in q:
        for n in adj[c]:
            if n not in d: d[n]=d[c]+1; q.append(n)
    return d
def surround(adj,u):
    v=[x[:] for x in u]; adv=[{h for h in adj if u[h][i]>u[h][1-i]} for i in (0,1)]
    for i in (0,1):
        for h in adj:
            if u[h][i]>0 and adj[h] and all(n in adv[1-i] for n in adj[h]): v[h][i]=0
    return v
def feats(adj,tr,u,own,can,cum,p):
    q=1-p; f={}
    mine=[h for h in adj if u[h][p]>0]; his=[h for h in adj if u[h][q]>0]
    f['units']=sum(u[h][p] for h in adj); f['cumCap']=cum['cap'][p]; f['cumSpawn']=cum['sp'][p]
    f['ownedTri']=sum(own[x]==p for x in tr)
    f['lockedTri']=sum(1 for x in tr if own[x]!=p and not can[x][p])
    f['occupied']=len(mine); f['stackExcess']=f['units']-len(mine)
    dm=bfs(adj,mine) if mine else {}; dh=bfs(adj,his) if his else {}
    near=[h for h in adj if dm.get(h,99)<dh.get(h,99)]
    f['voronoiHouses']=len(near)
    ns=set(near); f['voronoiTri']=sum(all(h in ns for h in x) for x in tr)
    f['voronoiFreeTri']=sum(all(h in ns for h in x) and own[x]!=p and can[x][p] for x in tr)
    r1={h for h in adj if u[h][p]>0 or any(u[n][p]>0 for n in adj[h])}
    f['reachFreeTri']=sum(own[x]!=p and can[x][p] and all(h in r1 for h in x) for x in tr)
    deg=collections.Counter(h for x in tr for h in x)
    f['hubUnits']=sum(u[h][p] for h in adj if deg[h]>=4)
    f['meanTriPerUnit']=sum(u[h][p]*deg[h] for h in adj)/max(1,f['units'])
    f['contact']=sum(u[h][p] for h in adj if u[h][q]>0 or any(u[n][q]>0 for n in adj[h]))
    return f
rows=[];cov=collections.Counter()
for f in sorted(glob.glob('logs/*.log')):
    g=os.path.basename(f)[:-4]; t=open(f,encoding='utf8',errors='replace').read()
    me=0 if re.search(r'^  0 .*\(me,',t,re.M) else 1
    sc=re.search(r'scores: 0=(-?\d+) 1=(-?\d+)',t); s=[int(sc[1]),int(sc[2])]; win=s[me]>s[1-me]
    pts,adj,u,outs,log,T=R.load(g); tr=R.tris(pts,adj)
    own={x:None for x in tr}; can={x:[True,True] for x in tr}
    for x in tr:
        for i in (0,1):
            if all(R.owner_of(h,adj,u)==i for h in x): own[x]=i; can[x]=[i!=0,i!=1]
    TT={int(a):tuple(map(int,b)) for a,*b in re.findall(r'\bT(\d+) score=\S+ units=\S+ tri=(\d+)/(\d+)/(\d+)',t)}
    cum={'cap':[0,0],'sp':[0,0]}; G=dict(g=g,win=win,F={},act={})
    for turn in range(1,MAXT+1):
        if turn>min(len(outs[0]),len(outs[1])): break
        cmds=[[c.strip() for c in outs[p][turn-1].split(';')] for p in (0,1)]
        if turn in T:
            a=(sum(x[me] for x in u),sum(x[1-me] for x in u)); b2=(sum(own[x]==me for x in tr),sum(own[x]==1-me for x in tr))
            if a!=T[turn] or (turn in TT and b2!=TT[turn][:2]): cov['mis']+=1; break
        if any('_PATH' in c for p in (0,1) for c in cmds[p]): cov['path']+=1; break
        G['F'][turn]=(feats(adj,tr,u,own,can,cum,me),feats(adj,tr,u,own,can,cum,1-me))
        init=dict(own); rem=[x[:] for x in u]
        for p in (0,1):
            for c in cmds[p]:
                w=c.split()
                if w and w[0]=='ATTACK':
                    tt=tuple(sorted(map(int,w[1:4]))); tg=int(w[4])
                    if tt in own and own[tt]==p and all(rem[h][p]>=1 for h in tt):
                        for h in tt: u[h][p]-=1; rem[h][p]-=1
                        own[tt]=None; can[tt][p]=False; u[tg][1-p]=0; rem[tg][1-p]=0
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
        for p in (0,1):
            for c in cmds[p]:
                w=c.split()
                if w and w[0]=='SPAWN':
                    hs=list(map(int,w[1:4])); tt=tuple(sorted(hs))
                    if tt in own and own[tt]==p and init[tt]==p:
                        own[tt]=None; can[tt][p]=False; u[hs[0]][p]+=1; cum['sp'][p]+=1
        def upd():
            for x in tr:
                for i in (0,1):
                    if own[x] is not None: can[x][i]=own[x]!=i
                    else: can[x][i]=can[x][i] or all(u[h][i]==0 for h in x)
        upd(); u[:]=surround(adj,u); upd()
        for x in tr:
            b=own[x]
            for i in (0,1):
                if can[x][i] and all(R.owner_of(h,adj,u)==i for h in x): own[x]=i
            if own[x] is not None and own[x]!=b: cum['cap'][own[x]]+=1
    rows.append(G)
def auc(pairs):
    w=[v for v,y in pairs if y]; l=[v for v,y in pairs if not y]
    if not w or not l: return .5
    return sum((a>b)+.5*(a==b) for a in w for b in l)/(len(w)*len(l))
names=list(next(iter(rows[0]['F'].values()))[0])
print('games',len(rows),dict(cov),'turn coverage',[sum(t in r['F'] for r in rows) for t in range(1,MAXT+1)])
print('AUC of (me-opp) at start of turn; .5=no info')
print(f"{'feature':16s}"+''.join(f'T{t:<6d}' for t in (2,3,4,5,6,7,8,9,10,11)))
res=[]
for n in names:
    line=[]
    for t in (2,3,4,5,6,7,8,9,10,11):
        line.append(auc([(r['F'][t][0][n]-r['F'][t][1][n],r['win']) for r in rows if t in r['F']]))
    print(f'{n:16s}'+''.join(f'{x:<7.2f}' for x in line))
print('\nmean me/opp at T6,T8 in W and L')
for n in ('units','cumCap','cumSpawn','ownedTri','lockedTri','voronoiHouses','voronoiTri','voronoiFreeTri','reachFreeTri','occupied','stackExcess','hubUnits','meanTriPerUnit','contact'):
    o=[]
    for t in (4,6,8):
        for w in (True,False):
            x=[r['F'][t] for r in rows if r['win']==w and t in r['F']]
            o.append(f"{'W' if w else 'L'}{t} {st.mean(a[0][n] for a in x):.1f}/{st.mean(a[1][n] for a in x):.1f}")
    print(f'{n:16s}',' '.join(o))
json.dump([{'g':r['g'],'win':r['win'],'F':{str(k):v for k,v in r['F'].items()}} for r in rows],open(sys.argv[3],'w'))
