package com.feesaas.personal.api;

import com.feesaas.personal.api.dto.CreateCategoryRequest;
import com.feesaas.personal.api.dto.CreateExpenseRequest;
import com.feesaas.personal.api.dto.CreateIncomeRequest;
import com.feesaas.personal.api.dto.UpsertBudgetRequest;
import com.feesaas.personal.application.PersonalFinanceService;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/personal")
public class PersonalFinanceController {

    private final PersonalFinanceService personal;

    public PersonalFinanceController(PersonalFinanceService personal) {
        this.personal = personal;
    }

    @GetMapping("/categories")
    public List<?> categories() {
        return personal.categories();
    }

    @PostMapping("/categories")
    public Object createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return personal.createCategory(request.name(), request.icon(), request.color());
    }

    @GetMapping("/expenses")
    public List<?> expenses(@RequestParam(required = false) String month) {
        return personal.expenses(parseMonth(month));
    }

    @PostMapping("/expenses")
    public Object addExpense(@Valid @RequestBody CreateExpenseRequest request) {
        return personal.addExpense(
                request.amountMinor(), request.categoryId(), request.description(),
                request.occurredOn(), request.method(), request.clientId());
    }

    @GetMapping("/income")
    public List<?> income(@RequestParam(required = false) String month) {
        return personal.income(parseMonth(month));
    }

    @PostMapping("/income")
    public Object addIncome(@Valid @RequestBody CreateIncomeRequest request) {
        return personal.addIncome(
                request.amountMinor(), request.source(), request.description(), request.occurredOn(), request.clientId());
    }

    @GetMapping("/budgets")
    public List<?> budgets(@RequestParam(required = false) String month) {
        return personal.budgets(month);
    }

    @PutMapping("/budgets")
    public List<?> saveBudget(@Valid @RequestBody UpsertBudgetRequest request) {
        return personal.saveBudget(request.categoryId(), request.yearMonth(), request.limitMinor());
    }

    @GetMapping("/summary")
    public Object summary(@RequestParam(required = false) String month) {
        var s = personal.summary(parseMonth(month));
        return Map.of(
                "month", s.month(),
                "incomeMinor", s.incomeMinor(),
                "expenseMinor", s.expenseMinor(),
                "savedMinor", s.savedMinor(),
                "savedPercent", s.savedPercent(),
                "currency", s.currency(),
                "byCategory", s.byCategory(),
                "budgets", s.budgets());
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        return YearMonth.parse(month);
    }
}
