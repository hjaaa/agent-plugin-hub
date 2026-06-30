-- M2:把 M1 既有「只有 latest、无 stable」的插件回填 stable(retroactive 首发规则),
-- 避免切到「marketplace 跟随 stable」后这些插件从安装入口消失。幂等:已有 stable 的不动。
-- updated_by/updated_at 保持 NULL(与既有行不回填审计一致)。
INSERT INTO dist_tag (plugin_id, tag, version)
SELECT d.plugin_id, 'stable', d.version
FROM dist_tag d
WHERE d.tag = 'latest'
  AND NOT EXISTS (
      SELECT 1 FROM dist_tag s
      WHERE s.plugin_id = d.plugin_id AND s.tag = 'stable'
  );
