package com.feesaas.tenant.api;

import com.feesaas.shared.tenancy.TenantExecutor;
import com.feesaas.shared.tenancy.TenantScope;
import com.feesaas.tenant.infra.TenantRepository;
import com.feesaas.tenant.infra.TenantRepository.TenantRow;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/tenants")
public class PublicTenantBrandingController {

    private final TenantRepository tenants;
    private final TenantExecutor executor;

    public PublicTenantBrandingController(TenantRepository tenants, TenantExecutor executor) {
        this.tenants = tenants;
        this.executor = executor;
    }

    @GetMapping("/{slug}/branding")
    public Map<String, Object> branding(@PathVariable String slug) {
        TenantRow row = find(slug);
        return Map.of(
                "name", row.name(),
                "displayName", row.displayName() == null ? row.name() : row.displayName(),
                "slug", row.slug(),
                "accentColor", row.accentColor() == null ? "" : row.accentColor(),
                "hasLogo", row.hasLogo(),
                "logoUrl", "/api/v1/public/tenants/" + row.slug() + "/logo"
        );
    }

    @GetMapping("/{slug}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable String slug) {
        TenantRow row = find(slug);
        if (row.logoBase64() == null || row.logoBase64().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        String raw = row.logoBase64();
        String mime = MediaType.IMAGE_PNG_VALUE;
        String payload = raw;
        int comma = raw.indexOf(',');
        if (raw.startsWith("data:") && comma > 0) {
            String header = raw.substring(5, comma).toLowerCase();
            payload = raw.substring(comma + 1);
            if (header.contains("jpeg") || header.contains("jpg")) {
                mime = MediaType.IMAGE_JPEG_VALUE;
            } else if (header.contains("webp")) {
                mime = "image/webp";
            } else if (header.contains("gif")) {
                mime = MediaType.IMAGE_GIF_VALUE;
            }
        }
        byte[] bytes = Base64.getDecoder().decode(payload.replaceAll("\\s", ""));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mime))
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.noCache().getHeaderValue())
                .body(bytes);
    }

    private TenantRow find(String slug) {
        return executor.call(TenantScope.platformAdmin(), () ->
                tenants.findBySlug(slug).orElseThrow(() -> new com.feesaas.shared.error.ApiException(
                        com.feesaas.shared.error.ErrorCode.NOT_FOUND, "Business not found.")));
    }
}
