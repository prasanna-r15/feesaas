package com.feesaas.personal.infra;

import com.github.f4b6a3.uuid.UuidCreator;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PersonalFinanceRepository {

    private final JdbcClient jdbc;

    public PersonalFinanceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<CategoryRow> categories(UUID workspaceId) {
        return jdbc.sql("""
                select id, name, icon, color, system_key, active
                  from personal_expense_categories
                 where workspace_id = :ws
                 order by name
                """)
                .param("ws", workspaceId)
                .query((rs, i) -> new CategoryRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("icon"),
                        rs.getString("color"),
                        rs.getString("system_key"),
                        rs.getBoolean("active")))
                .list();
    }

    public UUID insertCategory(UUID workspaceId, String name, String icon, String color) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into personal_expense_categories (id, workspace_id, name, icon, color, active)
                values (:id, :ws, :name, :icon, :color, true)
                """)
                .param("id", id).param("ws", workspaceId).param("name", name)
                .param("icon", icon).param("color", color)
                .update();
        return id;
    }

    public UUID insertExpense(UUID workspaceId, UUID categoryId, long amount, String currency,
            String description, LocalDate occurredOn, String method, UUID clientId) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into personal_expenses (id, workspace_id, category_id, amount_minor, currency, description, occurred_on, method, client_id)
                values (:id, :ws, :cat, :amt, :cur, :desc, :on, :method, :client)
                """)
                .param("id", id).param("ws", workspaceId).param("cat", categoryId)
                .param("amt", amount).param("cur", currency).param("desc", description)
                .param("on", occurredOn).param("method", method).param("client", clientId)
                .update();
        return id;
    }

    public List<ExpenseRow> expenses(UUID workspaceId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                select e.id, e.amount_minor, e.currency, e.description, e.occurred_on, e.method,
                       c.id as category_id, c.name as category_name, c.color as category_color
                  from personal_expenses e
                  join personal_expense_categories c on c.id = e.category_id
                 where e.workspace_id = :ws and e.occurred_on >= :from and e.occurred_on <= :to
                 order by e.occurred_on desc, e.created_at desc
                """)
                .param("ws", workspaceId).param("from", from).param("to", to)
                .query((rs, i) -> new ExpenseRow(
                        rs.getObject("id", UUID.class),
                        rs.getLong("amount_minor"),
                        rs.getString("currency"),
                        rs.getString("description"),
                        rs.getObject("occurred_on", LocalDate.class),
                        rs.getString("method"),
                        rs.getObject("category_id", UUID.class),
                        rs.getString("category_name"),
                        rs.getString("category_color")))
                .list();
    }

    public UUID insertIncome(UUID workspaceId, long amount, String currency, String source, String description, LocalDate occurredOn, UUID clientId) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into personal_income (id, workspace_id, amount_minor, currency, source, description, occurred_on, client_id)
                values (:id, :ws, :amt, :cur, :source, :desc, :on, :client)
                """)
                .param("id", id).param("ws", workspaceId).param("amt", amount).param("cur", currency)
                .param("source", source).param("desc", description).param("on", occurredOn).param("client", clientId)
                .update();
        return id;
    }

    public List<IncomeRow> income(UUID workspaceId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                select id, amount_minor, currency, source, description, occurred_on
                  from personal_income
                 where workspace_id = :ws and occurred_on >= :from and occurred_on <= :to
                 order by occurred_on desc
                """)
                .param("ws", workspaceId).param("from", from).param("to", to)
                .query((rs, i) -> new IncomeRow(
                        rs.getObject("id", UUID.class),
                        rs.getLong("amount_minor"),
                        rs.getString("currency"),
                        rs.getString("source"),
                        rs.getString("description"),
                        rs.getObject("occurred_on", LocalDate.class)))
                .list();
    }

    public UUID upsertBudget(UUID workspaceId, UUID categoryId, String yearMonth, long limit, String currency) {
        Optional<UUID> existing = jdbc.sql("""
                select id from personal_budgets where workspace_id = :ws and category_id = :cat and year_month = :ym
                """)
                .param("ws", workspaceId).param("cat", categoryId).param("ym", yearMonth)
                .query(UUID.class).optional();
        if (existing.isPresent()) {
            jdbc.sql("update personal_budgets set limit_minor = :lim where id = :id")
                    .param("lim", limit).param("id", existing.get()).update();
            return existing.get();
        }
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into personal_budgets (id, workspace_id, category_id, year_month, limit_minor, currency)
                values (:id, :ws, :cat, :ym, :lim, :cur)
                """)
                .param("id", id).param("ws", workspaceId).param("cat", categoryId)
                .param("ym", yearMonth).param("lim", limit).param("cur", currency)
                .update();
        return id;
    }

    public List<BudgetRow> budgets(UUID workspaceId, String yearMonth) {
        return jdbc.sql("""
                select b.id, b.limit_minor, b.currency, c.id as category_id, c.name as category_name, c.color,
                       coalesce((select sum(e.amount_minor) from personal_expenses e
                                 where e.workspace_id = b.workspace_id and e.category_id = b.category_id
                                   and to_char(e.occurred_on, 'YYYY-MM') = b.year_month), 0) as spent
                  from personal_budgets b
                  join personal_expense_categories c on c.id = b.category_id
                 where b.workspace_id = :ws and b.year_month = :ym
                """)
                .param("ws", workspaceId).param("ym", yearMonth)
                .query((rs, i) -> new BudgetRow(
                        rs.getObject("id", UUID.class),
                        rs.getLong("limit_minor"),
                        rs.getLong("spent"),
                        rs.getString("currency"),
                        rs.getObject("category_id", UUID.class),
                        rs.getString("category_name"),
                        rs.getString("color")))
                .list();
    }

    public long sumExpenses(UUID workspaceId, LocalDate from, LocalDate to) {
        Long v = jdbc.sql("""
                select coalesce(sum(amount_minor), 0) from personal_expenses
                 where workspace_id = :ws and occurred_on >= :from and occurred_on <= :to
                """)
                .param("ws", workspaceId).param("from", from).param("to", to)
                .query(Long.class).optional().orElse(0L);
        return v;
    }

    public long sumIncome(UUID workspaceId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                select coalesce(sum(amount_minor), 0) from personal_income
                 where workspace_id = :ws and occurred_on >= :from and occurred_on <= :to
                """)
                .param("ws", workspaceId).param("from", from).param("to", to)
                .query(Long.class).optional().orElse(0L);
    }

    public List<CategorySpend> spendByCategory(UUID workspaceId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                select c.name, c.color, coalesce(sum(e.amount_minor), 0) as spent
                  from personal_expense_categories c
                  left join personal_expenses e on e.category_id = c.id
                       and e.occurred_on >= :from and e.occurred_on <= :to
                 where c.workspace_id = :ws and c.active = true
                 group by c.name, c.color
                having coalesce(sum(e.amount_minor), 0) > 0
                 order by spent desc
                """)
                .param("ws", workspaceId).param("from", from).param("to", to)
                .query((rs, i) -> new CategorySpend(rs.getString("name"), rs.getString("color"), rs.getLong("spent")))
                .list();
    }

    public void insertAlert(UUID userId, String kind, String payloadJson) {
        jdbc.sql("insert into user_alerts (id, user_id, kind, payload) values (:id, :userId, :kind, :payload::jsonb)")
                .param("id", UuidCreator.getTimeOrderedEpoch())
                .param("userId", userId)
                .param("kind", kind)
                .param("payload", payloadJson)
                .update();
    }

    public record CategoryRow(UUID id, String name, String icon, String color, String systemKey, boolean active) {}
    public record ExpenseRow(UUID id, long amountMinor, String currency, String description, LocalDate occurredOn,
                             String method, UUID categoryId, String categoryName, String categoryColor) {}
    public record IncomeRow(UUID id, long amountMinor, String currency, String source, String description, LocalDate occurredOn) {}
    public record BudgetRow(UUID id, long limitMinor, long spentMinor, String currency, UUID categoryId, String categoryName, String color) {}
    public record CategorySpend(String name, String color, long spentMinor) {}
}
