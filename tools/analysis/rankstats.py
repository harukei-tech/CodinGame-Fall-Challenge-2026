import re,glob,sys,collections,statistics as st
D=sys.argv[1]
G=[]
for f in glob.glob(D+'/*.log'):
    t=open(f,encoding='utf8',errors='replace').read()
    me=0 if re.search(r'^  0 .*\(me,',t,re.M) else 1
    r=re.search(rf'^  {1-me} (.+?) \(agent_id \d+(?:, rank (\d+))?',t,re.M)
    sc=re.search(r'scores: 0=(-?\d+) 1=(-?\d+)',t); s=[int(sc[1]),int(sc[2])]
    g=dict(id=f,opp=r[1],rank=int(r[2] or 999),win=s[me]>s[1-me],draw=s[0]==s[1],seat=me,T={},ev=collections.defaultdict(lambda:collections.Counter()))
    turn=0
    for l in t.split('\n'):
        m=re.search(r'\bT(\d+) score=(-?\d+)/(-?\d+) units=(\d+)/(\d+) tri=(\d+)/(\d+)/(\d+)',l)
        if m:
            turn=int(m[1]); g['T'][turn]=tuple(map(int,m.groups()[1:])); continue
        for p,v,n in re.findall(r'\$(\d) (captured|surrounded|attacked|spawned) (\d+)',l):
            who='me' if int(p)==me else 'op'
            g['ev'][turn][who+'_'+v]+=int(n)
    g['end']=max(g['T']) if g['T'] else 0
    g['end_reason']=(re.findall(r'tooltips: (.*)',t) or [''])[-1][:60]
    G.append(g)
print('games',len(G),'wins',sum(g['win'] for g in G),'draw',sum(g['draw'] for g in G))
print('seat0 wins',sum(g['win'] for g in G if g['seat']==0),'/',sum(g['seat']==0 for g in G))
# rank buckets
for lo,hi in [(1,100),(101,200),(201,300),(301,375),(376,430),(431,600)]:
    x=[g for g in G if lo<=g['rank']<=hi]
    if x: print(f'opp rank {lo}-{hi}: {sum(g["win"] for g in x)}/{len(x)}  end median {st.median(g["end"] for g in x)}')
print('\nmean units me/opp, tri me/opp by turn (W=win L=loss, n alive)')
for T in (5,8,10,12,15,18,20,25,30,40,60):
    row=[]
    for w in (True,False):
        x=[g['T'][T] for g in G if g['win']==w and T in g['T']]
        if x: row.append(f"{'W' if w else 'L'} n={len(x):2d} u={st.mean(a[2] for a in x):5.1f}/{st.mean(a[3] for a in x):5.1f} tri={st.mean(a[4] for a in x):4.1f}/{st.mean(a[5] for a in x):4.1f}")
    print(f'T{T:2d}',' | '.join(row))
print('\nwin rate by unit ratio at turn (me/(me+op))')
for T in (10,15,20):
    b=collections.defaultdict(lambda:[0,0])
    for g in G:
        if T in g['T']:
            a=g['T'][T]; r=a[2]/max(1,a[2]+a[3]); k='<.3' if r<.3 else '.3-.45' if r<.45 else '.45-.55' if r<.55 else '.55-.7' if r<.7 else '>.7'
            b[k][0]+=g['win']; b[k][1]+=1
    print(T,{k:f'{v[0]}/{v[1]}' for k,v in sorted(b.items())})
print('\nloss: first turn share falls below .4 for good / share at T10')
fall=[]
for g in G:
    if g['win']: continue
    ts=sorted(g['T']); last=None
    for T in ts:
        a=g['T'][T]; 
        if a[2]/max(1,a[2]+a[3])>=.4: last=T
    fall.append((last or 0)+1)
print('collapse turn hist',sorted(collections.Counter(min(x//5*5,40) for x in fall).items()),'median',st.median(fall))
print('\nper phase mean events (me/op) W vs L')
for lo,hi in [(1,10),(11,15),(16,20),(21,30),(31,200)]:
    for w in (True,False):
        x=[g for g in G if g['win']==w]; c=collections.Counter()
        for g in x:
            for T,e in g['ev'].items():
                if lo<=T<=hi: c.update(e)
        n=len(x); f=lambda k:c[k]/n
        print(f"T{lo}-{hi} {'W' if w else 'L'} cap {f('me_captured'):5.1f}/{f('op_captured'):5.1f} spawn {f('me_spawned'):5.1f}/{f('op_spawned'):5.1f} surr-kill {f('me_surrounded'):5.1f}/{f('op_surrounded'):5.1f} atk-kill {f('me_attacked'):5.1f}/{f('op_attacked'):5.1f}")
print('\nend reasons',collections.Counter(re.sub(r'frame \d+: |\$\d ','',g['end_reason']) for g in G).most_common(8))
print('losses to lower-rank (>375):',[(g['opp'],g['rank'],g['end']) for g in G if not g['win'] and g['rank']>375][:15])
