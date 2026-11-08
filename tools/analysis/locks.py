# 長く解除できないロックで、角に残るユニットが何をしているかを分類する
import sys,re,glob,os,collections
sys.path.insert(0,sys.argv[1]); import replay as R
os.chdir(sys.argv[2])
def bfs(adj,s):
    d={x:0 for x in s}; q=list(d)
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
C=collections.Counter(); O=collections.Counter()
for f in sorted(glob.glob('logs/*.log')):
    g=os.path.basename(f)[:-4]; t=open(f,encoding='utf8',errors='replace').read()
    me=0 if re.search(r'^  0 .*\(me,',t,re.M) else 1
    pts,adj,u,outs,log,T=R.load(g); tr=R.tris(pts,adj)
    own={x:None for x in tr}; can={x:[True,True] for x in tr}
    for x in tr:
        for i in (0,1):
            if all(R.owner_of(h,adj,u)==i for h in x): own[x]=i; can[x]=[i!=0,i!=1]
    TT={int(a):tuple(map(int,b)) for a,*b in re.findall(r'\bT(\d+) score=\S+ units=\S+ tri=(\d+)/(\d+)/(\d+)',t)}
    age=[collections.Counter(),collections.Counter()]
    for turn in range(1,31):
        if turn>min(len(outs[0]),len(outs[1])): break
        cmds=[[c.strip() for c in outs[p][turn-1].split(';')] for p in (0,1)]
        if turn in T:
            a=(sum(x[me] for x in u),sum(x[1-me] for x in u)); b2=(sum(own[x]==me for x in tr),sum(own[x]==1-me for x in tr))
            if a!=T[turn] or (turn in TT and b2!=TT[turn][:2]): break
        if any('_PATH' in c for p in (0,1) for c in cmds[p]): break
        init=dict(own); rem=[x[:] for x in u]; start=None
        for p in (0,1):
            for c in cmds[p]:
                w=c.split()
                if w and w[0]=='ATTACK':
                    tt=tuple(sorted(map(int,w[1:4]))); tg=int(w[4])
                    if tt in own and own[tt]==p and all(rem[h][p]>=1 for h in tt):
                        for h in tt: u[h][p]-=1; rem[h][p]-=1
                        own[tt]=None; can[tt][p]=False; u[tg][1-p]=0; rem[tg][1-p]=0
        start=[x[:] for x in u]; arrived=collections.Counter()
        for p in (0,1):
            mv=[]
            for c in cmds[p]:
                w=c.split()
                if w and w[0]=='MOVE':
                    a,b,n=map(int,w[1:4]); n=min(n,rem[a][p])
                    if n>0 and a!=b:
                        d=bfs(adj,[b]); nx=next((x for x in sorted(adj[a]) if d.get(x,99)<d.get(a,99)),None)
                        if nx is not None: rem[a][p]-=n; mv.append((a,nx,n))
            for a,b,n in mv:
                u[a][p]-=n; u[b][p]+=n; arrived[(b,p)]+=n
        spawned=collections.Counter()
        for p in (0,1):
            for c in cmds[p]:
                w=c.split()
                if w and w[0]=='SPAWN':
                    hs=list(map(int,w[1:4])); tt=tuple(sorted(hs))
                    if tt in own and own[tt]==p and init[tt]==p:
                        own[tt]=None; can[tt][p]=False; u[hs[0]][p]+=1; spawned[(hs[0],p)]+=1
        def upd():
            for x in tr:
                for i in (0,1):
                    if own[x] is not None: can[x][i]=own[x]!=i
                    else: can[x][i]=can[x][i] or all(u[h][i]==0 for h in x)
        upd()
        if turn>=6:
            o={h:R.owner_of(h,adj,u) for h in adj}
            for p,cnt in ((me,C),(1-me,O)):
                for x in tr:
                    if own[x]==p or can[x][p]: age[p][x]=0; continue
                    age[p][x]+=1
                    occ=[h for h in x if u[h][p]>0]
                    cnt['locks']+=1
                    if age[p][x]<3: continue
                    cnt['old']+=1; cnt['oldCorners%d'%len(occ)]+=1
                    if len(occ)!=1: continue
                    h=occ[0]
                    if rem[h][p]>0: why='stayed'
                    elif spawned[(h,p)]: why='spawnedIn'
                    else: why='movedIn'
                    cnt['one_'+why]+=1
                    # そのユニットが別の三角形の角を保っているか
                    held=sum(1 for y in tr if h in y and y!=x and own[y]!=p and can[y][p] and all(o[k]==p for k in y))
                    cnt['one_'+why+('_capturing' if held else '_idle')]+=1
                    cnt['one_deg%d'%min(sum(h in y for y in tr),4)]+=1
        u[:]=surround(adj,u); upd()
        for x in tr:
            for i in (0,1):
                if can[x][i] and all(R.owner_of(h,adj,u)==i for h in x): own[x]=i
for n,c in (('me',C),('op',O)):
    print(n,dict(sorted(c.items())))
