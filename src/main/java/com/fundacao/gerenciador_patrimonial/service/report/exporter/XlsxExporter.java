package com.fundacao.gerenciador_patrimonial.service.report.exporter;

import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.service.DepreciacaoService;
import com.fundacao.gerenciador_patrimonial.service.DepreciacaoService.CalculoDepreciacao;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class XlsxExporter {

    private final DepreciacaoService depreciacaoService;

    public void exportarInventario(List<Patrimonio> lista, OutputStream out) throws IOException {
        // SXSSF mantém só 100 linhas em memória, resto vai para disco (tmp).
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            Sheet sh = wb.createSheet("Inventário");

            CellStyle headerStyle = criarHeaderStyle(wb);
            CellStyle dateStyle = criarDateStyle(wb);
            CellStyle moneyStyle = criarMoneyStyle(wb);

            // Cabeçalho
            String[] cols = {
                    "ID", "Tombo", "Descrição", "Categoria", "Data Compra", "Valor Compra",
                    "Conservação", "Situação", "Nota Fiscal",
                    "UPM", "Lotação", "Responsável",
                    "VUT (anos)", "Depreciação Acumulada", "VCL", "Depreciação Anual",
                    "Data Baixa", "Motivo Baixa"
            };
            Row header = sh.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(headerStyle);
            }

            // Linhas de dados
            int rowIdx = 1;
            for (Patrimonio p : lista) {
                CalculoDepreciacao calc = depreciacaoService.calcular(p);
                Row r = sh.createRow(rowIdx++);

                setLong(r, 0, p.getId());
                setStr(r, 1, p.getNumeroTombo());
                setStr(r, 2, p.getDescricao());
                setStr(r, 3, p.getCategoria());
                setDate(r, 4, p.getDataCompra() != null
                        ? java.sql.Date.valueOf(p.getDataCompra()) : null, dateStyle);
                setMoney(r, 5, p.getValorCompra(), moneyStyle);
                setStr(r, 6, p.getConservacao() != null ? p.getConservacao().name() : null);
                setStr(r, 7, p.getSituacao() != null ? p.getSituacao().name() : null);
                setStr(r, 8, p.getNotaFiscal());
                setStr(r, 9, p.getLotacao() != null ? p.getLotacao().getUpm() : null);
                setStr(r, 10, p.getLotacao() != null ? p.getLotacao().getNome() : null);
                setStr(r, 11, p.getResponsavel() != null ? p.getResponsavel().getNomeCompleto() : null);
                if (calc.vutAnos() != null) setLong(r, 12, calc.vutAnos().longValue());
                setMoney(r, 13, calc.depreciacaoAcumulada(), moneyStyle);
                setMoney(r, 14, calc.valorContabilLiquido(), moneyStyle);
                setMoney(r, 15, calc.depreciacaoAnual(), moneyStyle);
                setDate(r, 16, p.getDataBaixa() != null
                        ? java.sql.Date.valueOf(p.getDataBaixa()) : null, dateStyle);
                setStr(r, 17, p.getMotivoBaixa());
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
