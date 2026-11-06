#!/usr/bin/env bash
# ボスとTCPサーバーをコンテナ内の /tmp でビルドして起動する。
set -euo pipefail
mkdir -p /tmp/src/ref
cp -r /sim/boss /tmp/src/boss
cp -r /sim/ref/config /tmp/src/ref/config
# Silver では ADD_PATH が使えないので、ボスがそれを選ばないようにする。
sed -i "s/if (corner.CanConnect(partner, nodes))/if (false)/" /tmp/src/ref/config/level3/Boss.cs
cd /tmp/src/boss
dotnet build -c Release -v q -o /tmp/boss-out/Boss Boss/Boss.csproj
dotnet build -c Release -v q -o /tmp/boss-out/BossServer BossServer/BossServer.csproj
exec dotnet /tmp/boss-out/BossServer/BossServer.dll
