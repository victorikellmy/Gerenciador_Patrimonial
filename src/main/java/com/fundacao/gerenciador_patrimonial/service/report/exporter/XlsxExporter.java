package com.fundacao.gerenciador_patrimonial.service.report.exporter;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * Exporta inventário em XLSX usando SXSSFWorkbook (streaming),
 * seguro para planilhas com milhares de linhas.
 */
@Component
public class XlsxExporter {

    public void exportarInventario(List<LinhaInventario> linhas, OutputStream out) throws IOException {
        // SXSSF mantém só 100 linhas em memória, resto vai para disco (tmp).
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            Sheet sh = wb.createSheet("Inventário");

            CellStyle headerStyle = criarHeaderStyle(wb);
            CellStyle dateStyle = criarDateStyle(wb);
            CellStyle moneyStyle = criarMoneyStyle(wb);

            // Cabeçalho (colunas compartilhadas com o CSV — ver LinhaInventario)
            Row header = sh.createRow(0);
            for (int i = 0; i < LinhaInventario.CABECALHO.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(LinhaInventario.CABECALHO[i]);
                c.setCellStyle(headerStyle);
            }

            // Linhas de dados (depreciação já calculada no mapeamento da linha)
            int rowIdx = 1;
            for (LinhaInventario l : linhas) {
                Row r = sh.createRow(rowIdx++);

                setLong(r, 0, l.id());
                setStr(r, 1, l.tombo());
                setStr(r, 2, l.descricao());
                setStr(r, 3, l.categoria());
                setDate(r, 4, l.dataCompra() != null
                        ? java.sql.Date.valueOf(l.dataCompra()) : null, dateStyle);
                setMoney(r, 5, l.valorCompra(), moneyStyle);
                setStr(r, 6, l.conservacao());
                setStr(r, 7, l.situacao());
                setStr(r, 8, l.notaFiscal());
                setStr(r, 9, l.upm());
                setStr(r, 10, l.lotacaoNome());
                setStr(r, 11, l.responsavelNome());
                if (l.vutAnos() != null) setLong(r, 12, l.vutAnos().longValue());
                setMoney(r, 13, l.depreciacaoAcumulada(), moneyStyle);
                setMoney(r, 14, l.valorContabilLiquido(), moneyStyle);
                setMoney(r, 15, l.depreciacaoAnual(), moneyStyle);
                setDate(r, 16, l.dataBaixa() != null
                        ? java.sql.Date.valueOf(l.dataBaixa()) : null, dateStyle);
                setStr(r, 17, l.motivoBaixa());
            }

            // Congela primeira linha e auto-size nas principais colunas.
            sh.createFreezePane(0, 1);
            // Para SXSSF, auto-size só funciona se as colunas forem "tracked".
            // Aqui deixamos larguras fixas razoáveis para evitar tracking de todas.
            int[] larguras = {5, 12, 40, 15, 12, 14, 14, 10, 14, 10, 30, 30, 10, 18, 14, 18, 12, 30};
            for (int i = 0; i < larguras.length; i++) {
                sh.setColumnWidth(i, larguras[i] * 256);
            }

            wb.write(out);
            // try-with-resources chama close(), que já limpa os arquivos temporários.
        }
    }

    // ----- estilos -----

    private CellStyle criarHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    private CellStyle criarDateStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
        return s;
    }

    private CellStyle criarMoneyStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.getCreationHelper()
                .createDataFormat().getFormat("#,##0.00"));
        return s;
    }

    // ----- setters -----

    private void setStr(Row r, int col, String v) {
        Cell c = r.createCell(col);
        if (v != null) c.setCellValue(v);
    }

    private void setLong(Row r, int col, Long v) {
        Cell c = r.createCell(col);
        if (v != null) c.setCellValue(v);
    }

    private void setDate(Row r, int col, Date v, CellStyle style) {
        Cell c = r.createCell(col);
        if (v != null) {
            c.setCellValue(v);
            c.setCellStyle(style);
        }
    }

    private void setMoney(Row r, int col, BigDecimal v, CellStyle style) {
        Cell c = r.createCell(col);
        if (v != null) {
            c.setCellValue(v.doubleValue());
            c.setCellStyle(style);
        }
    }
}
