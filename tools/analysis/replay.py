import re,sys,collections
def ccw(a,b,c): return (c[1]-a[1])*(b[0]-a[0])>(b[1]-a[1])*(c[0]-a[0])
def inside(p,a,b,c):
    x=[ccw(p,a,b),ccw(p,b,c),ccw(p,c,a)]; return all(x) or not any(x)
def load(g):
    t=open(f'logs/{g}.log',encoding='utf8',errors='replace').read()
    e=re.search(r'edges=(\S+)',t).group(1); n=re.search(r'nodes=(\S+)',t).group(1); u=re.search(r'units=(\S+)',t).group(1)
    pts=[tuple(map(int,p.split(','))) for p in n.split('_')]
    adj=collections.defaultdict(set)
    for p in e.split('_'):
        a,b=map(int,p.split(',')); adj[a].add(b); adj[b].add(a)
    units=[[int(x) for x in p.split(',')] for p in u.split('_')]
    outs={0:[],1:[]}; summ=collections.Counter(); order=[]
    fr=re.split(r'\n(?=frame \d+)',t)
    turn=0
    log=[]
    for f in fr[1:]:
        m=re.match(r'frame \d+ agent (\d)',f)
        s=re.search(r'stdout: (.*)',f)
        if m and s: outs[int(m.group(1))].append(s.group(1))
        for who,n2 in re.findall(r'\$(\d) spawned (\d+) unit',f):
            log.append((len(outs[0]),int(who),int(n2)))
    T={int(a):(int(b),int(c)) for a,b,c in re.findall(r'\bT(\d+) score=\S+ units=(\d+)/(\d+)',t)}
    return pts,adj,units,outs,log,T
def tris(pts,adj):
    r=[]
    for a in adj:
        for b in adj[a]:
            for c in adj[b]:
                if a<b<c and a in adj[c] and not any(i not in(a,b,c) and inside(pts[i],pts[a],pts[b],pts[c]) for i in range(len(pts))): r.append((a,b,c))
    return r
def owner_of(h,adj,u):
    ns=adj[h]
    if ns and all(u[n][0]>u[n][1] for n in ns): return 0
    if ns and all(u[n][1]>u[n][0] for n in ns): return 1
    if u[h][0]>u[h][1]: return 0
    if u[h][1]>u[h][0]: return 1
    return None
def replay(g,turns=40):
    pts,adj,u,outs,log,T=load(g)
    tr=tris(pts,adj); own={t:None for t in tr}; can={t:[True,True] for t in tr}
    rows=[]; diff=[]
    for turn in range(1,turns+1):
        if turn>min(len(outs[0]),len(outs[1])): break
        if turn in T:
            mine=sum(x[0] for x in u); op=sum(x[1] for x in u)
            diff.append((turn,mine-T[turn][0],op-T[turn][1]))
        cmds=[[c.strip() for c in outs[p][turn-1].split(';')] for p in (0,1)]
        init={t:own[t] for t in tr}
        rem=[[x[0],x[1]] for x in u]
        st={p:dict(spawn=0,cap=0,capEmpty=0,moved=0,idle=0,capUnits=0) for p in (0,1)}
        # attack
        for p in (0,1):
            for c in cmds[p]:
                w=c.split()
                if w and w[0]=='ATTACK':
                    tt=tuple(sorted(map(int,w[1:4]))); tgt=int(w[4])
                    if tt in own and own[tt]==p and init[tt]==p and all(rem[h][p]>=1 for h in tt):
                        for h in tt: u[h][p]-=1; rem[h][p]-=1
                        own[tt]=None; can[tt][p]=False
                        u[tgt][1-p]=0; rem[tgt][1-p]=0
        # move
        for p in (0,1):
            mv=[]
            for c in cmds[p]:
                w=c.split()
                if w and w[0]=='MOVE':
                    a,b,n=map(int,w[1:4]); n=min(n,rem[a][p])
                    if n>0 and b in adj[a]: rem[a][p]-=n; mv.append((a,b,n)); st[p]['moved']+=n
            for a,b,n in mv: u[a][p]-=n; u[b][p]+=n
        # spawn
        for p in (0,1):
            for c in cmds[p]:
                w=c.split()
                if w and w[0]=='SPAWN':
                    hs=list(map(int,w[1:4])); tt=tuple(sorted(hs))
                    if tt in own and own[tt]==p and init[tt]==p:
                        own[tt]=None; can[tt][p]=False; u[hs[0]][p]+=1; st[p]['spawn']+=1
        for t in tr:
            for i in (0,1):
                if own[t] is not None: can[t][i]=own[t]!=i
                else: can[t][i]=can[t][i] or all(u[h][i]==0 for h in t)
        # surround once
        adv=[{h for h in adj if u[h][i]>u[h][1-i]} for i in (0,1)]
        for i in (0,1):
            for h in adj:
                if u[h][i]>0 and adj[h] and all(n in adv[1-i] for n in adj[h]): u[h][i]=0
        for t in tr:
            for i in (0,1):
                if own[t] is not None: can[t][i]=own[t]!=i
                else: can[t][i]=can[t][i] or all(u[h][i]==0 for h in t)
        for t in tr:
            before=own[t]
            for i in (0,1):
                if can[t][i] and all(owner_of(h,adj,u)==i for h in t): own[t]=i
            if own[t] is not None and own[t]!=before:
                p=own[t]; st[p]['cap']+=1
                if any(u[h][p]==0 for h in t): st[p]['capEmpty']+=1
        for p in (0,1):
            st[p]['units']=sum(x[p] for x in u); st[p]['owned']=sum(1 for t in tr if own[t]==p)
        rows.append(st)
    return rows,diff,log,len(tr),outs
if __name__=='__main__':
    for g in sys.argv[1:]:
        rows,diff,log,nt,outs=replay(g)
        bad=[d for d in diff if d[1] or d[2]]
        print('game',g,'triangles',nt,'checked',len(diff),'mismatch turns',len(bad),'first',bad[:2])
        real=[sum(n for t,w,n in log if w==p and t<=20) for p in (0,1)]
        print(' real spawns by summary T1-20 me/opp',real,'cmd count',[sum(o.count('SPAWN') for o in outs[p][:20]) for p in (0,1)])
        for p,name in ((0,'me'),(1,'opp')):
            print(' ',name,'turn: units/owned/cap/capEmpty/spawn')
            print('   ',' '.join(f"{i+1}:{r[p]['units']}/{r[p]['owned']}/{r[p]['cap']}/{r[p]['capEmpty']}/{r[p]['spawn']}" for i,r in enumerate(rows[:20])))
