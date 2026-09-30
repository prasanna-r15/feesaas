package com.feesaas.config.api;

import com.feesaas.config.application.PlatformConfigService;
import com.feesaas.config.application.PlatformConfigService.ConfigView;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/config")
public class PlatformConfigController {

    private final PlatformConfigService configs;

    public PlatformConfigController(PlatformConfigService configs) {
        this.configs = configs;
    }

    @GetMapping
    public List<ConfigView> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String paramKey,
            @RequestParam(required = false) String paramSubKey) {
        return configs.list(q, paramKey, paramSubKey);
    }

    @GetMapping("/{id}")
    public ConfigView get(@PathVariable UUID id) {
        return configs.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigView create(@RequestBody SaveRequest request) {
        return configs.create(request.paramKey(), request.paramSubKey(), request.paramValue(), request.description());
    }

    @PutMapping("/{id}")
    public ConfigView update(@PathVariable UUID id, @RequestBody SaveRequest request) {
        return configs.update(id, request.paramKey(), request.paramSubKey(), request.paramValue(), request.description());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        configs.delete(id);
    }

    public record SaveRequest(String paramKey, String paramSubKey, String paramValue, String description) {}
}
