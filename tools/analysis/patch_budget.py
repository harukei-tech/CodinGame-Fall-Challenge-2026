#!/usr/bin/env python3
"""ターンの時間配分を、ターン開始からの締め切りで管理する修正を入れる。使い方: patch_budget.py <元> <出力>
本番では前の段階が遅れ、包囲の回避が時間切れで行われないターンが約2割あった。"""
import sys
s=open(sys.argv[1],encoding='utf8').read()
def rep(a,b):
    global s
    assert s.count(a)==1,(a,s.count(a)); s=s.replace(a,b)
rep("""    /** 包囲対策に使える時間。1ターン50msの制限に余裕を残す。 */
    static final long SURROUND_CHECK_BUDGET_NANOS = 32_000_000L;""","""    /** 包囲対策に使える時間。1ターン50msの制限に余裕を残す。 */
    static final long SURROUND_CHECK_BUDGET_NANOS = 36_000_000L;
    /**
     * 占領の探索と局所探索の締め切り（ターン開始から）。本番は処理が遅く、持ち時間だけで決めると
     * 包囲の回避の時間がなくなったので、後の段階の時間をここで確保する。
     */
    static final long CAPTURE_SEARCH_END_NANOS = 15_000_000L;
    static final long LOCAL_SEARCH_END_NANOS = 22_000_000L;""")
rep("""        long deadline = System.nanoTime() + CAPTURE_SEARCH_BUDGET_NANOS;""","""        long deadline = Math.min(System.nanoTime() + CAPTURE_SEARCH_BUDGET_NANOS, turnStartNanos + CAPTURE_SEARCH_END_NANOS);""")
rep("""        long deadline = System.nanoTime() + LOCAL_SEARCH_BUDGET_NANOS;""","""        long deadline = Math.min(System.nanoTime() + LOCAL_SEARCH_BUDGET_NANOS, turnStartNanos + LOCAL_SEARCH_END_NANOS);""")
open(sys.argv[2],'w',encoding='utf8').write(s)
