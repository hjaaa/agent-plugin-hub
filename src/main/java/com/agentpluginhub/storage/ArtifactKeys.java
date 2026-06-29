package com.agentpluginhub.storage;

// 由 (package, version) 推导扁平、无斜杠的对象 key,与 npm pack 文件名一致。
// @demo/hello-plugin + 1.0.0 → demo-hello-plugin-1.0.0.tgz
public final class ArtifactKeys {

    private ArtifactKeys() {
    }

    public static String canonical(String packageName, String version) {
        String flat = packageName.replace("@", "").replace("/", "-");
        return flat + "-" + version + ".tgz";
    }
}
