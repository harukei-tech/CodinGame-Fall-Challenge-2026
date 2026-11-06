# ローカル対戦環境

公式の審判（eulerscheZahl/TryAngle-Catch）で、ボットと公開ボスを対戦させます。
ホストに必要なのは Docker と git だけです。

- `referee` コンテナ: Maven と Java 11。審判を動かし、ボットをビルドします。
- `boss` コンテナ: .NET SDK。ボスをビルドして、TCPのポート9000で待ち受けます。

## 使い方

```sh
sim/setup.sh                # 初回のみ。審判のソースを sim/ref に取得する
docker compose up -d boss   # ボスを起動する（ビルドに数十秒かかる）

# 40試合を2並列で行う
docker compose run --rm -T referee bash sim/arena.sh 40 bot/Player.java boss 2

# 過去のボットとの対戦
docker compose run --rm -T referee bash sim/arena.sh 40 bot/Player.java sim/bots/v2/Player.java 2

docker compose down         # 終わったら止める
```

引数は順に、試合数、ボット、相手（`boss` か `Player.java` のパス）、並列数、リーグ番号（既定は2 = Silver）です。
試合ごとの結果は `sim/results/last.txt` に残ります。奇数シードは席を入れ替えます。

## 版の比較

```sh
# ボス、過去版の順に対戦し、ロック待機と危険な塊の集計も出す
sim/try.sh bot/Player.java 40 sim/bots/v4/Player.java

# 本番のボスと同じマップで比べる
BOT=sim/bots/v4/Player.java OPTIONS=seed=8919 tools/play.sh 1 boss

# 本番のボスと固定シード10個で対戦し、T20のユニット数と包囲・ATTACKの損得を集計する
tools/seeds.sh bot/Player.java
```

`lockstats.py` の列は、`waiting` がロック中の角で待つユニット、`hubWaiting` がそのうち2つ以上の三角形を止める家にいるユニット、`exposed` が次のターンにATTACKされうる塊のユニットです。

## 注意

- **並列数は2にしてください。** 6並列ではボスの計算が50msに間に合わず、タイムアウト勝ちで結果が歪みます。
  集計の `timeouts` が0であることを必ず確認してください。
- **包囲の調整はローカルで判断できません。** 本番のボスは公開版と手が異なり、包囲で失う数も1試合1体と13体で大きく違います。ローカルで19勝1敗の版が、本番では0勝10敗でした。
- **ローカルのボスは本番より弱いです。** `config/level3/Boss.cs` から `ADD_PATH` を外したもので、本番のボスと同一ではありません。
  同じボットがローカルで8割勝っても、本番では3割程度でした。改善の方向を比べる用途に使い、最後は `tools/play.sh` で本番のボスと確認してください。
- **本番の比較はシードをそろえてください。** 勝敗は版の差よりマップの差に大きく左右されます。シードを指定しない比較では、同じボットが7勝0敗の直後に1勝9敗になりました。
