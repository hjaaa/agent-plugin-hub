package com.agentpluginhub.storage;

// 制品存储抽象;M0 用本地文件实现,后续可换 MinIO/S3 而不动上层
public interface ArtifactStore {

    // 按文件名读取 tarball 字节;不存在或非法名抛 ArtifactNotFoundException
    byte[] load(String filename);
}
