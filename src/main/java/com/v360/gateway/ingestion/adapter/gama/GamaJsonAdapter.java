package com.v360.gateway.ingestion.adapter.gama;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v360.gateway.common.exception.ApiException;
import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.model.PurchaseOrderItem;
import com.v360.gateway.domain.model.Vendor;
import com.v360.gateway.ingestion.adapter.gama.dto.GamaItemDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GamaJsonAdapter {

    public static final String GAMA_CLIENT_ID = "CLI-GAMA-003";
    private static final String BASE_UOM = "UN";
    private final ObjectMapper objectMapper;

    public GamaJsonAdapter() {
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public List<PurchaseOrder> parse(String jsonContent) {
        if (jsonContent == null || jsonContent.isBlank()) {
            return List.of();
        }

        List<GamaItemDto> itemDtos;
        try {
            itemDtos = objectMapper.readValue(jsonContent, new TypeReference<List<GamaItemDto>>() {});
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MALFORMED_GAMA_PAYLOAD",
                    "Malformed flat JSON payload for Client Gama: " + e.getMessage()
            );
        }

        if (itemDtos == null || itemDtos.isEmpty()) {
            return List.of();
        }

        // Agrupa itens flat por número de pedido preservando ordem de inserção
        Map<String, List<GamaItemDto>> ordersMap = new LinkedHashMap<>();
        for (GamaItemDto itemDto : itemDtos) {
            if (itemDto.ped() == null || itemDto.ped().isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_ORDER_NUMBER",
                        "O número de Purchase Order ('ped') é obrigatório para todos os registros do Cliente Gama"
                );
            }
            ordersMap.computeIfAbsent(itemDto.ped().trim(), k -> new ArrayList<>()).add(itemDto);
        }

        List<PurchaseOrder> purchaseOrders = new ArrayList<>();
        for (Map.Entry<String, List<GamaItemDto>> entry : ordersMap.entrySet()) {
            String poNumber = entry.getKey();
            List<GamaItemDto> items = entry.getValue();
            purchaseOrders.add(mapToPurchaseOrder(poNumber, items));
        }

        return purchaseOrders;
    }

    private PurchaseOrder mapToPurchaseOrder(String poNumber, List<GamaItemDto> items) {
        GamaItemDto firstItem = items.get(0);

        LocalDate createdAt = parseEpochSeconds(firstItem.dtCriacao());
        OrderStatus status = parseOrderStatus(firstItem.situacao());
        String currency = "BRL";

        Vendor vendor = new Vendor(
                firstItem.cnpjFornecedor() != null ? firstItem.cnpjFornecedor().trim() : "",
                firstItem.nomeFornecedor() != null ? firstItem.nomeFornecedor().trim() : ""
        );

        PurchaseOrder order = new PurchaseOrder(
                GAMA_CLIENT_ID,
                poNumber,
                createdAt,
                status,
                currency,
                vendor
        );

        for (GamaItemDto itemDto : items) {
            PurchaseOrderItem item = mapToPurchaseOrderItem(itemDto);
            order.addItem(item);
        }

        return order;
    }

    private PurchaseOrderItem mapToPurchaseOrderItem(GamaItemDto dto) {
        Integer lineNumber = dto.item() != null ? dto.item() : 1;
        String materialCode = dto.codMat() != null ? dto.codMat().trim() : "";
        String description = dto.descMat() != null ? dto.descMat().trim() : "";
        String originalUom;
        if (dto.um() == null || dto.um().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MISSING_ORIGINAL_UOM",
                    "A unidade de medida comercial original ('um') é obrigatória para o material: " + materialCode
            );
        }
        originalUom = dto.um().trim();

        BigDecimal factor = dto.fatorConv() != null ? dto.fatorConv() : BigDecimal.ONE;
        if (factor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_CONVERSION_FACTOR",
                    "O fator de conversão ('fator_conv') deve ser maior que zero para o material: " + materialCode
            );
        }

        BigDecimal originalQuantity = dto.qtdPed() != null
                ? dto.qtdPed().setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        BigDecimal originalReceived = dto.qtdRec() != null
                ? dto.qtdRec().setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        // Normalização para unidade base (ADR-0003)
        BigDecimal quantityOrdered = originalQuantity.multiply(factor).setScale(4, RoundingMode.HALF_UP);
        BigDecimal quantityReceived = originalReceived.multiply(factor).setScale(4, RoundingMode.HALF_UP);

        // Conversão monetária: preco_unit_centavos para Reais e divisão pelo fator de conversão
        BigDecimal unitPrice = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        if (dto.precoUnitCentavos() != null) {
            BigDecimal priceInReais = BigDecimal.valueOf(dto.precoUnitCentavos())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            unitPrice = priceInReais.divide(factor, 4, RoundingMode.HALF_UP);
        }

        PurchaseOrderItem item = new PurchaseOrderItem(
                lineNumber,
                materialCode,
                description,
                BASE_UOM,
                quantityOrdered,
                quantityReceived,
                unitPrice
        );

        // Preservação de auditoria dos metadados comerciais originais (ADR-0003)
        item.setOriginalUom(originalUom);
        item.setOriginalQuantity(originalQuantity);
        item.setConversionFactor(factor.setScale(4, RoundingMode.HALF_UP));

        return item;
    }

    private LocalDate parseEpochSeconds(Long seconds) {
        if (seconds == null || seconds <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_CREATION_DATE",
                    "A data de criação ('dt_criacao') é obrigatória e deve ser um timestamp Unix válido em segundos"
            );
        }
        return Instant.ofEpochSecond(seconds).atZone(ZoneOffset.UTC).toLocalDate();
    }

    private OrderStatus parseOrderStatus(Integer statusNumber) {
        if (statusNumber == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STATUS",
                    "Order status ('situacao') é obrigatório para o Cliente Gama"
            );
        }
        return switch (statusNumber) {
            case 1 -> OrderStatus.OPEN;
            case 2 -> OrderStatus.CLOSED;
            case 3 -> OrderStatus.BLOCKED;
            default -> throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STATUS",
                    "Valor numérico de Order Status inválido para o Cliente Gama (esperado 1=OPEN, 2=CLOSED, 3=BLOCKED): " + statusNumber
            );
        };
    }
}
