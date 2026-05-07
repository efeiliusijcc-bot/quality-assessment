package com.example.demo.etl;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;

class SheetNameTest {

    @Test
    void printSheetNames() throws Exception {
        String dir = "test_data/";
        String[] files = {"core_data.xlsx", "prod_data.xlsx", "qc_data.xlsx", "eval_data.xlsx", "kg_data.xlsx"};

        for (String fname : files) {
            File f = new File(dir + fname);
            if (!f.exists()) {
                System.out.println(fname + ": FILE NOT FOUND");
                continue;
            }
            try (Workbook wb = new XSSFWorkbook(new FileInputStream(f))) {
                System.out.println(fname + ":");
                for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                    Sheet sheet = wb.getSheetAt(i);
                    System.out.println("  [" + i + "] " + sheet.getSheetName()
                        + " (rows: " + sheet.getPhysicalNumberOfRows() + ")");
                }
            }
        }
    }
}
