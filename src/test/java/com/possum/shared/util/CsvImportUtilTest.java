package com.possum.shared.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvImportUtilTest {

    @Test
    void parseCsv_handlesQuotedCommasAndEscapedQuotes() {
        String csv = "A,B,C\n1,\"x,y\",\"he said \"\"hi\"\"\"";
        List<List<String>> rows = CsvImportUtil.parseCsv(csv);

        assertEquals(2, rows.size());
        assertEquals(List.of("A", "B", "C"), rows.get(0));
        assertEquals(List.of("1", "x,y", "he said \"hi\""), rows.get(1));
    }

    @Test
    void findHeaderRowIndex_detectsHeaderAfterReportPreamble() {
        String csv = """
                PROBE MEDICALS,,,,
                Report Generated On : 26/08/25 8:49:03 PM,,,,
                ,,,,
                Product Code,Product Name,Minimum Stock Level,MRP,Avg Item Cost,Division Name
                1,Vinayagar Statue,0,9999.00,100.00,GIFT ITEMS
                """;

        List<List<String>> rows = CsvImportUtil.parseCsv(csv);
        int headerIndex = CsvImportUtil.findHeaderRowIndex(rows, "Product Code", "Product Name");

        assertEquals(3, headerIndex);
    }

    @Test
    void parseDecimalAndInteger_extractNumericValuesFromNoisyInput() {
        assertEquals(new BigDecimal("107359.50"), CsvImportUtil.parseDecimal("107,359.50 INR", BigDecimal.ZERO));
        assertEquals(12, CsvImportUtil.parseInteger(" stock alert: 12 pcs", 0));
    }
}
