package com.v360.gateway.ingestion.adapter.beta;

import com.v360.gateway.common.exception.ApiException;
import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.model.PurchaseOrderItem;
import com.v360.gateway.domain.model.Vendor;
import com.v360.gateway.ingestion.adapter.beta.dto.BetaHeaderRow;
import com.v360.gateway.ingestion.adapter.beta.dto.BetaItemRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class BetaCsvAdapter {

    public static final String BETA_CLIENT_ID = "CLI-BETA-002";

    private static final DateTimeFormatter BR_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern THOUSANDS_SEPARATOR_ONLY = Pattern.compile("^\\d{1,3}(\\.\\d{3})+$");

    public List<PurchaseOrder> parse(String headersCsv, String itemsCsv) {
        if ((headersCsv == null || headersCsv.isBlank()) && (itemsCsv == null || itemsCsv.isBlank())) {
            return List.of();
        }

        List<BetaHeaderRow> headerRows = parseHeaders(headersCsv);
        Map<String, List<BetaItemRow>> itemsByOrder = parseItems(itemsCsv);

        // Validar integridade referencial: detectar itens órfãos sem cabeçalho correspondente
        Set<String> validOrderNumbers = new HashSet<>();
        for (BetaHeaderRow h : headerRows) {
            validOrderNumbers.add(h.poNumber());
        }

        for (String itemPoNumber : itemsByOrder.keySet()) {
            if (!validOrderNumbers.contains(itemPoNumber)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ORPHAN_ORDER_ITEMS",
                        "Itens encontrados com NUMERO_PEDIDO (" + itemPoNumber + ") sem cabeçalho correspondente no cabecalho.csv"
                );
            }
        }

        // Construir entidades canônicas do domínio
        List<PurchaseOrder> orders = new ArrayList<>();
        for (BetaHeaderRow header : headerRows) {
            Vendor vendor = new Vendor(header.vendorTaxId(), header.vendorName());
            PurchaseOrder order = new PurchaseOrder(
                    BETA_CLIENT_ID,
                    header.poNumber(),
                    header.issueDate(),
                    header.status(),
                    header.currency(),
                    vendor
            );

            List<BetaItemRow> itemRows = itemsByOrder.getOrDefault(header.poNumber(), List.of());
            for (BetaItemRow itemRow : itemRows) {
                PurchaseOrderItem item = new PurchaseOrderItem(
                        itemRow.lineNumber(),
                        itemRow.materialCode(),
                        itemRow.description(),
                        itemRow.uom(),
                        itemRow.quantityOrdered(),
                        itemRow.quantityReceived(),
                        itemRow.unitPrice()
                );
                order.addItem(item);
            }

            orders.add(order);
        }

        return orders;
    }

    private List<BetaHeaderRow> parseHeaders(String headersCsv) {
        List<BetaHeaderRow> rows = new ArrayList<>();
        if (headersCsv == null || headersCsv.isBlank()) {
            return rows;
        }

        String[] lines = headersCsv.split("\\r?\\n");
        if (lines.length == 0) {
            return rows;
        }

        Map<String, Integer> headerIndex = parseHeaderLine(lines[0]);

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) {
                continue;
            }

            List<String> cols = parseCsvLine(line);

            String poNumber = getColValue(cols, headerIndex, "NUMERO_PEDIDO");
            if (poNumber.isBlank()) {
                continue;
            }

            String rawCnpj = getColValue(cols, headerIndex, "FORNECEDOR_CNPJ");
            String cnpj = sanitizeCnpj(rawCnpj);
            String vendorName = getColValue(cols, headerIndex, "FORNECEDOR_RAZAO_SOCIAL");
            LocalDate issueDate = parseBrazilianDate(getColValue(cols, headerIndex, "EMISSAO"));
            OrderStatus status = parseBetaStatus(getColValue(cols, headerIndex, "SITUACAO"));
            String currency = getColValue(cols, headerIndex, "MOEDA");
            if (currency.isBlank()) {
                currency = "BRL";
            }

            rows.add(new BetaHeaderRow(poNumber, cnpj, vendorName, issueDate, status, currency));
        }

        return rows;
    }

    private Map<String, List<BetaItemRow>> parseItems(String itemsCsv) {
        Map<String, List<BetaItemRow>> result = new LinkedHashMap<>();
        if (itemsCsv == null || itemsCsv.isBlank()) {
            return result;
        }

        String[] lines = itemsCsv.split("\\r?\\n");
        if (lines.length == 0) {
            return result;
        }

        Map<String, Integer> headerIndex = parseHeaderLine(lines[0]);

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) {
                continue;
            }

            List<String> cols = parseCsvLine(line);

            String poNumber = getColValue(cols, headerIndex, "NUMERO_PEDIDO");
            if (poNumber.isBlank()) {
                continue;
            }

            int lineNum = parseInteger(getColValue(cols, headerIndex, "ITEM"), 1);
            String materialCode = getColValue(cols, headerIndex, "CODIGO_MATERIAL");
            String description = getColValue(cols, headerIndex, "DESCRICAO");
            String uom = getColValue(cols, headerIndex, "UNIDADE");
            if (uom.isBlank()) {
                uom = "UN";
            }

            BigDecimal qtyOrdered = parseBrazilianNumber(getColValue(cols, headerIndex, "QTD_PEDIDA"), "QTD_PEDIDA");
            BigDecimal qtyReceived = parseBrazilianNumber(getColValue(cols, headerIndex, "QTD_RECEBIDA"), "QTD_RECEBIDA");
            BigDecimal unitPrice = parseBrazilianNumber(getColValue(cols, headerIndex, "PRECO_UNITARIO"), "PRECO_UNITARIO");

            BetaItemRow item = new BetaItemRow(
                    poNumber,
                    lineNum,
                    materialCode,
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

    private Map<String, Integer> parseHeaderLine(String headerLine) {
        Map<String, Integer> map = new HashMap<>();
        List<String> cols = parseCsvLine(headerLine);
        for (int i = 0; i < cols.size(); i++) {
            String clean = cleanCol(cols.get(i)).toUpperCase();
            map.put(clean, i);
        }
        return map;
    }

    /**
     * RFC 4180 compliant CSV line tokenizer for semicolon-separated values.
     * Preserves semicolons inside quoted values and unescapes double quotes.
     */
    public static List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        if (line == null) {
            return tokens;
        }

        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++; // skip escaped quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ';' && !inQuotes) {
                tokens.add(cleanCol(sb.toString()));
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(cleanCol(sb.toString()));
        return tokens;
    }

    private String getColValue(List<String> cols, Map<String, Integer> headerIndex, String colName) {
        Integer idx = headerIndex.get(colName.toUpperCase());
        if (idx != null && idx < cols.size()) {
            return cols.get(idx);
        }
        return "";
    }

    private static String cleanCol(String val) {
        if (val == null) {
            return "";
        }
        String trimmed = val.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    public static BigDecimal parseBrazilianNumber(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MALFORMED_NUMBER",
                    "Campo numérico obrigatório '" + fieldName + "' não pode ser nulo ou vazio"
            );
        }
        String cleaned = raw.trim();

        try {
            // Se contém vírgula, segue o padrão decimal brasileiro (ex: "1.200,000" ou "6,49")
            if (cleaned.contains(",")) {
                cleaned = cleaned.replace(".", "").replace(",", ".");
            } else if (THOUSANDS_SEPARATOR_ONLY.matcher(cleaned).matches()) {
                // Inteiro com separador de milhar sem decimais (ex: "1.200")
                cleaned = cleaned.replace(".", "");
            }
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MALFORMED_NUMBER",
                    "Valor numérico inválido no campo '" + fieldName + "': " + raw
            );
        }
    }

    public static LocalDate parseBrazilianDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DATE_FORMAT",
                    "Data de emissão obrigatória ausente no cabeçalho do pedido"
            );
        }
        String trimmed = raw.trim();
        try {
            return LocalDate.parse(trimmed, BR_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DATE_FORMAT",
                    "Data de emissão inválida ou fora do padrão DD/MM/YYYY: '" + raw + "'"
            );
        }
    }

    public static OrderStatus parseBetaStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ORDER_STATUS",
                    "Situação de pedido obrigatória ausente no cabeçalho do pedido"
            );
        }
        String normalized = raw.trim().toUpperCase();
        return switch (normalized) {
            case "EM ABERTO", "ABERTO" -> OrderStatus.OPEN;
            case "ENCERRADO", "FECHADO" -> OrderStatus.CLOSED;
            case "BLOQUEADO" -> OrderStatus.BLOCKED;
            default -> throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ORDER_STATUS",
                    "Situação de pedido inválida para o Cliente Beta: '" + raw + "'. Esperado: EM ABERTO, ENCERRADO ou BLOQUEADO"
            );
        };
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
