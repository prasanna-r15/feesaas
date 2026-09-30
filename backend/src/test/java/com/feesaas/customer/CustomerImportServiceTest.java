package com.feesaas.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.feesaas.customer.application.CustomerImportService;
import com.feesaas.shared.export.XlsxRows;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CustomerImportServiceTest {

    @Test
    void parseQuotedCsv() {
        var cols = CustomerImportService.parseLine("Rahul,\"note, with comma\",x");
        assertThat(cols).containsExactly("Rahul", "note, with comma", "x");
    }

    @Test
    void parsesIsoAndExcelDates() {
        assertThat(XlsxRows.parseDate("2026-10-15")).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(XlsxRows.parseDate("15/10/2026")).isEqualTo(LocalDate.of(2026, 10, 15));
        long serial = java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.of(1899, 12, 30), LocalDate.of(2026, 10, 15));
        assertThat(XlsxRows.parseDate(String.valueOf(serial))).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void keepsPhoneText() {
        assertThat(XlsxRows.normalizePhone("+919876543210")).isEqualTo("+919876543210");
        assertThat(XlsxRows.normalizePhone("9876543210.0")).isEqualTo("9876543210");
    }
}
