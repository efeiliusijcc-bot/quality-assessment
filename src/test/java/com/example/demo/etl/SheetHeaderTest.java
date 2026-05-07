package com.example.demo.etl;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

class SheetHeaderTest {

    @Test
    void printHeaders() throws Exception {
        String dir = "test_data/";
        String[] files = {"core_data.xlsx", "eval_data.xlsx", "kg_data.xlsx"};

        for (String fname : files) {
            File f = new File(dir + fname);
            if (!f.exists()) continue;
            try (Workbook wb = new XSSFWorkbook(new FileInputStream(f))) {
                System.out.println("=== " + fname + " ===");
                for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                    Sheet sheet = wb.getSheetAt(i);
                    Row header = sheet.getRow(0);
                    if (header == null) continue;
                    List<String> headers = new ArrayList<>();
                    for (Cell c : header) {
                        headers.add(c.getStringCellValue().trim());
                    }
                    System.out.println("  " + sheet.getSheetName() + ": " + String.join(", ", headers));
                }
            }
        }
    }
}
