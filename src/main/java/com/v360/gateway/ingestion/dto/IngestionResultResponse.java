package com.v360.gateway.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Resultado da ingestão de pedidos de compra")
public record IngestionResultResponse(
        @Schema(description = "Identificador único do cliente", example = "CLI-ALFA-001")
        String clientId,

        @Schema(description = "Quantidade de pedidos processados", example = "1")
        int ordersProcessed,

        @Schema(description = "Quantidade total de itens processados", example = "2")
        int itemsProcessed,

        @Schema(description = "Lista dos números dos pedidos processados", example = "[\"4500001234\"]")
        List<String> orderNumbers,

        @Schema(description = "Mensagem descritiva do resultado", example = "Carga de pedidos processada com sucesso")
        String message,

        @Schema(description = "Timestamp da ingestão")
        Instant processedAt
) {
    public static IngestionResultResponse success(String clientId, List<String> orderNumbers, int itemsProcessed) {
        return new IngestionResultResponse(
                clientId,
                orderNumbers.size(),
                itemsProcessed,
                orderNumbers,
                "Carga de pedidos processada com sucesso",
                Instant.now()
        );
    }
}
