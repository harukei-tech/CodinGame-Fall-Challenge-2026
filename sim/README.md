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

## 注意

- **並列数は2にしてください。** 6並列ではボスの計算が50msに間に合わず、タイムアウト勝ちで結果が歪みます。
  集計の `timeouts` が0であることを必ず確認してください。
- **ローカルのボスは本番より弱いです。** `config/level3/Boss.cs` から `ADD_PATH` を外したもので、本番のボスと同一ではありません。
  同じボットがローカルで8割勝っても、本番では3割程度でした。改善の方向を比べる用途に使い、最後は `tools/play.sh` で本番のボスと確認してください。
