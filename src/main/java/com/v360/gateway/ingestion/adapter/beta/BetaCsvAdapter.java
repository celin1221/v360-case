package com.v360.gateway.ingestion.adapter.beta;

import com.v360.gateway.common.exception.ApiException;
import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.model.PurchaseOrderItem;
import com.v360.gateway.domain.model.Vendor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class BetaCsvAdapter {

    public static final String BETA_CLIENT_ID = "CLI-BETA-002";

    private static final DateTimeFormatter BR_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public List<PurchaseOrder> parse(String cabecalhoCsv, String itensCsv) {
        if ((cabecalhoCsv == null || cabecalhoCsv.isBlank()) && (itensCsv == null || itensCsv.isBlank())) {
            return List.of();
        }

        try {
            Map<String, List<PurchaseOrderItem>> itemsByOrder = parseItens(itensCsv);
            return parseCabecalho(cabecalhoCsv, itemsByOrder);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MALFORMED_BETA_CSV",
                    "Erro ao processar arquivos CSV do Cliente Beta: " + e.getMessage()
            );
        }
    }

    private Map<String, List<PurchaseOrderItem>> parseItens(String itensCsv) {
        Map<String, List<PurchaseOrderItem>> result = new LinkedHashMap<>();
        if (itensCsv == null || itensCsv.isBlank()) {
            return result;
        }

        String[] lines = itensCsv.split("\\r?\\n");
        if (lines.length == 0) {
            return result;
        }

        Map<String, Integer> headerIndex = parseHeaderLine(lines[0]);

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) {
                continue;
            }

            String[] cols = splitCsvLine(line);

            String poNumber = getColValue(cols, headerIndex, "NUMERO_PEDIDO");
            if (poNumber.isBlank()) {
                continue;
            }

            int lineNum = parseInteger(getColValue(cols, headerIndex, "ITEM"), 1);
            String material = getColValue(cols, headerIndex, "CODIGO_MATERIAL");
            String description = getColValue(cols, headerIndex, "DESCRICAO");
            String uom = getColValue(cols, headerIndex, "UNIDADE");
            if (uom.isBlank()) {
                uom = "UN";
            }
            BigDecimal qtyOrdered = parseBrazilianNumber(getColValue(cols, headerIndex, "QTD_PEDIDA"));
            BigDecimal qtyReceived = parseBrazilianNumber(getColValue(cols, headerIndex, "QTD_RECEBIDA"));
            BigDecimal unitPrice = parseBrazilianNumber(getColValue(cols, headerIndex, "PRECO_UNITARIO"));

            PurchaseOrderItem item = new PurchaseOrderItem(
                    lineNum,
                    material,
                    description,
                    uom,
                    qtyOrdered,
                    qtyReceived,
                    unitPrice
            );

            result.computeIfAbsent(poNumber, k -> new ArrayList<>()).add(item);
        }

        return result;
    }

    private List<PurchaseOrder> parseCabecalho(String cabecalhoCsv, Map<String, List<PurchaseOrderItem>> itemsByOrder) {
        List<PurchaseOrder> orders = new ArrayList<>();
        if (cabecalhoCsv == null || cabecalhoCsv.isBlank()) {
            return orders;
        }

        String[] lines = cabecalhoCsv.split("\\r?\\n");
        if (lines.length == 0) {
            return orders;
        }

        Map<String, Integer> headerIndex = parseHeaderLine(lines[0]);

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) {
                continue;
            }

            String[] cols = splitCsvLine(line);

            String poNumber = getColValue(cols, headerIndex, "NUMERO_PEDIDO");
            if (poNumber.isBlank()) {
                continue;
            }

            String cnpj = sanitizeCnpj(getColValue(cols, headerIndex, "FORNECEDOR_CNPJ"));
            String razaoSocial = getColValue(cols, headerIndex, "FORNECEDOR_RAZAO_SOCIAL");
            LocalDate emissao = parseBrazilianDate(getColValue(cols, headerIndex, "EMISSAO"));
            OrderStatus status = OrderStatus.fromBetaStatus(getColValue(cols, headerIndex, "SITUACAO"));
            String moeda = getColValue(cols, headerIndex, "MOEDA");
            if (moeda.isBlank()) {
                moeda = "BRL";
            }

            Vendor vendor = new Vendor(cnpj, razaoSocial);
            PurchaseOrder order = new PurchaseOrder(
                    BETA_CLIENT_ID,
                    poNumber,
                    emissao,
                    status,
                    moeda,
                    vendor
            );

            List<PurchaseOrderItem> items = itemsByOrder.getOrDefault(poNumber, List.of());
            for (PurchaseOrderItem item : items) {
                order.addItem(item);
            }

            orders.add(order);
        }

        return orders;
    }

    private Map<String, Integer> parseHeaderLine(String headerLine) {
        Map<String, Integer> map = new HashMap<>();
        String[] cols = splitCsvLine(headerLine);
        for (int i = 0; i < cols.length; i++) {
            String clean = cleanCol(cols[i]).toUpperCase();
            map.put(clean, i);
        }
        return map;
    }

    private String[] splitCsvLine(String line) {
        return line.split(";", -1);
    }

    private String getColValue(String[] cols, Map<String, Integer> headerIndex, String colName) {
        Integer idx = headerIndex.get(colName.toUpperCase());
        if (idx != null && idx < cols.length) {
            return cleanCol(cols[idx]);
        }
        return "";
    }

    private String cleanCol(String val) {
        if (val == null) {
            return "";
        }
        String trimmed = val.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    public static BigDecimal parseBrazilianNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        String cleaned = raw.trim();
        // Se contém vírgula, assume pontuação BR: remove pontos de milhar e troca vírgula por ponto
        if (cleaned.contains(",")) {
            cleaned = cleaned.replace(".", "").replace(",", ".");
        }
        return new BigDecimal(cleaned);
    }

    public static LocalDate parseBrazilianDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDate.now();
        }
        String trimmed = raw.trim();
        try {
            return LocalDate.parse(trimmed, BR_DATE_FORMAT);
        } catch (Exception e) {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    public static String sanitizeCnpj(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\D", "");
    }

    private int parseInteger(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
