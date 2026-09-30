package com.feesaas.personal.application;

import com.feesaas.auth.infra.IdentityRepository;
import com.feesaas.personal.infra.PersonalFinanceRepository;
import com.feesaas.personal.infra.PersonalFinanceRepository.BudgetRow;
import com.feesaas.personal.infra.PersonalFinanceRepository.CategoryRow;
import com.feesaas.personal.infra.PersonalFinanceRepository.CategorySpend;
import com.feesaas.personal.infra.PersonalFinanceRepository.ExpenseRow;
import com.feesaas.personal.infra.PersonalFinanceRepository.IncomeRow;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.tenancy.TenantContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalFinanceService {

    private final PersonalFinanceRepository repo;
    private final IdentityRepository identity;

    public PersonalFinanceService(PersonalFinanceRepository repo, IdentityRepository identity) {
        this.repo = repo;
        this.identity = identity;
    }

    @PreAuthorize("hasPermission(null, 'personal.manage')")
    @Transactional(readOnly = true)
    public List<CategoryRow> categories() {
        return repo.categories(workspaceId());
    }

    @PreAuthorize("hasPermission(null, 'personal.manage')")
    @Transactional
    public CategoryRow createCategory(String name, String icon, String color) {
        try {
            UUID id = repo.insertCategory(workspaceId(), name.trim(), icon == null ? "category" : icon,
                    color == null ? "#0F766E" : color);
            return categories().stream().filter(c -> c.id().equals(id)).findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Could not save category."));
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.CONFLICT, "That category already exists.");
        }
    }

    @PreAuthorize("hasPermission(null, 'personal.manage')")
    @Transactional
    public ExpenseRow addExpense(long amountMinor, UUID categoryId, String description, LocalDate occurredOn,
            String method, UUID clientId) {
        if (amountMinor <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Amount must be greater than zero.");
        }
        UUID ws = workspaceId();
        try {
            UUID id = repo.insertExpense(ws, categoryId, amountMinor, "INR", description, occurredOn == null ? LocalDate.now() : occurredOn, method, clientId);
            maybeBudgetAlerts(ws, categoryId, occurredOn == null ? LocalDate.now() : occurredOn);
            return repo.expenses(ws, LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1)).stream()
                    .filter(e -> e.id().equals(id)).findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Could not save expense."));
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.CONFLICT, "This expense was already saved.");
        }
    }

    @PreAuthorize("hasPermission(null, 'personal.manage')")
    @Transactional(readOnly = true)
    public List<ExpenseRow> expenses(YearMonth month) {
        LocalDate from = month.atDay(1);
        return repo.expenses(workspaceId(), from, month.atEndOfMonth());
    }

    @PreAuthorize("hasPermission(null, 'personal.manage')")
    @Transactional
    public IncomeRow addIncome(long amountMinor, String source, String description, LocalDate occurredOn, UUID clientId) {
        if (amountMinor <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Amount must be greater than zero.");
        }
        UUID ws = workspaceId();
        UUID id = repo.insertIncome(ws, amountMinor, "INR", source == null ? "Other" : source, description,
                occurredOn == null ? LocalDate.now() : occurredOn, clientId);
        return repo.income(ws, LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1)).stream()
                .filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Could not save income."));
    }

    @PreAuthorize("hasPermission(null, 'personal.manage')")
    @Transactional(readOnly = true)
    public List<IncomeRow> income(YearMonth month) {
        return repo.income(workspaceId(), month.atDay(1), month.atEndOfMonth());
    }

    @PreAuthorize("hasPermission(null, 'personal.manage')")
    @Transactional
    public List<BudgetRow> saveBudget(UUID categoryId, String yearMonth, long limitMinor) {
        if (limitMinor <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Budget amount must be greater than zero.");
        }
        String ym = yearMonth == null ? YearMonth.now().toString() : yearMonth;
        repo.upsertBudget(workspaceId(), categoryId, ym, limitMinor, "INR");
        return repo.budgets(workspaceId(), ym);
    }

    @PreAuthorize("hasPermission(null, 'personal.manage')")
    @Transactional(readOnly = true)
    public List<BudgetRow> budgets(String yearMonth) {
        String ym = yearMonth == null ? YearMonth.now().toString() : yearMonth;
        return repo.budgets(workspaceId(), ym);
    }

    @PreAuthorize("hasPermission(null, 'personal.manage')")
    @Transactional(readOnly = true)
    public Summary summary(YearMonth month) {
        UUID ws = workspaceId();
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        long income = repo.sumIncome(ws, from, to);
        long expenses = repo.sumExpenses(ws, from, to);
        long saved = income - expenses;
        int pct = income == 0 ? 0 : (int) Math.round(100.0 * saved / income);
        List<CategorySpend> byCat = repo.spendByCategory(ws, from, to);
        List<BudgetRow> budgetRows = repo.budgets(ws, month.toString());
        return new Summary(month.toString(), income, expenses, saved, pct, "INR", byCat, budgetRows);
    }

    private void maybeBudgetAlerts(UUID workspaceId, UUID categoryId, LocalDate on) {
        String ym = YearMonth.from(on).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        for (BudgetRow row : repo.budgets(workspaceId, ym)) {
            if (!row.categoryId().equals(categoryId) || row.limitMinor() <= 0) {
                continue;
            }
            double pct = 100.0 * row.spentMinor() / row.limitMinor();
            String kind = null;
            if (pct > 100) {
                kind = "BUDGET_EXCEEDED";
            } else if (pct >= 100) {
                kind = "BUDGET_REACHED";
            } else if (pct >= 80) {
                kind = "BUDGET_APPROACHING";
            }
            if (kind != null) {
                repo.insertAlert(TenantContext.requireUserId(), kind,
                        "{\"category\":\"" + row.categoryName() + "\",\"percent\":" + (int) pct + "}");
            }
        }
    }

    private UUID workspaceId() {
        UUID fromJwt = TenantContext.current().map(s -> s.workspaceId()).orElse(null);
        UUID userId = TenantContext.requireUserId();
        UUID owned = identity.personalWorkspaceId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "No personal workspace."));
        if (fromJwt != null && !fromJwt.equals(owned)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You cannot open that workspace.");
        }
        return owned;
    }

    public record Summary(
            String month,
            long incomeMinor,
            long expenseMinor,
            long savedMinor,
            int savedPercent,
            String currency,
            List<CategorySpend> byCategory,
            List<BudgetRow> budgets
    ) {}
}
