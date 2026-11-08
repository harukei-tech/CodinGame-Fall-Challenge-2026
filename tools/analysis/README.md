# 対戦ログの分析スクリプト

本番の対戦ログから盤面を再現して集計します。実行はリポジトリのルートから行います。
ランキング戦のログは `tools/fetch-rank.sh <submission_id>` で `logs/rank-<ID>/` に保存します。
`logs/` はgitの管理外なので、別のマシンでは取り直してください。

| スクリプト | 用途 | 使い方 |
|---|---|---|
| `rankstats.py` | 勝敗、相手の順位別、ターン別のユニット、崩れた時期 | `python3 tools/analysis/rankstats.py logs/rank-<ID>/logs` |
| `early.py` | T1〜T12の指標と勝敗の関係（AUC） | `python3 tools/analysis/early.py $PWD/tools/analysis $PWD/logs/rank-<ID> /tmp/early.json` |
| `solo.py` | 敵を無視した序盤の到達値（`early.py`の出力が必要） | `python3 tools/analysis/solo.py $PWD/tools/analysis $PWD/logs/rank-<ID> 6 /tmp/early.json 60` |
| `contest.py` | 敵の移動で潰された占領、多数を取れないユニット、敵の到着率 | `python3 tools/analysis/contest.py $PWD/tools/analysis $PWD/logs/rank-<ID>` |
| `issues.py` | 孤立死、閉域、塊の大きさ、ロックの長さ、ATTACK | `python3 tools/analysis/issues.py $PWD/tools/analysis $PWD/logs/rank-<ID>` |
| `locks.py` | 長く解除できないロックの原因の分類 | `python3 tools/analysis/locks.py $PWD/tools/analysis $PWD/logs/rank-<ID>` |
| `capdiff.py` | テスト対戦の占領数とT10/15/20のユニット | `python3 tools/analysis/capdiff.py logs/live-results cap cap2` |

`capdiff.py`は`logs/live-results/<名前>.txt`の試合IDを読み、`logs/<試合ID>.log`を集計します。

## 注意

- `replay.py`の`replay()`は古く、隣接しない`MOVE`と、開始時に成立している三角形を扱えません。
  `load`、`tris`、`owner_of`だけを使ってください。正しいターン処理は`early.py`や`issues.py`のループにあります。
- `MOVE`の最短経路の同点の選び方は、審判の隣接リストの順に依存するので再現しきれません。
  スクリプトはユニット数と三角形数の不一致を検出すると、その試合の集計を止めます。T15まではほぼ全部使えますが、T16以降は試合が減ります。
- テスト対戦のログも同じ形式です。集計するには、`logs/<任意の名前>/logs/`に試合ログのリンクを作ります。
