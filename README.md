# V360 - Conector de Pedidos de Compra (Purchase Order Integration Gateway)

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Build & Tests](https://img.shields.io/badge/Tests-105%20Passing-success.svg)]()
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)

API REST corporativa desenvolvida para a plataforma **V360**, responsável por centralizar, normalizar e auditar pedidos de compra (*Purchase Orders*) oriundos de múltiplos sistemas ERP heterogêneos, executando a conferência automatizada de faturas de fornecedores (*Three-Way Matching*) e gerando inteligência de auditoria em suprimentos.

> 🧭 **Navegação Rápida & Destaques de Avaliação:**
> - 📖 **[Relato de Uso de IA (AI_USAGE.md)](file:///D:/Git/v360-case/AI_USAGE.md):** Metodologia *Human-in-the-Loop*, os 5 grandes insights do desenvolvedor, catálogo de prompts e correções técnicas aplicadas.
> - 📑 **[Decisões Arquiteturais Registradas (docs/adr/)](file:///D:/Git/v360-case/docs/adr/):** ADR-0001 (Modelo Canônico), ADR-0002 (Chain of Responsibility), ADR-0003 (Preservação de Embalagem Comercial), ADR-0004 (Tenant Code) e ADR-0005 (OAuth2 M2M JWT).
> - 🧪 **[Suíte de Testes Automatizados](#6-como-testar-coleções-e-suíte-de-testes):** 105 testes passando sem falhas (`105 run, 0 failures, 0 errors`).
> - 📮 **[Coleção Postman (v360-collection.json)](file:///D:/Git/v360-case/v360-collection.json)** e **[requests.http](file:///D:/Git/v360-case/requests.http):** 40 cenários prontos com tokens automáticos no topo.

---

## 1. O Problema de Negócio

No ecossistema corporativo de **Procure-to-Pay (P2P)**, empresas compradoras geram ordens de compra em seus próprios sistemas de gestão (ERPs), enquanto fornecedores faturam entregas emitindo Notas Fiscais eletrônicas.

Tradicionalmente, a conciliação entre o que foi comprado e o que foi faturado enfrenta três grandes gargalos:
1. **Heterogeneidade Extrema de Formatos:** Cada cliente exporta dados no padrão do seu ERP (JSONs aninhados da SAP, CSVs com ponto e vírgula e formatação brasileira do TOTVS Protheus, JSONs achatados de legados com timestamps Unix).
2. **Divergências de Unidade de Compra vs. Faturamento:** Compradores frequentemente adquirem mercadorias em embalagens comerciais fechadas (ex: caixas com 12 unidades), enquanto a Nota Fiscal do fornecedor é faturada estritamente na unidade base física (ex: unidades soltas a preço unitário).
3. **Erros e Fraudes no Recebimento Fiscal:** Notas fiscais com preços superfaturados, quantidades além do saldo em aberto ou cobrança de itens não previstos passam despercebidas quando a conferência é manual.

### O Papel do Gateway V360
A V360 atua como o **barramento inteligente de integração e conciliação**:
- Ingestão multiformato idempotente através de adaptadores isolados (*Ports & Adapters*);
- Normalização transparente para um **Modelo Canônico** em unidade base (`UN`), preservando metadados de embalagem comercial original para auditoria;
- Motor de **Three-Way Matching** baseado no padrão **Chain of Responsibility**, com feedback granular item a item, saldo acumulado e tolerância de arredondamento de até R$ 0,01;
- Isolamento estrito de dados entre clientes concorrentes (*Multi-Tenancy*), com visão analítica consolidada para a operadora da plataforma.

---

## 2. Arquitetura e Decisões de Design

A aplicação adota os princípios da **Arquitetura Hexagonal (Ports & Adapters)**, **Domain-Driven Design (DDD)** e **12-Factor App**.

> 💡 *Para uma visualização isolada dos diagramas, acesse o documento dedicado: [`docs/diagrams/architecture-diagrams.md`](file:///D:/Git/v360-case/docs/diagrams/architecture-diagrams.md).*

### Estrutura do Sistema (Classes e Módulos Agrupados)

```mermaid
graph TB
    subgraph SEC["🔐 Segurança & Multi-Tenancy (OAuth2 / JWT)"]
        JwtFilter["JwtAuthFilter<br/><i>Validação do Bearer Token</i>"]
        JwtSvc["JwtService<br/><i>Claims: tenantCode, role</i>"]
        SecConfig["SecurityConfig<br/><i>Regras de Endpoint & RBAC</i>"]
        SecConfig --> JwtFilter
        JwtFilter --> JwtSvc
    end

    subgraph ING["📥 Camada de Ingestão (Ports & Adapters)"]
        AlfaCtrl["AlfaIngestionController<br/><i>POST /api/v1/ingestion/alfa</i>"]
        AlfaAdp["AlfaJsonAdapter<br/><i>Parse JSON Aninhado (SAP)</i>"]
        
        BetaCtrl["BetaIngestionController<br/><i>POST /api/v1/ingestion/beta</i>"]
        BetaAdp["BetaCsvAdapter<br/><i>Parse CSV RFC 4180 (TOTVS)</i>"]
        
        GamaCtrl["GamaIngestionController<br/><i>POST /api/v1/ingestion/gama</i>"]
        GamaAdp["GamaJsonAdapter<br/><i>Parse Flat JSON + CX->UN</i>"]

        AlfaCtrl --> AlfaAdp
        BetaCtrl --> BetaAdp
        GamaCtrl --> GamaAdp
    end

    subgraph DOM["🏛️ Domínio Canônico (Business Core)"]
        PO["PurchaseOrder<br/><b>Aggregate Root</b><br/>orderNumber, tenantCode, status"]
        POI["PurchaseOrderItem<br/><b>Entity</b><br/>itemNumber, materialCode, unitPrice<br/><i>originalUom, originalQty, convFactor</i>"]
        Vendor["Vendor<br/><b>Value Object</b><br/>taxId, name"]
        PO --> POI
        PO --> Vendor
    end

    subgraph REC["⚙️ Motor Three-Way Matching (Chain of Responsibility)"]
        RecCtrl["ReconciliationController<br/><i>POST /api/v1/reconciliation/match</i>"]
        RecSvc["InvoiceReconciliationService<br/><i>Orquestração do Matching</i>"]
        Chain["ReconciliationRuleChain<br/><i>Execução da Cadeia</i>"]
        
        R1["1. OrderExistenceRule<br/><i>Pedido existe no tenant?</i>"]
        R2["2. VendorTaxIdMatchRule<br/><i>CNPJ bate com fornecedor?</i>"]
        R3["3. ItemExistenceRule<br/><i>Material existe no pedido?</i>"]
        R4["4. UnitPriceToleranceRule<br/><i>Tolerância de até R$ 0,01?</i>"]
        R5["5. CumulativeQuantityRule<br/><i>Saldo cumulativo / split-lines</i>"]
        
        Diverg["ReconciliationDivergenceDto<br/><i>Feedback granular cirúrgico</i>"]

        RecCtrl --> RecSvc
        RecSvc --> Chain
        Chain --> R1
        Chain --> R2
        Chain --> R3
        Chain --> R4
        Chain --> R5
        Chain -.-> Diverg
    end

    subgraph QRY["📊 Consultas & Inteligência Analítica"]
        QueryCtrl["PurchaseOrderController<br/><i>Filtros, Paginação & Saldo</i>"]
        AuditCtrl["ReconciliationAuditController<br/><i>Relatórios de Auditoria</i>"]
    end

    subgraph INFRA["💾 Persistência & Portas de Dados"]
        PORepo["PurchaseOrderRepository<br/><i>Interface / Port</i>"]
        AuditRepo["ReconciliationAuditRepository<br/><i>Interface / Port</i>"]
        PGDB[("PostgreSQL 16 / JPA<br/><i>Produção & Docker (H2 em testes)</i>")]
        
        PORepo --> PGDB
        AuditRepo --> PGDB
    end

    %% Conexões entre camadas
    SEC -.->|Isolamento por tenantCode| ING
    SEC -.->|Isolamento por tenantCode| REC
    SEC -.->|Isolamento por tenantCode| QRY

    AlfaAdp -->|Normaliza para| PO
    BetaAdp -->|Normaliza para| PO
    GamaAdp -->|Normaliza para| PO

    ING -->|Salva / Atualiza| PORepo
    RecSvc -->|Consulta Pedido| PORepo
    RecSvc -->|Grava Auditoria| AuditRepo
    QueryCtrl -->|Consulta Pedidos| PORepo
    AuditCtrl -->|Gera Métricas| AuditRepo
```

### Caminho da Requisição (Fluxo Passo a Passo no Sistema)

```mermaid
sequenceDiagram
    autonumber
    actor Client as "Cliente ERP / Fornecedor"
    participant Filter as "JwtAuthFilter"
    participant Ctrl as "Controller (API)"
    participant Adapter as "Ingestion Adapter"
    participant Matcher as "Reconciliation Service"
    participant Chain as "Reconciliation Rule Chain"
    participant Repo as "Database (JPA)"
    participant Audit as "Audit Repository"

    %% FLUXO 1: INGESTÃO DE PEDIDOS
    Note over Client, Repo: FLUXO 1: Ingestao Multi-Tenant de Pedidos (Alfa, Beta ou Gama)
    Client->>Filter: POST /api/v1/ingestion/:cliente com Bearer Token
    Filter->>Filter: Valida assinatura HMAC-SHA256 e extrai tenantCode e Role
    Filter->>Ctrl: Encaminha requisicao autenticada
    Ctrl->>Ctrl: Valida se tenantCode tem permissao
    Ctrl->>Adapter: Envia payload bruto (JSON ou CSV)
    Adapter->>Adapter: Converte formato, datas, centavos e caixas para unidades (CX para UN)
    Adapter->>Repo: Verifica duplicidade e salva PurchaseOrder
    Repo-->>Ctrl: Pedido gravado com sucesso
    Ctrl-->>Client: HTTP 200 OK (processedOrders: 1, items: 3)

    %% FLUXO 2: THREE-WAY MATCHING
    Note over Client, Audit: FLUXO 2: Three-Way Matching (Conferencia de Nota Fiscal)
    Client->>Filter: POST /api/v1/reconciliation/match com Fatura do Fornecedor
    Filter->>Filter: Valida tenant (ROLE_CLIENT no seu tenant, ROLE_PLATFORM transversal)
    Filter->>Ctrl: Requisicao autorizada
    Ctrl->>Matcher: match(tenantCode, SupplierInvoiceDto)
    Matcher->>Repo: Busca pedido canónico pelo numero
    Repo-->>Matcher: Retorna PurchaseOrder e itens normalizados em UN
    
    Matcher->>Chain: execute(ReconciliationContext)
    Note over Chain: Avaliacao sequencial dos 5 elos da cadeia
    Chain->>Chain: 1. OrderExistenceRule: Pedido existe no tenant?
    Chain->>Chain: 2. VendorTaxIdMatchRule: CNPJ do emitente confere?
    Chain->>Chain: 3. ItemExistenceRule: Materiais constam no pedido?
    Chain->>Chain: 4. UnitPriceToleranceRule: Preco unitario (tolerancia ate R$ 0.01)?
    Chain->>Chain: 5. CumulativeQuantityRule: Saldo cumulativo de split-lines?

    alt Fatura 100% Conforme
        Chain-->>Matcher: Aprovado (0 divergencias)
        Matcher->>Audit: Salva log de auditoria (status APPROVED)
        Matcher-->>Ctrl: Retorna APPROVED
        Ctrl-->>Client: HTTP 200 OK - Status: APPROVED
    else Fatura com Divergencias
        Chain-->>Matcher: Rejeitado (acumula divergencias detalhadas)
        Matcher->>Audit: Salva log de auditoria (status REJECTED com divergencias)
        Matcher-->>Ctrl: Retorna REJECTED com lista de divergencias
        Ctrl-->>Client: HTTP 200 OK - Status: REJECTED com Feedback Granular
    end
```

### Principais Padrões Utilizados
* **Ports & Adapters (Hexagonal):** O núcleo de domínio (`com.v360.gateway.domain`) não possui dependências de infraestrutura, bancos ou frameworks web. Portas de repositório (`PurchaseOrderRepository`, `ReconciliationAuditRepository`) permitem trocar a camada de dados (H2, PostgreSQL, In-Memory) sem tocar nas regras de negócio.
* **Modelo Canônico (ADR-0001):** Todos os dados externos são transformados em uma única representação interna (`PurchaseOrder`). O motor de conciliação e a API de consulta operam exclusivamente sobre o modelo canônico.
* **Chain of Responsibility (ADR-0002):** A validação de conferência é desacoplada em elos independentes (`ReconciliationRuleChain`). Ao invés de verificações procedurais acopladas em uma service monolítica, cada regra inspeciona o `ReconciliationContext` e acumula divergências de forma isolada e testável.
* **Feedback Granular de Divergências:** Ao rejeitar uma nota fiscal, a API não retorna apenas um status binário; ela identifica com precisão cirúrgica a linha da fatura, código do material, valor esperado, valor faturado e a diferença matemática calculada:
  ```json
  {
    "status": "REJECTED",
    "divergences": [
      {
        "code": "PRICE_MISMATCH",
        "lineNumber": 1,
        "materialCode": "MAT-1001",
        "description": "Linha 1 (MAT-1001): Preço unitário faturado (R$ 52.00) excede o acordado (R$ 45.90) além da tolerância permitida de R$ 0.01",
        "expectedValue": "45.9000",
        "actualValue": "52.00",
        "difference": "+6.1000"
      }
    ]
  }
  ```
* **Isolamento de Tenant vs. Superusuário da Plataforma (ADR-0004 & ADR-0005):**
  - `ROLE_CLIENT` (`CLI-ALFA-001`, `CLI-BETA-002`, `CLI-GAMA-003`): Isolamento estrito de dados. Clientes parceiros são impedidos pelo Spring Security de consultar ou conciliar pedidos de outros tenants (`403 FORBIDDEN`).
  - `ROLE_PLATFORM` (`v360-platform`): Perfil de serviço central da V360. Atua com visão transversal necessária para a operação SaaS (conciliação central de NFs de fornecedores externos contra pedidos de clientes e relatórios analíticos globais).

---

## 3. O que mudou da Parte 1 para a Parte 2

> **Seção Obrigatória do Case:** Análise técnica e arquitetural sobre os impactos da inserção do terceiro cliente parceiro (**Cliente Gama Logística**).

Na transição da Parte 1 para a Parte 2, surgiu uma questão central de modelagem:
> *"Uma das mudanças com a inserção da terceira empresa não seria adicionar colunas no banco de dados, já que antes não estava previsto ter fator de conversão e outros campos?"*

### A Resposta Arquitetural e os Trade-offs Analisados:

Ao analisar o impacto da terceira empresa, existiam duas abordagens possíveis:

1. **Abordagem Simplista (Descartar a embalagem de origem):**
   - O adapter converte caixas para unidades na memória e grava apenas `UN` nas colunas existentes.
   - *Problema:* Perda irremediável de rastreabilidade comercial. Se o comprador da Gama abrir o sistema, verá "120 unidades", sem saber que o pedido de compra oficial enviado ao fornecedor foi de "10 caixas". Se houver uma disputa judicial ou comercial sobre avaria de caixas, o sistema perde o valor probatório.

2. **Abordagem Adotada - Normalização com Preservação Comercial (ADR-0003):**
   - **Sim, o schema do banco de dados foi expandido** para acomodar a complexidade do novo parceiro, adicionando 3 novas colunas na tabela `purchase_order_items`:
     - `original_uom` (VARCHAR 20): Unidade de compra da embalagem de origem (ex: `CX`);
     - `original_quantity` (DECIMAL 15,4): Quantidade comprada na embalagem de origem (ex: `10.0000`);
     - `conversion_factor` (DECIMAL 10,4): Fator multiplicador para a unidade base (ex: `12.0000`).
   - **Impacto em Produção (Zero Quebra):** Como os campos de embalagem original são anuláveis (`nullable`), pedidos dos clientes existentes (Alfa e Beta) continuam funcionando perfeitamente sem qualquer quebra de compatibilidade retroativa. Em um banco corporativo (PostgreSQL/Oracle), a alteração ocorreria via script Flyway:
     ```sql
     ALTER TABLE purchase_order_items ADD COLUMN original_uom VARCHAR(20);
     ALTER TABLE purchase_order_items ADD COLUMN original_quantity NUMERIC(15,4);
     ALTER TABLE purchase_order_items ADD COLUMN conversion_factor NUMERIC(10,4);
     ```

### Por que o Core da Aplicação Permaneceu Intacto?
Mesmo com a evolução do schema para rastreabilidade, **o motor de Three-Way Matching não precisou de nenhuma alteração**:
- As fórmulas de conversão executadas no `GamaJsonAdapter`:
  $$\text{quantityOrdered} = \text{qtd\_ped} \times \text{fator\_conv}$$
  $$\text{quantityReceived} = \text{qtd\_rec} \times \text{fator\_conv}$$
  $$\text{unitPrice} = \frac{\text{preco\_unit\_centavos} / 100}{\text{fator\_conv}}$$
- O motor de conciliação continua operando estritamente sobre as grandezas canônicas em unidade base (`UN`). Dessa forma, quando a Nota Fiscal do fornecedor chega em unidades soltas a R$ 100,00, a conferência aprova com exatidão matemática sem precisar entender regras de empacotamento do cliente.

---

## 4. Instruções de Execução

### Pré-requisitos
- **Docker** e **Docker Compose** (recomendado para execução em contêiner com PostgreSQL 16);
- Ou **Java 21 LTS** e **Maven 3.9+** (para execução local via `./mvnw`).

### Opção A: Execução Conteinerizada via Docker Compose (1 comando - Recomendada)
```bash
docker compose up --build
```
Sobe a topologia completa de produção conteinerizada:
1. **`v360-postgres` (PostgreSQL 16 Alpine):** Inicializa o banco de dados `v360_db`, cria o volume persistente `postgres_data` e executa healthcheck contínuo via `pg_isready`;
2. **`v360-order-gateway` (Spring Boot 3 / Java 21):** Aguarda o PostgreSQL estar 100% pronto (`service_healthy`), sobe a API em imagem multi-stage com usuário não-root, executa o `DataInitializer` e expõe a porta `8080`.

### Opção B: Execução Local com PostgreSQL
Se você tiver uma instância do PostgreSQL rodando localmente na porta 5432:
```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

### Opção C: Execução Local com H2 em Arquivo (Fallback sem Docker)
Para rodar localmente sem precisar de PostgreSQL instalado:
```bash
# Windows
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=h2

# Linux / macOS
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
```

---

## 5. Endpoints, Documentação e Interfaces

| Recurso | URL / Parâmetros | Descrição |
| :--- | :--- | :--- |
| **Swagger UI** | `http://localhost:8080/swagger-ui.html` | Interface gráfica interativa OpenAPI 3 com autenticação Bearer |
| **OpenAPI JSON**| `http://localhost:8080/v3/api-docs` | Especificação completa da API no formato OpenAPI |
| **PostgreSQL (Docker)** | `localhost:5432` | Banco `v360_db`, usuário `v360_user`, senha `v360_pass` (conectável via DBeaver/psql) |
| **H2 Console (Profile H2)** | `http://localhost:8080/h2-console` | Console H2 habilitado exclusivamente ao utilizar `-Dspring-boot.run.profiles=h2` |
| **Health Check** | `http://localhost:8080/api/v1/health` | Verificação de status e identidade do token |

---

## 6. Como Testar (Coleções e Suíte de Testes)

### 1. Suíte de Testes Automatizados (105 Testes)
```bash
.\mvnw.cmd test     # Windows
./mvnw test         # Linux/macOS
```
Cobre testes unitários, testes de adapters (incluindo tratamento de erros de formato e ausência de campos obrigatórios), regras de matching e testes de integração de controllers com MockMvc:
```text
Results:
Tests run: 105, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 2. Arquivo `requests.http` (40 Chamadas Prontas)
O arquivo [`requests.http`](file:///D:/Git/v360-case/requests.http) na raiz do projeto está estruturado para execução imediata no VS Code (*REST Client*) ou IntelliJ:
1. **Autenticação no Topo (1 a 4):** Obtenção com 1 clique dos tokens `@tokenAlfa`, `@tokenBeta`, `@tokenGama` e `@tokenPlatform`;
2. **Ingestão Multi-Tenant (5 a 12):** Ingestão de pedidos para Alfa e Beta, upserts idempotentes e validações de segurança 403;
3. **Consultas Canônicas (13 a 19):** Listagem com paginação, filtro de saldo pendente (`onlyPendingBalance=true`), busca por CNPJ de fornecedor e isolamento de tenant;
4. **Three-Way Matching (20 a 29):** Casos 100% conformes, tolerância de até R$ 0,01, divergência de preço (`PRICE_MISMATCH`), quantidade excedente (`QUANTITY_EXCEEDS_PENDING_BALANCE`), e bloqueio cross-tenant;
5. **Relatórios Analíticos (30 a 34):** Relatório global da plataforma, filtrado por cliente e isolado por tenant;
6. **Parte 2 - Cliente Gama (35 a 40):** Ingestão flat, consulta de pedido com metadados originais de embalagem (`GL-778`), aprovação de nota fiscal em `UN` contra pedido em `CX`, e rejeição por cobrança indevida no valor da caixa.

### 3. Coleção Postman (`v360-collection.json`)
Importe o arquivo [`v360-collection.json`](file:///D:/Git/v360-case/v360-collection.json) no Postman. A coleção já inclui scripts automáticos nos testes de autenticação que salvam os tokens nas variáveis de coleção (`tokenAlfa`, `tokenBeta`, `tokenGama`, `tokenPlatform`).

---

## 7. Credenciais Padrão (M2M OAuth2)

| Cliente | Client ID | Client Secret | Tenant Code | Papel |
| :--- | :--- | :--- | :--- | :--- |
| **Plataforma V360** | `v360-platform` | `platform-secret-123` | `PLATFORM` | `ROLE_PLATFORM` (Superusuário Global) |
| **Cliente Alfa Energia** | `alfa-client` | `alfa-secret-123` | `CLI-ALFA-001` | `ROLE_CLIENT` |
| **Cliente Beta Alimentos** | `beta-client` | `beta-secret-123` | `CLI-BETA-002` | `ROLE_CLIENT` |
| **Cliente Gama Logística** | `gama-client` | `gama-secret-123` | `CLI-GAMA-003` | `ROLE_CLIENT` |

---

## 8. O que faria diferente com mais tempo (Roadmap para Produção)

1. **Ingestão Assíncrona e Streaming de Eventos (Kafka / RabbitMQ):**
   - Para arquivos com dezenas de milhares de linhas, transformar a ingestão em assíncrona (`202 Accepted` com `JobId` e processamento via tópicos Kafka particionados por tenant).
2. **Banco Relacional de Produção com Versionamento de Schema:**
   - Migração do banco H2 em memória para **PostgreSQL** em cluster, gerenciado com **Flyway** para migrações de schema versionadas.
3. **Observabilidade e Tracing Distribuído (OpenTelemetry / Prometheus):**
   - Integração com **Micrometer** e **Jaeger** para rastreamento distribuído de ponta a ponta com `traceId` e `spanId`.
4. **RBAC Granular para Operadores da Plataforma:**
   - Decomposição de `ROLE_PLATFORM` em privilégios específicos (`PLATFORM_SUPPORT`, `PLATFORM_AUDITOR`, `FINANCIAL_OPERATOR`) para auditorias SOX e ISO 27001.
5. **Leitura Direta de XML de NF-e (SEFAZ):**
   - Adaptador nativo para o schema XML da Nota Fiscal Eletrônica (NF-e modelo 55) do SEFAZ.
