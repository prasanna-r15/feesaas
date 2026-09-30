package com.feesaas.config.application;

import com.feesaas.config.infra.PlatformConfigRepository;
import com.feesaas.config.infra.PlatformConfigRepository.ConfigRow;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformConfigService {

    private final PlatformConfigRepository configs;

    public PlatformConfigService(PlatformConfigRepository configs) {
        this.configs = configs;
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    @Transactional(readOnly = true)
    public List<ConfigView> list(String query, String paramKey, String paramSubKey) {
        return configs.list(query, paramKey, paramSubKey).stream().map(PlatformConfigService::toView).toList();
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    @Transactional(readOnly = true)
    public ConfigView get(UUID id) {
        return toView(require(id));
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    @Transactional
    public ConfigView create(String paramKey, String paramSubKey, String paramValue, String description) {
        String key = requireKey(paramKey);
        String sub = normaliseSub(paramSubKey);
        String value = requireValue(paramValue);
        try {
            UUID id = configs.insert(key, sub, value, blankToNull(description), false);
            return toView(require(id));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.CONFLICT, "That param_key and param_sub_key pair already exists.");
        }
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    @Transactional
    public ConfigView update(UUID id, String paramKey, String paramSubKey, String paramValue, String description) {
        ConfigRow row = require(id);
        String value = requireValue(paramValue);
        try {
            if (row.locked()) {
                configs.updateValue(id, value, blankToNull(description));
            } else {
                configs.update(id, requireKey(paramKey), normaliseSub(paramSubKey), value, blankToNull(description));
            }
            return toView(require(id));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.CONFLICT, "That param_key and param_sub_key pair already exists.");
        }
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    @Transactional
    public void delete(UUID id) {
        ConfigRow row = require(id);
        if (row.locked()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "System keys cannot be deleted. Edit the value instead.");
        }
        configs.delete(id);
    }

    public String resolveOr(String paramKey, String paramSubKey, String fallback) {
        return configs.resolve(paramKey, paramSubKey).filter(v -> !v.isBlank()).orElse(fallback);
    }

    private ConfigRow require(UUID id) {
        return configs.find(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Config row not found."));
    }

    private static String requireKey(String paramKey) {
        if (paramKey == null || paramKey.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "param_key is required.");
        }
        String key = paramKey.trim().toUpperCase();
        if (!key.matches("[A-Z][A-Z0-9_]{1,98}")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "param_key must be uppercase letters, numbers, and underscores.");
        }
        return key;
    }

    private static String normaliseSub(String paramSubKey) {
        if (paramSubKey == null || paramSubKey.isBlank()) {
            return "DEFAULT";
        }
        String sub = paramSubKey.trim().toUpperCase();
        if (sub.length() > 50) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "param_sub_key is too long.");
        }
        return sub;
    }

    private static String requireValue(String paramValue) {
        if (paramValue == null || paramValue.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "param_value is required.");
        }
        if (paramValue.length() > 20000) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "param_value is too long.");
        }
        return paramValue;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static ConfigView toView(ConfigRow row) {
        return new ConfigView(
                row.id(),
                row.paramKey(),
                row.paramSubKey(),
                row.paramValue(),
                row.description(),
                row.locked(),
                row.createdAt().toString(),
                row.updatedAt().toString());
    }

    public record ConfigView(
            UUID id,
            String paramKey,
            String paramSubKey,
            String paramValue,
            String description,
            boolean locked,
            String createdAt,
            String updatedAt
    ) {}
}
