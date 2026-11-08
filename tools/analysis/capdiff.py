import re,sys,os,collections
S=sys.argv[1]
for f in sys.argv[2:]:
    ids=sorted(set(re.findall(r'\b(9\d{8})\b',open(f'{S}/{f}.txt').read())))
    c=[0,0];n=0;u=collections.defaultdict(list)
    for g in ids:
        t=open(f'logs/{g}.log',encoding='utf8',errors='replace').read(); n+=1; turn=0
        for m in re.findall(r'\bT(\d+) score=\S+ units=(\d+)/(\d+)',t):
            if int(m[0]) in (10,15,20): u[int(m[0])].append((int(m[1]),int(m[2])))
        for l in t.split('\n'):
            m=re.search(r'\bT(\d+) score=',l)
            if m: turn=int(m[1])
            if turn>20: break
            for w,k in re.findall(r'\$(\d) captured (\d+)',l): c[int(w)]+=int(k)
    print(f,n,'cap T1-20',[round(x/n,1) for x in c],{k:(round(sum(a for a,b in v)/len(v),1),round(sum(b for a,b in v)/len(v),1)) for k,v in sorted(u.items())})
