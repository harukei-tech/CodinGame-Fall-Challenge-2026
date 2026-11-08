#!/usr/bin/env python3
"""デバッグ版（sim/bots/f134dbg）のGログから、3体以上の家を出たユニットの行き先と原因の段階を集計する。
使い方: python3 tools/analysis/stackmoves.py <stderrファイル...>
段階: J=仕事の割り当て, F=余りユニット, I=局所探索と占領の探索, A=包囲の回避, D=塊の分散
行き先: C=占領に使う角, lock=占領に使わずロック中の三角形の角, none=占領に使わずロックもない"""
import re,sys,collections
def parse(part):
    r={}
    for tok in part.split():
        h,rest=tok.split(':',1); d={}
        for x in rest.split(','):
            if not x: continue
            if x.startswith('s'): d['stay']=int(x[1:])
            else: a,b=x.split('x'); d[int(a)]=int(b)
        r[int(h)]=d
    return r
stage=collections.Counter(); dest=collections.Counter(); both=collections.Counter(); group=collections.Counter(); turns=0; srch=collections.Counter()
import os
TMAX=int(os.environ.get("TMAX","999"))
for f in sys.argv[1:]:
    for l in open(f,encoding='utf8',errors='replace'):
        if not l.startswith("G") or int(re.match(r"G(\d+)",l)[1])>TMAX: continue
        head,info=l.split('||',1)
        parts=dict(p.strip().split('=',1) for p in head.split('|')[0:6] if '=' in p)
        m=re.match(r'G(\d+) J=(.*?) \| F=(.*?) \| I=(.*?) \| A=(.*?) \| D=(.*?) \| S=(\d)',head)
        if not m: continue
        st={k:parse(v) for k,v in zip('JFIAD',m.groups()[1:6])}; adopted=m[7]=='1'
        prop={}
        for blk in info.split(' | '):
            mm=re.match(r'\s*(\d+)\(n(\d+) o(\d+) lk(\d+) atk(\d)\)(.*)',blk)
            if not mm: continue
            h=int(mm[1]); prop[h]={'atk':mm[5]=='1','lk':int(mm[4])}
            for d,cap,lk,o,p,c in re.findall(r'(\d+)\[cap(\d+) lk(\d+) o(\d+) p(\d+)( C)?\]',mm[6]):
                prop[(h,int(d))]='C' if c else ('lock' if int(lk)>0 else 'none')
        for h,fin in st['D'].items():
            turns+=1
            for d,n in fin.items():
                if d=='stay': continue
                kind=prop.get((h,d),'?'); dest[kind]+=n
                # どの段階でこの行き先が増えたか
                prev=0
                for s in 'JFIAD':
                    c=st[s].get(h,{}).get(d,0)
                    if c>prev: stage[s]+=c-prev; both[(s,kind)]+=c-prev
                    prev=c
                if n>=2 and kind!='C': group[kind]+=1
                if kind!='C': srch['adopted' if adopted else 'not']+=n
tot=sum(dest.values())
print('stack-turns',turns,'departing units',tot)
print('destination',{k:f'{v} ({100*v/tot:.0f}%)' for k,v in dest.items()})
print('stage that added the move',dict(stage))
print('stage x destination',{f'{a}-{b}':v for (a,b),v in sorted(both.items())})
print('moves of 2+ units to one non-capturing house',dict(group))
print('non-capturing departures on turns where capture search was adopted',dict(srch))
