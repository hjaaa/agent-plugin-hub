package com.agentpluginhub.registry;

import com.agentpluginhub.catalog.PluginCatalog;
import com.agentpluginhub.catalog.model.PluginEntry;
import com.agentpluginhub.common.ArtifactNotFoundException;
import com.agentpluginhub.common.PackageNotFoundException;
import com.agentpluginhub.registry.model.Packument;
import com.agentpluginhub.storage.ArtifactStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class RegistryController {

    private final PackumentService packumentService;
    private final ArtifactStore store;
    private final PluginCatalog catalog;

    public RegistryController(PackumentService packumentService, ArtifactStore store, PluginCatalog catalog) {
        this.packumentService = packumentService;
        this.store = store;
        this.catalog = catalog;
    }

    // npm 只读 registry 单入口:
    //   GET /registry/<package>                      → packument(元数据)
    //   GET /registry/<package>/-/<file>.tgz         → tarball(字节)
    // {*path} 捕获 /registry/ 之后的全部(含已被 Tomcat 解码的 "/"),带前导斜杠。
    @GetMapping("/registry/{*path}")
    public ResponseEntity<?> handle(@PathVariable("path") String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        if (p.isEmpty()) {
            throw new PackageNotFoundException("");
        }
        int sep = p.indexOf("/-/");
        if (sep >= 0 && p.endsWith(".tgz")) {
            String packageName = p.substring(0, sep);
            String filename = p.substring(sep + 3);
            // 校验:包必须已登记,且 filename 必须是该包某个已登记版本的 tarball,否则 404
            PluginEntry entry = catalog.require(packageName); // 未知包 → PackageNotFoundException → 404
            boolean known = entry.versions().stream()
                    .anyMatch(v -> v.tarball().equals(filename));
            if (!known) {
                throw new ArtifactNotFoundException(filename); // 文件名非该包已登记版本 → 404
            }
            byte[] bytes = store.load(filename);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(bytes);
        }
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        Packument doc = packumentService.build(p, baseUrl); // 未知包 → 404
        return ResponseEntity.ok(doc);
    }
}
