package com.feesaas.catalog.api;

import com.feesaas.catalog.application.CatalogService;
import com.feesaas.catalog.infra.CatalogRepository.AddonRow;
import com.feesaas.catalog.infra.CatalogRepository.BranchRow;
import com.feesaas.catalog.infra.CatalogRepository.DietRow;
import com.feesaas.catalog.infra.CatalogRepository.SaleRow;
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
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/branches")
    public List<Map<String, Object>> branches() {
        return catalog.listBranches().stream().map(CatalogController::branch).toList();
    }

    @PostMapping("/branches")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Map<String, Object>> createBranch(@RequestBody Map<String, Object> body) {
        return catalog.createBranch(str(body, "name"), str(body, "address"), str(body, "phone"), bool(body, "primary"))
                .stream().map(CatalogController::branch).toList();
    }

    @PatchMapping("/branches/{id}")
    public List<Map<String, Object>> patchBranch(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return catalog.patchBranch(id, str(body, "name"), str(body, "address"), str(body, "phone"), boolObj(body, "primary"))
                .stream().map(CatalogController::branch).toList();
    }

    @DeleteMapping("/branches/{id}")
    public List<Map<String, Object>> deleteBranch(@PathVariable UUID id) {
        return catalog.deleteBranch(id).stream().map(CatalogController::branch).toList();
    }

    @GetMapping("/addons")
    public List<Map<String, Object>> addons() {
        return catalog.listAddons().stream().map(CatalogController::addon).toList();
    }

    @PostMapping("/addons")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Map<String, Object>> createAddon(@RequestBody Map<String, Object> body) {
        return catalog.createAddon(str(body, "name"), str(body, "description"), longVal(body, "amountMinor", 0), str(body, "currency"))
                .stream().map(CatalogController::addon).toList();
    }

    @PatchMapping("/addons/{id}")
    public List<Map<String, Object>> patchAddon(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return catalog.patchAddon(id, str(body, "name"), str(body, "description"), longObj(body, "amountMinor"), boolObj(body, "active"))
                .stream().map(CatalogController::addon).toList();
    }

    @DeleteMapping("/addons/{id}")
    public List<Map<String, Object>> deleteAddon(@PathVariable UUID id) {
        return catalog.deleteAddon(id).stream().map(CatalogController::addon).toList();
    }

    @GetMapping("/addon-sales")
    public List<Map<String, Object>> sales() {
        return catalog.listSales().stream().map(CatalogController::sale).toList();
    }

    @PostMapping("/addons/{id}/collect")
    public List<Map<String, Object>> collect(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UUID customerId = UUID.fromString(str(body, "customerId"));
        return catalog.collectAddon(id, customerId, longObj(body, "amountMinor"), str(body, "method"), str(body, "notes"))
                .stream().map(CatalogController::sale).toList();
    }

    @GetMapping("/diet-charts")
    public List<Map<String, Object>> diets() {
        return catalog.listDiets().stream().map(CatalogController::diet).toList();
    }

    @PostMapping("/diet-charts")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Map<String, Object>> createDiet(@RequestBody Map<String, Object> body) {
        return catalog.createDiet(str(body, "name"), str(body, "body")).stream().map(CatalogController::diet).toList();
    }

    @PatchMapping("/diet-charts/{id}")
    public List<Map<String, Object>> patchDiet(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return catalog.patchDiet(id, str(body, "name"), str(body, "body")).stream().map(CatalogController::diet).toList();
    }

    @DeleteMapping("/diet-charts/{id}")
    public List<Map<String, Object>> deleteDiet(@PathVariable UUID id) {
        return catalog.deleteDiet(id).stream().map(CatalogController::diet).toList();
    }

    @PostMapping("/diet-charts/{id}/send")
    public Map<String, Object> sendDiet(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        List<UUID> ids = new java.util.ArrayList<>();
        Object single = body.get("customerId");
        if (single != null && !String.valueOf(single).isBlank()) {
            ids.add(UUID.fromString(String.valueOf(single)));
        }
        Object many = body.get("customerIds");
        if (many instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    ids.add(UUID.fromString(String.valueOf(item)));
                }
            }
        }
        UUID branchId = null;
        Object branch = body.get("branchId");
        if (branch != null && !String.valueOf(branch).isBlank()) {
            branchId = UUID.fromString(String.valueOf(branch));
        }
        List<Map<String, Object>> sends = catalog.sendDiet(id, ids, branchId).stream()
                .map(view -> Map.<String, Object>of(
                        "channel", view.channel(),
                        "body", view.body(),
                        "waLink", view.waLink(),
                        "smsLink", view.smsLink(),
                        "tenantWhatsapp", view.tenantWhatsapp() == null ? "" : view.tenantWhatsapp(),
                        "tenantPhone", view.tenantPhone() == null ? "" : view.tenantPhone(),
                        "memberName", view.memberName() == null ? "" : view.memberName()))
                .toList();
        return Map.of("sends", sends);
    }

    static Map<String, Object> branch(BranchRow row) {
        return Map.of(
                "id", row.id(),
                "name", row.name(),
                "address", row.address() == null ? "" : row.address(),
                "phone", row.phone() == null ? "" : row.phone(),
                "primary", row.primary());
    }

    static Map<String, Object> addon(AddonRow row) {
        return Map.of(
                "id", row.id(),
                "name", row.name(),
                "description", row.description() == null ? "" : row.description(),
                "amountMinor", row.amountMinor(),
                "currency", row.currency(),
                "active", row.active());
    }

    static Map<String, Object> sale(SaleRow row) {
        return Map.of(
                "id", row.id(),
                "productId", row.productId(),
                "productName", row.productName(),
                "customerId", row.customerId(),
                "customerName", row.customerName(),
                "amountMinor", row.amountMinor(),
                "currency", row.currency(),
                "method", row.method(),
                "notes", row.notes() == null ? "" : row.notes(),
                "soldOn", row.soldOn().toString());
    }

    static Map<String, Object> diet(DietRow row) {
        return Map.of(
                "id", row.id(),
                "name", row.name(),
                "body", row.body());
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
