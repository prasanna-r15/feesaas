package com.feesaas.shared.persistence;

import java.sql.SQLException;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

/** Ensures the restricted app role exists before V001, so a stock postgres user can migrate. */
@Component
public class EnsureAppRoleFlywayCallback implements Callback {

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        try (var statement = context.getConnection().createStatement()) {
            statement.execute("""
                    DO $$
                    BEGIN
                      IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'feesaas_app') THEN
                        CREATE ROLE feesaas_app LOGIN PASSWORD 'feesaas_app'
                          NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
                      END IF;
                    END $$;
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not ensure feesaas_app role", e);
        }
    }

    @Override
    public String getCallbackName() {
        return "ensureAppRole";
    }
}
