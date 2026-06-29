#!/usr/bin/env bash
# [废弃 - M1] M1 起元数据以 MySQL 为 source of truth,制品走对象存储;
# index.json/本地目录 seed 流程已被 DB + 发布/审批闭环取代。
# 本地 dev 改用:docker compose up -d && mvn spring-boot:run -Dspring-boot.run.profiles=dev
# 详见 docs/m1-smoke-runbook.md。
# 把样例插件 npm pack 进 ./artifacts 并生成 index.json(M0 手工塞 tarball 的自动化版)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ART="$ROOT/artifacts"
mkdir -p "$ART"

# npm 7+ 支持 --pack-destination;本机 npm 11 已具备
( cd "$ROOT/examples/hello-plugin" && npm pack --pack-destination "$ART" )

cat > "$ART/index.json" <<'JSON'
{
  "plugins": [
    {
      "package": "@demo/hello-plugin",
      "pluginName": "hello-plugin",
      "description": "Demo plugin for agent-plugin-hub M0 smoke test",
      "distTags": { "latest": "1.0.0" },
      "versions": [
        { "version": "1.0.0", "tarball": "demo-hello-plugin-1.0.0.tgz" }
      ]
    }
  ]
}
JSON

echo "seeded -> $ART"
ls -1 "$ART"
