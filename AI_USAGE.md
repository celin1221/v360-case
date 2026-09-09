# Relato de Uso de Inteligência Artificial (AI_USAGE.md)

Este documento descreve de forma transparente, estruturada e aprofundada como ferramentas de Inteligência Artificial Generativa e o framework de **Agent Skills (Matt Pocock Skills)** foram utilizados na concepção e implementação do **V360 Conector de Pedidos de Compra**.

O projeto foi conduzido sob uma filosofia estrita de **Human-in-the-Loop**: a IA operou como uma força de tração e pair programming avançado, enquanto o desenvolvedor humano atuou como **Tech Lead, Arquiteto de Software e Gatekeeper de Qualidade**, definindo premissas arquiteturais, selecionando padrões de mercado consagrados, guiando a modelagem do domínio e validando cada decisão técnica.

---

## 1. O Framework de Agent Skills (Matt Pocock Skills) e seu Impacto no Case

Um dos maiores diferenciais metodológicos deste projeto foi a não utilização da IA de forma solta ou improvisada (*"vibe coding"*). Em vez de prompts informais em chat, o desenvolvimento foi orquestrado utilizando a especificação de **Agent Skills criada por Matt Pocock** (definida no diretório `.agents/skills/` e documentada em `AGENTS.md`).

### As Skills Utilizadas no Ciclo de Vida

| Skill | Papel no Projeto | Como foi Aplicada |
| :--- | :--- | :--- |
| **`domain-modeling`** | Definição da Linguagem Ubíqua e ADRs | Estabeleceu o glossário rigoroso em `CONTEXT.md` (termos canônicos vs. termos proibidos) e estruturou as 5 Decisões de Arquitetura em `docs/adr/`. |
| **`implement`** | Execução Estruturada por Costuras (*Seams*) | Implementou os tickets verticais em pequenos passos lógicos, garantindo compilação e testes em cada etapa antes de avançar. |
| **`tdd`** | Desenvolvimento Orientado a Testes | Guiou a criação da suíte de testes unitários e de integração antes e durante a codificação das regras de negócio complexas. |
| **`code-review`** | Auditoria Dual-Axis em Subagentes Paralelos | Executou análises automatizadas divididas em dois eixos independentes: **Standards** (Code Smells do Martin Fowler) e **Spec** (aderência aos requisitos). |
| **`codebase-design`** | Design de Módulos Profundos (*Deep Modules*) | Orientou a criação de interfaces enxutas com implementação rica, maximizando o encapsulamento nos adaptadores, portas e regras de conciliação. |

### Por que o uso dessas Skills agrega valor decisivo à resolução do case?

1. **Eliminação do Viés de Contexto (*Context Pollution*):**
   No `/code-review` tradicional com IA, o mesmo modelo que codifica tende a avaliar o próprio código de forma complacente. Com a skill de `code-review`, o sistema dispara **dois subagentes autônomos em paralelo**:
   - Um agente avalia exclusivamente a conformidade com as especificações (*Spec Axis*), detectando requisitos parciais ou desvios de escopo;
   - Outro agente avalia exclusivamente o padrão de código (*Standards Axis*), confrontando o diff contra os 12 *Code Smells* clássicos do livro *Refactoring* de Martin Fowler (como *Primitive Obsession*, *Feature Envy*, *Duplicated Code* e *Data Clumps*) e as regras de `CONTEXT.md`.
   Isso garantiu auditorias neutras, implacáveis e com rastreabilidade formal.

2. **Rastreabilidade e Não-Proliferação de Código (*Anti-Hallucination*):**
   A arquitetura orientada a skills impede que a IA invente funcionalidades desnecessárias (*Speculative Generality*). Todo código gerado precisou obrigatoriamente responder a um ticket formal em `.scratch/purchase-order-gateway/issues/` e respeitar uma ADR correspondente.

3. **Reprodutibilidade Corporativa:**
   Qualquer membro da equipe ou novo agente que abra o repositório herda imediatamente as mesmas instruções operacionais e padrões de qualidade definidos no repositório, garantindo padrão de engenharia corporativo de ponta a ponta.

---

## 2. Matriz de Ferramentas e Responsabilidades

| Camada | Tecnologia / Modelo | Responsabilidade Técnica |
| :--- | :--- | :--- |
| **Ambiente de Agentes** | Google Antigravity | Orquestração de subagentes paralelos, execução de comandos e gestão de ferramentas de inspeção de código. |
| **Modelo de Linguagem (LLM)** | Gemini 3.8 Flash | Discussão de trade-offs arquiteturais, formalização de ADRs, geração e refatoração de código Java 21, cálculo de tolerâncias e regras de rateio. |
| **Framework de Governança** | Matt Pocock Agent Skills | Orquestração do ciclo de desenvolvimento, TDD, design de módulos profundos e modelagem de domínio (`CONTEXT.md`). |
| **Auditoria & Review** | Subagentes Paralelos (Dual Review) | Revisão estática automatizada em subagentes paralelos (*Standards Reviewer* e *Spec Reviewer*) com Gemini 3.8 Flash contra padrões e critérios de aceitação. |

---

## 3. As Grandes Decisões Formais de Arquitetura e Engenharia

A arquitetura e solidez do projeto derivam de intervenções arquiteturais diretas do desenvolvedor, adotando padrões consolidados da indústria:

### Decisão 1: Autenticação M2M com Padrão de Mercado OAuth 2.0 Client Credentials & JWT (ADR-0005)
* **Contexto e Deliberação:**
  Em integrações corporativas com ERPs (como SAP S/4HANA e TOTVS Protheus), a comunicação entre sistemas é estritamente Máquina-para-Máquina (M2M), sem intervenção humana no momento da chamada. A IA poderia facilmente optar por mecanismos frágeis como Basic Authentication ou chaves de API estáticas (*API Keys*) no header.
* **Diretriz do Desenvolvedor:**
  O desenvolvedor exigiu a adoção formal do padrão de mercado adotado globalmente em ecossistemas corporativos e SAP Cloud Integration: o **OAuth 2.0 Client Credentials Grant** com tokens **JSON Web Token (JWT)** assinados criptograficamente (HMAC-SHA256).
* **Estrutura Implementada:**
  - Endpoint `POST /api/v1/auth/token` validando `clientId` e `clientSecret`;
  - Emissão de JWT stateless com tempo de expiração (`exp`), identificador de inquilino (`tenantCode`) e papéis de segurança;
  - Segregação estrita no Spring Security entre:
    - **`ROLE_CLIENT`:** Permissão restrita ao próprio inquilino (`CLI-ALFA-001`, `CLI-BETA-002`, `CLI-GAMA-003`). Tentativas de consultar ou ingerir pedidos de outro tenant resultam em `403 FORBIDDEN`;
    - **`ROLE_PLATFORM`:** Perfil da operadora V360 para conciliação transversal de notas de fornecedores, consultas multi-tenant e emissão de relatórios analíticos globais.

---

### Decisão 2: Arquitetura Hexagonal (*Ports & Adapters*) com Modelo Canônico Puro (ADR-0001)
* **Contexto e Deliberação:**
  A plataforma precisa integrar compradores com contratos e tecnologias completamente díspares:
  - **Cliente Alfa (Energia):** JSON aninhado com estrutura SAP;
  - **Cliente Beta (Alimentos):** Arquivos CSV tabulares separados (cabeçalho e itens) com delimitador ponto e vírgula e datas em padrão brasileiro;
  - **Cliente Gama (Logística):** JSON plano (*flat*) com datas em timestamp Unix e valores inteiros em centavos.
* **Diretriz do Desenvolvedor:**
  Impedir terminantemente que detalhes de serialização dos clientes infectem o modelo de negócio ou o motor de conciliação.
* **Estrutura Implementada:**
  - O domínio central conhece apenas as entidades puras: `PurchaseOrder`, `PurchaseOrderItem`, `Vendor` e `OrderStatus`;
  - Cada cliente possui um adaptador isolado (`AlfaJsonAdapter`, `BetaCsvAdapter`, `GamaJsonAdapter`);
  - Inclusão de um novo cliente no futuro requer apenas escrever um novo adaptador, mantendo o núcleo e o motor de matching 100% inalterados e protegidos contra regressões.

---

### Decisão 3: Padrão GoF *Chain of Responsibility* no Motor Three-Way Matching (ADR-0002)
* **Contexto e Deliberação:**
  A validação fiscal de faturas contra pedidos de compra envolve múltiplos critérios de conformidade (existência do pedido, correspondência de CNPJ do fornecedor, presença dos materiais faturados, tolerância de preço unitário e limite de saldo pendente). Implementar isso de forma sequencial procedural em um único método de serviço geraria código acoplado, de difícil manutenção e propenso a efeitos colaterais.
* **Diretriz do Desenvolvedor:**
  O desenvolvedor orientou a refatoração do motor de matching para o padrão comportamental **Chain of Responsibility**, acompanhado de uma exigência rigorosa de Developer Experience (DX): a API não deveria apenas aprovar ou reprovar, mas retornar **feedback cirúrgico e granular por item**.
* **Estrutura Implementada:**
  - Interface atômica `ReconciliationRule` e orquestrador `ReconciliationRuleChain`;
  - Regras desacopladas e testadas isoladamente:
    1. `OrderExistenceRule` (Pedido existente e acessível pelo tenant)
    2. `VendorTaxIdMatchRule` (Conferência de CNPJ do fornecedor)
    3. `ItemExistenceRule` (Conferência de código do material)
    4. `UnitPriceToleranceRule` (Tolerância de preço unitário)
    5. `CumulativeQuantityRule` (Saldo disponível considerando rateios cumulativos)
  - Em caso de reprovação, a resposta devolve o objeto estruturado `ReconciliationDivergenceDto` com o número exato da linha da fatura (`lineNumber`), código do material (`materialCode`), valor esperado (`expectedValue`), valor faturado (`actualValue`) e a diferença matemática exata (`difference`).

---

### Decisão 4: Evolução do Schema com Preservação de Rastreabilidade Comercial (ADR-0003 & ADR-0004)
* **Contexto e Deliberação:**
  Ao introduzir o Cliente Gama (Parte 2), a empresa comprava em caixas (`CX`) com fator de conversão (ex: 10 caixas com fator 12 = 120 unidades). A proposta usual de IAs seria converter o valor em memória para `UN` e salvar apenas os dados normalizados.
* **Diretriz do Desenvolvedor:**
  O desenvolvedor identificou que descartar a unidade comercial original destruiria o valor probatório e legal do pedido em caso de litígio comercial ou conferência física no armazém.
* **Estrutura Implementada:**
  - Evolução do schema relacional da tabela `purchase_order_items` com a adição das colunas de auditoria: `original_uom`, `original_quantity` e `conversion_factor`;
  - O motor de matching opera com segurança na base normalizada (`UN`), enquanto o portal de compras e os relatórios preservam a integridade do que foi originalmente negociado pelo comprador;
  - Suporte a identificadores imutáveis de inquilino (`CLI-ALFA-001`) com resolução transparente de *slug aliases* (`alfa`, `beta`, `gama`) nos filtros da API.

---

### Decisão 5: Port de Repositório Hexagonal (DIP) e Facilidade para Troca de Matriz de Banco de Dados (ADR-0002)
* **Contexto e Deliberação:**
  Em arquiteturas convencionais que acoplam controllers e services diretamente ao Spring Data JPA (`JpaRepository`) ou anotações Hibernate, cria-se um aprisionamento tecnológico (*vendor lock-in*). Toda a camada de negócio fica amarrada a detalhes de infraestrutura SQL relacional. Mudar o banco de dados (de um relacional para um distribuído NoSQL, colunar de auditoria ou document-based) exigiria reescrever ou refatorar profundamente serviços, regras de negócio e suítes de teste.
* **Diretriz do Desenvolvedor:**
  O desenvolvedor estabeleceu a aplicação estrita do **Princípio da Inversão de Dependência (DIP)** e do conceito de **Porta Hexagonal**:
  1. O domínio define uma interface pura (`PurchaseOrderRepository` e `ReconciliationAuditRepository`) contendo exclusivamente contratos de negócio expressos em agregados e tipos canônicos (`PurchaseOrder`, `ReconciliationAudit`, `PurchaseOrderFilter`), sem anotações ou tipos do Spring Data;
  2. A persistência torna-se um **adaptador periférico descartável e substituível**: o núcleo da aplicação desconhece se os dados estão sendo salvos em PostgreSQL, H2, MongoDB, Cassandra, DynamoDB ou na memória RAM.
* **Impacto Prático e Benefícios Comprovados no Projeto:**
  - **Migração Transparente da Matriz de Banco de Dados:**
    A migração da persistência inicial em H2 em arquivo para **PostgreSQL 16 conteinerizado** foi realizada de forma cirúrgica: nenhum serviço de ingestão (`AlfaIngestionService`, `BetaIngestionService`, `GamaIngestionService`), serviço de consulta (`PurchaseOrderQueryService`) ou motor de conciliação (`InvoiceReconciliationService`) precisou ser alterado. Bastou ajustar o adaptador JPA e as configurações do datasource;
  - **Pronto para Futuras Trocas de Matriz:**
    Se no futuro a V360 optar por migrar os pedidos para um banco colunar ou distribuído em nuvem (ex: Google Cloud Spanner, CockroachDB, AWS Aurora), basta desenvolver um novo adaptador que implemente `PurchaseOrderRepository`, mantendo 100% das regras e integrações intactas;
  - **Dualidade Produção vs. Testes Ultrarrápidos:**
    Permitiu manter duas implementações ativas no projeto:
    - **`JpaPurchaseOrderRepositoryAdapter`:** Implementação oficial de produção para o **PostgreSQL 16**;
    - **`InMemoryPurchaseOrderRepositoryAdapter`:** Implementação puramente em memória (`ConcurrentHashMap`), habilitada no perfil `@Profile("in-memory")`, permitindo rodar testes de integração e cenários de conciliação complexos em milissegundos, sem I/O de disco ou dependência de banco de dados ativo.

---

### Decisões Técnicas Menores e Detalhes de Implementação Orientados pelo Desenvolvedor

Além das grandes decisões de arquitetura, o desenvolvedor guiou convenções técnicas de baixo nível essenciais para a confiabilidade de um software de conferência financeira:

1. **Tratamento de Valores Monetários com `BigDecimal` e Preços Inteiros em Centavos:**
   - **Por que não usar `float` ou `double`?** Tipos de ponto flutuante binário (padrão IEEE 754) sofrem de erros cumulativos de representação decimal (ex: `0.1 + 0.2 = 0.30000000000000004`). Em compras corporativas de milhares de itens, esses erros gerariam furos contábeis inaceitáveis.
   - **Como foi tratado:** Todas as grandezas monetárias e quantidades utilizam `java.math.BigDecimal` com controle explícito de escala e arredondamento formal (`RoundingMode.HALF_UP`). Preços recebidos em centavos como inteiros (Cliente Gama: `120000`) são divididos com precisão decimal exata por 100 (`R$ 1200.00`).

2. **Tolerância Monetária Absoluta de R$ 0,01:**
   - Na divisão de valores unitários a partir de caixas (ex: R$ 100,00 por uma caixa com 3 unidades = R$ 33,3333...), surgem dízimas infinitas. Uma tolerância monetária absoluta de até **R$ 0,01 por unidade** (`abs(expected - actual) <= 0.01`) foi adotada para evitar rejeições injustas de faturas por frações de centavo, sem abrir as brechas de fraude que uma tolerância percentual abriria em itens de alto valor unitário.

3. **Higienização Canônica de Documentos (CNPJs desmascarados com 14 dígitos):**
   - Fornecedores são identificados por CNPJ em formatos arbitrários pelos clientes (com máscara `12.345.678/0001-90` ou apenas números `12345678000190`). No construtor do Value Object `Vendor`, qualquer caractere não numérico é descartado via regex (`\D`), garantindo que o Three-Way Matching e as consultas por fornecedor operem sempre de forma determinística e padronizada.

4. **Tratamento de Datas Contábeis com `LocalDate` (Zero Bug de Timezone):**
   - Datas de criação de pedidos e emissão de notas fiscais representam dias fiscais/contábeis fechados, e não instantes de relógio em alta precisão. O uso de `java.time.LocalDate` (formato ISO-8601 `YYYY-MM-DD`) elimina riscos de deslocamento de data causados por fusos horários (por exemplo, meia-noite em Brasília UTC-3 virando 21h do dia anterior em UTC). Timestamps Unix em segundos (Cliente Gama) são convertidos para o calendário local do negócio.

5. **Imutabilidade e Segurança com Java 21 `record`:**
   - DTOs de transporte, Value Objects (`Vendor`) e filtros de consulta foram modelados utilizando `record` do Java 21. Isso assegura imutabilidade inerente (sem setters arbitrários), elimina código boilerplate e garante implementações seguras e consistentes de `equals`, `hashCode` e `toString`.

6. **Idempotência no Processamento de Pedidos (Upsert):**
   - Em caso de falha de conexão ou reenvio automático por parte do ERP cliente, a API não duplica pedidos nem itens. O repositório realiza um *upsert* idempotente baseado no par único (`clientId` + `poNumber`), atualizando o pedido existente e mantendo a integridade histórica.

7. **Tratamento Centralizado de Erros e Exceções (`GlobalExceptionHandler`):**
   - Nenhuma exceção interna ou stacktrace de banco vaza para o consumidor da API. Um manipulador centralizado `@RestControllerAdvice` intercepta falhas de validação (`MethodArgumentNotValidException`) e erros de negócio (`ApiException`), devolvendo respostas HTTP padronizadas com payload estruturado (`timestamp`, `status`, `code`, `message`).

---

## 4. Catálogo de Prompts Eficazes (Engenharia de Prompt Formal)

Os prompts que extraíram os melhores resultados da IA destacaram-se pelo rigor conceitual, referenciando padrões da indústria e contratos de dados explícitos:

### Exemplo 1: Definição da Arquitetura de Segurança OAuth 2.0 M2M
```text
Preciso estruturar a segurança da integração entre os ERPs clientes e o Gateway V360. 
Não quero basic auth nem token estático. Vamos adotar o padrão de mercado para integrações M2M: 
OAuth 2.0 Client Credentials Grant com JWT stateless. 
Defina o endpoint POST /api/v1/auth/token gerando token assinado HMAC-SHA256 com claims 
de tenantCode e roles segregadas (ROLE_PLATFORM para a V360 e ROLE_CLIENT para clientes Alfa, Beta, Gama). 
Configure o filtro de segurança Spring Security garantindo que um cliente comum não consiga 
consultar nem reconciliar pedidos de outros tenants (isolamento multi-tenant estrito).
```
* **Por que funcionou:**
  Especificou o grant type exato do RFC 6749, o algoritmo criptográfico de assinatura, os claims requeridos e a regra de controle de acesso baseada em papéis (RBAC) com isolamento multi-inquilino.

### Exemplo 2: Desacoplamento da Persistência com Interface Repository (DIP / Hexagonal)
```text
Vamos estruturar a persistência aplicando a Arquitetura Hexagonal (DIP). 
Não quero que a camada de domínio nem os services de negócio conheçam Spring Data JPA ou anotações do Hibernate. 
Crie uma interface PurchaseOrderRepository no pacote domain.port como uma porta pura do domínio. 
Em seguida, crie o adapter JpaPurchaseOrderRepositoryAdapter na camada de infraestrutura. 
Isso vai nos permitir mudar a matriz do banco de dados facilmente no futuro (como migrar de H2 para PostgreSQL 16 
ou até para um banco NoSQL) sem encostar em uma linha sequer de regra de negócio, além de viabilizar um 
InMemoryPurchaseOrderRepository para testes rápidos.
```
* **Por que funcionou:**
  Definiu a separação de responsabilidades pelo DIP, estabeleceu o contrato de porta e adaptador e fundamentou a flexibilidade futura para alternância da matriz de banco de dados.

### Exemplo 3: Refatoração do Three-Way Matching para Chain of Responsibility
```text
Para fazer as verificações do reconciliation, ao invés de chamar um por um proceduralmente na service, 
vamos aplicar o padrão comportamental Chain of Responsibility. 
Crie uma interface ReconciliationRule e uma cadeia ReconciliationRuleChain. 
Além disso, quero capturar exatamente qual item foi divergente para entregar um feedback cirúrgico 
para o integrador, contendo: lineNumber, materialCode, expectedValue, actualValue e difference.
```
* **Por que funcionou:**
  Nomeou o padrão GoF apropriado para o problema de extensibilidade e definiu a estrutura exata do contrato de dados da divergência, eliminando ambiguidades na resposta JSON da API.

---

## 5. Momentos Críticos de Correção da IA pelo Desenvolvedor

A intervenção humana foi fundamental para impedir falhas de integridade que a IA propôs por simplificação:

1. **Correção no Rateio de Saldo Cumulativo (*Split-Lines*):**
   * *Proposta da IA:* A IA validava apenas se a quantidade de cada linha da nota era menor que o saldo pendente do pedido (`invoiceItem.qty <= orderItem.pendingQty`).
   * *Falha Identificada pelo Dev:* Se uma fatura contiver duas linhas para o mesmo material (ex: 30 un e 20 un contra um saldo de 40 un), a validação da IA aprovaria ambas as linhas isoladas, estourando o saldo do pedido em 10 unidades.
   * *Correção do Dev:* Exigiu a criação de um acumulador de consumo de saldo por material em memória durante a execução da cadeia, bloqueando o estouro cumulativo da fatura.

2. **Correção na Regra de Tolerância de Preço Unitário:**
   * *Proposta da IA:* A IA propôs tolerância percentual de $\pm 1\%$.
   * *Falha Identificada pelo Dev:* Em compras de insumos pesados ou transformadores de R$ 50.000,00, 1% representaria R$ 500,00 de margem de erro por unidade.
   * *Correção do Dev:* Impôs tolerância monetária estrita de até **R$ 0,01 por unidade**, utilizando `BigDecimal` com arredondamento formal (`RoundingMode.HALF_UP`).

3. **Correção no Endpoint de Health Check e Docker Compose:**
   * *Proposta da IA:* A IA colocou o healthcheck do Docker Compose apontando para `/api/v1/health` sem autenticação.
   * *Falha Identificada:* O endpoint exigia token JWT, retornando `401 Unauthorized` para o comando `curl` do Docker e marcando o container como permanentemente `unhealthy`.
   * *Correção do Dev:* Ajustou o healthcheck do compose para validar a prontidão do gateway inspecionando `/v3/api-docs` (documentação OpenAPI pública), garantindo status saudável sem comprometer os testes de segurança.

---

## 6. Garantia de Domínio Técnico e Qualidade de Entrega

O resultado final atesta o domínio completo sobre cada linha de código entregue:

1. **106 Testes Automatizados com 100% de Sucesso:**
   - Cobertura completa incluindo unitários, adapters com dados malformados, regras isoladas de matching, conciliação ponta a ponta e testes de autorização multi-tenant via `MockMvc`;
   - Execução integral: `Tests run: 106, Failures: 0, Errors: 0, Skipped: 0`.

2. **Conformidade Contínua com Code Reviews Automatizados:**
   - Todas as entregas passaram por ciclos de `/code-review` com subagentes especializados, saneando code smells, corrigindo potenciais vulnerabilidades de concorrência e mantendo fidelidade estrita às especificações.

3. **Pronto para Produção e Avaliação com 1 Comando:**
   - Entrega conteinerizada com `docker compose up --build`, orquestrando **PostgreSQL 16** e a aplicação Spring Boot com *healthchecks* encadeados, suite de testes em `requests.http` e documentação visual interativa no Swagger UI.
