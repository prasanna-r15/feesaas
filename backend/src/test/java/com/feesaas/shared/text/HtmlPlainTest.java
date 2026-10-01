package com.feesaas.shared.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HtmlPlainTest {

    @Test
    void boldAndHeadingBecomeWhatsAppMarkers() {
        String out = HtmlPlain.text("<h3>Breakfast</h3><p>Have <b>oats</b> and <i>eggs</i>.</p>");
        assertTrue(out.contains("*Breakfast*"));
        assertTrue(out.contains("*oats*"));
        assertTrue(out.contains("_eggs_"));
    }

    @Test
    void tablesBecomeMonospacePipes() {
        String html = """
                <table>
                  <tr><th>Meal</th><th>Food</th></tr>
                  <tr><td>Lunch</td><td>Rice</td></tr>
                </table>
                """;
        String out = HtmlPlain.text(html);
        assertTrue(out.contains("```"));
        assertTrue(out.contains("Meal | Food"));
        assertTrue(out.contains("Lunch | Rice"));
        assertEquals(-1, out.indexOf("<td>"));
    }
}
