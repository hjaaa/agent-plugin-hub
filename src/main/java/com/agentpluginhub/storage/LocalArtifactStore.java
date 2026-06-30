package com.agentpluginhub.storage;

import com.agentpluginhub.common.ArtifactNotFoundException;
import com.agentpluginhub.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local")
public class LocalArtifactStore implements ArtifactStore {

    private final AppProperties props;

    public LocalArtifactStore(AppProperties props) {
        this.props = props;
    }

    @Override
    public byte[] load(String key) {
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) {
            throw new ArtifactNotFoundException(key);
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ArtifactNotFoundException(key);
        }
    }

    @Override
    public void save(String key, byte[] data) {
        Path file = resolve(key);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, data);
        } catch (IOException e) {
            throw new IllegalStateException("failed to save artifact: " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        if (isIllegal(key)) {
            return false;
        }
        return Files.isRegularFile(Path.of(props.getArtifactsDir(), key));
    }

    private Path resolve(String key) {
        if (isIllegal(key)) {
            throw new ArtifactNotFoundException(String.valueOf(key));
        }
        return Path.of(props.getArtifactsDir(), key);
    }

    // 防路径穿越:只允许 artifactsDir 下的直接子文件
    private boolean isIllegal(String key) {
        return key == null || key.isBlank()
                || key.contains("/") || key.contains("\\") || key.contains("..");
    }
}
