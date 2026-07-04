package com.agentpluginhub.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DomainEntityStyleTest {

    private static final Path DOMAIN_DIR = Path.of("src/main/java/com/agentpluginhub/domain");
    private static final Pattern FIELD_PATTERN = Pattern.compile("\\s*private\\s+[^=;]+\\s+\\w+\\s*;");
    private static final Pattern MEMBER_PATTERN = Pattern.compile("\\s*(public|protected|private)\\s+[^;=]+\\([^;]*\\)\\s*\\{?");
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]");

    @Test
    void table_entities_should_only_keep_commented_fields_and_lombok_annotations() throws IOException {
        try (var files = Files.list(DOMAIN_DIR)) {
            List<Path> entityFiles = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::isTableEntity)
                    .toList();

            assertThat(entityFiles).isNotEmpty();
            for (Path file : entityFiles) {
                assertOnlyFieldsHaveExplicitMembers(file);
                assertEveryFieldHasChineseComment(file);
            }
        }
    }

    private boolean isTableEntity(Path file) {
        try {
            return Files.readString(file).contains("@TableName");
        } catch (IOException e) {
            throw new IllegalStateException("读取实体类失败:" + file, e);
        }
    }

    private void assertOnlyFieldsHaveExplicitMembers(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);

        List<String> explicitMembers = lines.stream()
                .map(String::trim)
                .filter(line -> MEMBER_PATTERN.matcher(line).matches())
                .filter(line -> !line.startsWith("public class "))
                .filter(line -> !line.startsWith("private ") || !line.endsWith(";"))
                .filter(line -> line.contains("("))
                .toList();

        assertThat(explicitMembers)
                .as("%s 不应手写构造器或方法", file)
                .isEmpty();
    }

    private void assertEveryFieldHasChineseComment(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            if (!FIELD_PATTERN.matcher(lines.get(i)).matches()) {
                continue;
            }

            String comment = previousMeaningfulLine(lines, i);
            assertThat(comment)
                    .as("%s 第 %s 行字段缺少中文注释", file, i + 1)
                    .matches(line -> line.startsWith("//") || line.startsWith("/**") || line.startsWith("*"));
            assertThat(comment)
                    .as("%s 第 %s 行字段注释必须包含中文", file, i + 1)
                    .matches(line -> CHINESE_PATTERN.matcher(line).find());
        }
    }

    private String previousMeaningfulLine(List<String> lines, int fieldLineIndex) {
        for (int i = fieldLineIndex - 1; i >= 0; i--) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("@")) {
                continue;
            }
            return line;
        }
        return "";
    }
}
