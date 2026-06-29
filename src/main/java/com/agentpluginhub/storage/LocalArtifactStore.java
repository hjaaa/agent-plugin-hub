package com.agentpluginhub.storage;

import com.agentpluginhub.common.ArtifactNotFoundException;
import com.agentpluginhub.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class LocalArtifactStore implements ArtifactStore {

    private final AppProperties props;

    public LocalArtifactStore(AppProperties props) {
        this.props = props;
    }

    @Override
    public byte[] load(String filename) {
        // 防路径穿越:只允许 artifactsDir 下的直接子文件
        if (filename == null || filename.isBlank()
                || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new ArtifactNotFoundException(String.valueOf(filename));
        }
        Path file = Path.of(props.getArtifactsDir(), filename);
        if (!Files.isRegularFile(file)) {
            throw new ArtifactNotFoundException(filename);
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ArtifactNotFoundException(filename);
        }
    }
}
