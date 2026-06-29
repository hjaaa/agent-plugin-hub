package com.agentpluginhub.marketplace;

import com.agentpluginhub.marketplace.model.Marketplace;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class MarketplaceController {

    private final MarketplaceService service;

    public MarketplaceController(MarketplaceService service) {
        this.service = service;
    }

    // CC 远程市场入口:/plugin marketplace add http://<host>/marketplace.json
    @GetMapping(value = "/marketplace.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Marketplace marketplace() {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        return service.render(baseUrl);
    }
}
