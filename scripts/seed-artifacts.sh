#!/usr/bin/env bash
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
