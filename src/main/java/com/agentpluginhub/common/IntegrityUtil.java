package com.agentpluginhub.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

// npm 完整性哈希工具:packument 的 dist.integrity / dist.shasum 由此生成
public final class IntegrityUtil {

    private IntegrityUtil() {
    }

    // npm SRI 格式:sha512-<base64(原始 sha512 摘要)>
    public static String sriSha512(byte[] data) {
        return "sha512-" + Base64.getEncoder().encodeToString(digest("SHA-512", data));
    }

    // 老 npm 仍读 shasum:40 位十六进制 sha1
    public static String hexSha1(byte[] data) {
        byte[] d = digest("SHA-1", data);
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] digest(String algorithm, byte[] data) {
        try {
            return MessageDigest.getInstance(algorithm).digest(data);
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 / SHA-512 是 JDK 标配,理论上不可能走到这里
            throw new IllegalStateException("missing digest algorithm: " + algorithm, e);
        }
    }
}
