package com.agentpluginhub.storage;

// 制品存储抽象;M1 默认 S3/MinIO 实现,可经 app.storage.type=local 切回本地文件
public interface ArtifactStore {

    // 按 key 读取 tarball 字节;不存在或非法 key 抛 ArtifactNotFoundException
    byte[] load(String key);

    // 保存 tarball 字节到 key(覆盖);key 必须无路径分隔符
    void save(String key, byte[] data);

    // key 是否已存在
    boolean exists(String key);
}
