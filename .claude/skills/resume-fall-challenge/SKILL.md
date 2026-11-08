---
name: resume-fall-challenge
description: Fall Challenge 2026（TryAngle Catch）のボット開発を別の日に再開するときの手順。現在の順位の確認、これまでの結論、次の作業、測定方法をまとめています。「続きから」「再開」「前回の続き」などの依頼で使います。
---

# Fall Challenge 2026の再開手順

## 1. 現状を確認する

次の順に確認し、結果を日本語でユーザーへ短く伝えます。

1. [docs/research-log.md](../../../docs/research-log.md)の「現在の状態」と「次の作業」を読みます。
2. コンテストの残り時間を確認します（終了は2026-11-09 18:00 JST）。
3. 順位を確認します。トークンは画面に出しません。

```sh
curl -s -H "Authorization: Bearer $(cat ~/.config/codingame/token)" \
  https://www.codingame.com/api/v1/challenges/fall-challenge-2026/progress \
  | jq -c '{percentage,rank,league,score,submission_id}'
```

4. `git status`と`git log --oneline -5`で、ユーザーの変更がないか確認します。
5. `bot/Player.java`と`sim/bots/phase5/Player.java`を比べます。違う場合は、記録より新しい作業があるので、ユーザーに確認します。
6. リーグが変わっていたら、チャレンジの情報を読み直します。新しいルールが追加されることがあります。

## 2. ローカル環境

```sh
docker compose up -d boss   # 最初に必要。審判のsim/refがなければ先にsim/setup.shを実行する
docker compose run --rm -T referee bash sim/arena.sh 40 <ボット> <相手> 2 3
docker compose down         # 終わったら止める
```

固定シードは環境変数で渡します: `docker compose run --rm -T -e SEEDS="1 2 3" referee bash sim/arena.sh ...`。
`sim/results/last.txt`を共有するので、ローカル対戦は同時に2つ動かさないでください。

コンパイルの確認:

```sh
docker compose run --rm -T --no-deps java bash -c 'mkdir -p /tmp/o && javac -encoding UTF-8 -d /tmp/o bot/Player.java && echo OK'
```

## 3. 本番のテスト対戦

```sh
SEED_BASE=1000 OPPONENT=<agent_id> tools/seeds.sh <Player.java> <結果ファイル>
```

10シードで約3分かかります。`SEED_BASE`は1000と2000を使ってきました。結果ファイルは`logs/live-results/<名前>.txt`に置くと、`tools/analysis/capdiff.py`で集計できます。

対戦相手のIDは`/challenges/fall-challenge-2026/opponents`で確認します。

## 4. サブミット

提出はユーザーの許可を得てから行います。提出前に`progress`で順位を記録し、提出後は2分おきに確認します。100%になったら`tools/fetch-rank.sh <submission_id>`でログを保存します。

```sh
jq -n --rawfile code bot/Player.java '{language: "Java", code: $code}' \
  | curl -s -X POST -H "Authorization: Bearer $(cat ~/.config/codingame/token)" -H 'Content-Type: application/json' \
    --data-binary @- https://www.codingame.com/api/v1/challenges/fall-challenge-2026/submissions | jq -c '.submission_id'
```

## 5. 結果の読み方

これまでに誤った判断をしたので、次の基準を守ります。

- 本番の10シードでは差を測れません。同じ版でT20の値が大きく動きます。20シード以上にします。
- 自分の過去版とのローカル対戦は、順位を予測しません。60勝20敗でも順位は変わりませんでした。
- ランキングのスコアは、何も変えなくても0.7程度動きます。1点未満の差は誤差として扱います。
- 比較は、提出直前の順位とスコアに対して行います。周りのボットも更新されます。
- 検証が終わったら、記録を`docs/research-log.md`へ追記します。
