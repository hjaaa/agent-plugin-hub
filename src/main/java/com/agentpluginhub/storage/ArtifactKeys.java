package com.agentpluginhub.storage;

// 由 (package, version, shasum) 推导扁平、无斜杠的对象 key。
// 嵌入 shasum 短前缀做内容标识:不同内容(含 scoped 与同名扁平包的碰撞)→ 不同 key,
// 避免「@demo/hello-plugin」与「demo-hello-plugin」同版本映射到同一对象而相互覆盖。
// @demo/hello-plugin + 1.0.0 + sha1(abcdef…) → demo-hello-plugin-1.0.0-abcdef012345.tgz
public final class ArtifactKeys {

    private ArtifactKeys() {
    }

    public static String canonical(String packageName, String version, String shasum) {
        String flat = packageName.replace("@", "").replace("/", "-");
        // shasum 固定为 40 位 sha1 hex(见 IntegrityUtil.hexSha1),取前 12 位足够消歧
        String tag = shasum.substring(0, 12);
        return flat + "-" + version + "-" + tag + ".tgz";
    }
}
