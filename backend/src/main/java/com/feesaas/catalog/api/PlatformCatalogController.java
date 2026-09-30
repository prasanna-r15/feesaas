package com.feesaas.catalog.api;

import com.feesaas.catalog.application.CatalogService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/tenants/{tenantId}")
public class PlatformCatalogController {

    private final CatalogService catalog;

    public PlatformCatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/branches")
    public List<Map<String, Object>> branches(@PathVariable UUID tenantId) {
        return catalog.platformBranches(tenantId).stream().map(CatalogController::branch).toList();
    }

    @PostMapping("/branches")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Map<String, Object>> createBranch(@PathVariable UUID tenantId, @RequestBody Map<String, Object> body) {
        return catalog.platformCreateBranch(
                        tenantId, str(body, "name"), str(body, "address"), str(body, "phone"), bool(body, "primary"))
                .stream().map(CatalogController::branch).toList();
    }

    @PatchMapping("/branches/{id}")
    public List<Map<String, Object>> patchBranch(
            @PathVariable UUID tenantId, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return catalog.platformPatchBranch(
                        tenantId, id, str(body, "name"), str(body, "address"), str(body, "phone"), boolObj(body, "primary"))
                .stream().map(CatalogController::branch).toList();
    }

    @DeleteMapping("/branches/{id}")
    public List<Map<String, Object>> deleteBranch(@PathVariable UUID tenantId, @PathVariable UUID id) {
        return catalog.platformDeleteBranch(tenantId, id).stream().map(CatalogController::branch).toList();
    }

    @GetMapping("/addons")
    public List<Map<String, Object>> addons(@PathVariable UUID tenantId) {
        return catalog.platformAddons(tenantId).stream().map(CatalogController::addon).toList();
    }

    @PostMapping("/addons")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Map<String, Object>> createAddon(@PathVariable UUID tenantId, @RequestBody Map<String, Object> body) {
        return catalog.platformCreateAddon(
                        tenantId, str(body, "name"), str(body, "description"), longVal(body, "amountMinor", 0), str(body, "currency"))
                .stream().map(CatalogController::addon).toList();
    }

    @PatchMapping("/addons/{id}")
    public List<Map<String, Object>> patchAddon(
            @PathVariable UUID tenantId, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return catalog.platformPatchAddon(
                        tenantId, id, str(body, "name"), str(body, "description"), longObj(body, "amountMinor"), boolObj(body, "active"))
                .stream().map(CatalogController::addon).toList();
    }

    @DeleteMapping("/addons/{id}")
    public List<Map<String, Object>> deleteAddon(@PathVariable UUID tenantId, @PathVariable UUID id) {
        return catalog.platformDeleteAddon(tenantId, id).stream().map(CatalogController::addon).toList();
    }

    @GetMapping("/diet-charts")
    public List<Map<String, Object>> diets(@PathVariable UUID tenantId) {
        return catalog.platformDiets(tenantId).stream().map(CatalogController::diet).toList();
    }

    @PostMapping("/diet-charts")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Map<String, Object>> createDiet(@PathVariable UUID tenantId, @RequestBody Map<String, Object> body) {
        return catalog.platformCreateDiet(tenantId, str(body, "name"), str(body, "body"))
                .stream().map(CatalogController::diet).toList();
    }

    @PatchMapping("/diet-charts/{id}")
    public List<Map<String, Object>> patchDiet(
            @PathVariable UUID tenantId, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return catalog.platformPatchDiet(tenantId, id, str(body, "name"), str(body, "body"))
                .stream().map(CatalogController::diet).toList();
    }

    @DeleteMapping("/diet-charts/{id}")
    public List<Map<String, Object>> deleteDiet(@PathVariable UUID tenantId, @PathVariable UUID id) {
        return catalog.platformDeleteDiet(tenantId, id).stream().map(CatalogController::diet).toList();
    }

    private static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean bool(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static Boolean boolObj(Map<String, Object> body, String key) {
        if (!body.containsKey(key) || body.get(key) == null) {
            return null;
        }
        return bool(body, key);
    }

    private static long longVal(Map<String, Object> body, String key, long fallback) {
        Long v = longObj(body, key);
        return v == null ? fallback : v;
    }

    private static Long longObj(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }
}
