package com.fundacao.gerenciador_patrimonial.service.importer;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Leitor robusto de células Excel.
 * Trata strings vazias, valores "-", números no formato string e datas.
 */
public final class CellReader {

    private CellReader() {}

    public static String lerString(Row row, int col) {
        Cell c = row.getCell(col, MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (c == null) return null;
        String valor = switch (c.getCellType()) {
            case STRING -> c.getStringCellValue();
            case NUMERIC -> {
                double d = c.getNumericCellValue();
                yield (d == Math.floor(d)) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            case FORMULA -> lerFormulaComoString(c);
            default -> null;
        };
        if (valor == null) return null;
        valor = valor.trim();
        return (valor.isEmpty() || valor.equals("-")) ? null : valor;
    }

    public static BigDecimal lerBigDecimal(Row row, int col) {
        Cell c = row.getCell(col, MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (c == null) return null;
        try {
            return switch (c.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(c.getNumericCellValue());
                case STRING  -> {
                    String s = c.getStringCellValue().trim().replace(",", ".");
                    if (s.isEmpty() || s.equals("-")) yield null;
                    yield new BigDecimal(s);
                }
                case FORMULA -> BigDecimal.valueOf(c.getNumericCellValue());
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    public static LocalDate lerData(Row row, int col) {
        Cell c = row.getCell(col, MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (c == null) return null;
        try {
            if (c.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                    && DateUtil.isCellDateFormatted(c)) {
                LocalDateTime ldt = c.getLocalDateTimeCellValue();
                return ldt != null ? ldt.toLocalDate() : null;
            }
            if (c.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                // Data armazenada como número sem formato de data
                return DateUtil.getJavaDate(c.getNumericCellValue())
                        .toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
            if (c.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                String s = c.getStringCellValue().trim();
                if (s.isEmpty() || s.equals("-")) return null;
                // Formatos comuns: "dd/MM/yyyy"
                return LocalDate.parse(s,
                        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static String lerFormulaComoString(Cell c) {
        try {
            return switch (c.getCachedFormulaResultType()) {
                case STRING  -> c.getStringCellValue();
                case NUMERIC -> String.valueOf(c.getNumericCellValue());
                default      -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }
}
