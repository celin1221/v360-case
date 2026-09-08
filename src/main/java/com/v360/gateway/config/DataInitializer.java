package com.v360.gateway.config;

import com.v360.gateway.domain.port.PurchaseOrderRepository;
import com.v360.gateway.ingestion.service.AlfaIngestionService;
import com.v360.gateway.ingestion.service.BetaIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "app.data-initializer.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final AlfaIngestionService alfaIngestionService;
    private final BetaIngestionService betaIngestionService;
    private final ResourceLoader resourceLoader;

    public DataInitializer(PurchaseOrderRepository purchaseOrderRepository,
                           AlfaIngestionService alfaIngestionService,
                           BetaIngestionService betaIngestionService,
                           ResourceLoader resourceLoader) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.alfaIngestionService = alfaIngestionService;
        this.betaIngestionService = betaIngestionService;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (purchaseOrderRepository.count() > 0) {
            log.info("Repositório de pedidos já contém dados ({} pedidos cadastrados). Carga inicial ignorada.", purchaseOrderRepository.count());
            return;
        }

        log.info("Iniciando carga automática de dados de exemplo (DataInitializer)...");

        try {
            // 1. Ingestão Cliente Alfa
            String alfaJson = readResource("classpath:sample-data/alfa/pedidos_alfa.json");
            if (alfaJson != null) {
                alfaIngestionService.ingest(alfaJson);
                log.info("Cliente Alfa: pedidos carregados com sucesso.");
            }

            // 2. Ingestão Cliente Beta
            String cabecalhoCsv = readResource("classpath:sample-data/beta/cabecalho.csv");
            String itensCsv = readResource("classpath:sample-data/beta/itens.csv");
            if (cabecalhoCsv != null && itensCsv != null) {
                betaIngestionService.ingest(cabecalhoCsv, itensCsv);
                log.info("Cliente Beta: pedidos carregados com sucesso.");
            }

            log.info("DataInitializer concluído com sucesso. Total de pedidos no banco: {}", purchaseOrderRepository.count());
        } catch (Exception e) {
            log.error("Falha ao executar carga inicial de dados: {}", e.getMessage(), e);
        }
    }

    private String readResource(String location) {
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                log.warn("Arquivo de dados não encontrado: {}", location);
                return null;
            }
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("Erro ao ler recurso {}: {}", location, e.getMessage());
            return null;
        }
    }
}
