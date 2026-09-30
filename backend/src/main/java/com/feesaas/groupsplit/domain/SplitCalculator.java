package com.feesaas.groupsplit.domain;

import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SplitCalculator {

    private SplitCalculator() {}

    public static Map<UUID, Long> split(long totalMinor, String method, List<ShareInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Pick at least one person to split with.");
        }
        return switch (method == null ? "EQUAL" : method.toUpperCase()) {
            case "EQUAL" -> equal(totalMinor, inputs);
            case "EXACT" -> exact(totalMinor, inputs);
            case "PERCENT" -> percent(totalMinor, inputs);
            case "SHARES" -> shares(totalMinor, inputs);
            default -> throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown split method.");
        };
    }

    private static Map<UUID, Long> equal(long total, List<ShareInput> inputs) {
        int n = inputs.size();
        long base = total / n;
        long rem = total % n;
        Map<UUID, Long> out = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            out.put(inputs.get(i).memberId(), base + (i < rem ? 1 : 0));
        }
        return out;
    }

    private static Map<UUID, Long> exact(long total, List<ShareInput> inputs) {
        long sum = 0;
        Map<UUID, Long> out = new LinkedHashMap<>();
        for (ShareInput in : inputs) {
            if (in.amountMinor() == null || in.amountMinor() < 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Every share needs an amount.");
            }
            sum += in.amountMinor();
            out.put(in.memberId(), in.amountMinor());
        }
        if (sum != total) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Exact amounts must add up to the total.");
        }
        return out;
    }

    private static Map<UUID, Long> percent(long total, List<ShareInput> inputs) {
        long bps = 0;
        for (ShareInput in : inputs) {
            if (in.percentBps() == null || in.percentBps() < 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Every share needs a percentage.");
            }
            bps += in.percentBps();
        }
        if (bps != 10_000) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Percentages must add up to 100%.");
        }
        Map<UUID, Long> out = new LinkedHashMap<>();
        long allocated = 0;
        for (int i = 0; i < inputs.size(); i++) {
            ShareInput in = inputs.get(i);
            long share = i == inputs.size() - 1 ? total - allocated : total * in.percentBps() / 10_000;
            allocated += share;
            out.put(in.memberId(), share);
        }
        return out;
    }

    private static Map<UUID, Long> shares(long total, List<ShareInput> inputs) {
        long weight = 0;
        for (ShareInput in : inputs) {
            if (in.shares() == null || in.shares() <= 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Shares must be greater than zero.");
            }
            weight += in.shares();
        }
        Map<UUID, Long> out = new LinkedHashMap<>();
        long allocated = 0;
        for (int i = 0; i < inputs.size(); i++) {
            ShareInput in = inputs.get(i);
            long share = i == inputs.size() - 1 ? total - allocated : total * in.shares() / weight;
            allocated += share;
            out.put(in.memberId(), share);
        }
        return out;
    }

    public record ShareInput(UUID memberId, Long amountMinor, Integer percentBps, Integer shares) {}
}
