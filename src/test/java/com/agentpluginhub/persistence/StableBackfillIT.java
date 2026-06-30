package com.agentpluginhub.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentpluginhub.domain.DistTag;
import com.agentpluginhub.domain.DistTagRepository;
import com.agentpluginhub.domain.Plugin;
import com.agentpluginhub.domain.PluginRepository;
import com.agentpluginhub.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

/**
 * 验证 V3 回填逻辑:只有 latest、无 stable 的插件执行回填后 stable 出现,
 * 且 SQL 可幂等执行(重复运行不产生重复行)。
 *
 * Flyway 在空库已跑过 V3 为 no-op(空库无行可回填),
 * 故测试直接用 JdbcTemplate 内联同一条回填 SQL 验证语义。
 */
class StableBackfillIT extends AbstractIntegrationTest {

    // 与 V3 迁移脚本保持完全一致
    private static final String BACKFILL_SQL =
            "INSERT INTO dist_tag (plugin_id, tag, version) " +
            "SELECT d.plugin_id, 'stable', d.version " +
            "FROM dist_tag d " +
            "WHERE d.tag = 'latest' " +
            "  AND NOT EXISTS (" +
            "      SELECT 1 FROM dist_tag s " +
            "      WHERE s.plugin_id = d.plugin_id AND s.tag = 'stable'" +
            "  )";

    @Autowired
    private PluginRepository pluginRepo;

    @Autowired
    private DistTagRepository distTags;

    @Autowired
    private DataSource dataSource;

    @Test
    void backfill_creates_stable_from_latest_and_is_idempotent() {
        // 准备:建一个只有 latest 的 M1 遗留插件(唯一包名避免与其它测试数据冲突)
        Plugin plugin = pluginRepo.save(
                new Plugin("@demo/m1legacy", "M1 Legacy Plugin", "backfill test fixture", null));
        Long pluginId = plugin.getId();

        distTags.save(new DistTag(pluginId, "latest", "1.0.0"));
        // 确认此时没有 stable
        assertThat(distTags.findByPluginIdAndTag(pluginId, "stable")).isEmpty();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // 第一次回填:应产生 stable 行
        jdbc.execute(BACKFILL_SQL);

        assertThat(distTags.findByPluginIdAndTag(pluginId, "stable"))
                .isPresent()
                .hasValueSatisfying(t -> assertThat(t.getVersion()).isEqualTo("1.0.0"));

        // 第二次回填:幂等,stable 行数仍为 1
        jdbc.execute(BACKFILL_SQL);

        long stableCount = distTags.findByPluginId(pluginId).stream()
                .filter(t -> t.getTag().equals("stable"))
                .count();
        assertThat(stableCount).isEqualTo(1);
    }
}
