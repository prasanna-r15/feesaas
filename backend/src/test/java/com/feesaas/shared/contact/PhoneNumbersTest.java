package com.feesaas.shared.contact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    void internationalizesTenDigitIndianNumbers() {
        assertThat(PhoneNumbers.international("9876543210")).isEqualTo("919876543210");
        assertThat(PhoneNumbers.international("+91 98765 43210")).isEqualTo("919876543210");
    }

    @Test
    void buildsWhatsAppAndSmsLinks() {
        String wa = PhoneNumbers.waLink("9876543210", "Hi Rahul");
        assertThat(wa).startsWith("https://wa.me/919876543210?text=");
        assertThat(wa).contains("Hi");
        String sms = PhoneNumbers.smsLink("9876543210", "Hi");
        assertThat(sms).startsWith("sms:+919876543210?body=");
    }
}
