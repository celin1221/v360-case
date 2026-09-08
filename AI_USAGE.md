# Relato de Uso de Inteligência Artificial (AI_USAGE.md)

Este documento descreve de forma transparente, estruturada e detalhada como ferramentas de Inteligência Artificial Generativa foram utilizadas no desenvolvimento do **V360 Conector de Pedidos de Compra**. 

O projeto foi conduzido sob uma filosofia rigorosa de **Human-in-the-Loop**: a IA atuou como uma ferramenta aceleradora de codificação e pair programming, enquanto o desenvolvedor humano exerceu o papel de **Tech Lead, Arquiteto de Software e Gatekeeper de Qualidade**, guiando decisões de design, identificando falhas de modelagem e validando cada linha entregue.

---

## 1. Ferramentas Utilizadas e Matriz de Aplicação

O ecossistema utilizado foi o **Google Antigravity**, operando com os modelos de linguagem **Gemini 2.5 Pro** e **Gemini 2.5 Flash** integrados a subagentes autônomos e ferramentas de inspeção estática:

| Etapa do Projeto | Ferramenta / Modelo | Forma de Uso e Responsabilidade |
| :--- | :--- | :--- |
| **Arquitetura & Design (ADRs)** | Gemini 2.5 Pro | Discussão de trade-offs arquiteturais, elaboração de ADRs (0001 a 0005), definição do Modelo Canônico e padrão *Ports & Adapters*. |
| **Implementação de Código (TDD)** | Gemini 2.5 Flash / Pro | Geração de scaffolds de classes, adaptadores de parsing (Alfa JSON, Beta CSV RFC 4180, Gama Flat JSON) e motor de regras *Chain of Responsibility*. |
| **Geração de Testes & Casos de Borda** | Gemini 2.5 Pro | Criação de suítes de teste de integração (MockMvc), testes unitários e casos de borda (tolerância monetária de R$ 0,01, rateio de split-lines cumulativo, ataques cross-tenant). |
| **Revisão de Código (Dual Code Review)** | Subagentes Paralelos | Execução de agentes de revisão: *Standards Reviewer* (caça a Code Smells de Martin Fowler) e *Spec Reviewer* (verificação estrita contra os requisitos das User Stories). |
| **Documentação & Entregáveis** | Gemini 2.5 Flash | Estruturação do `README.md`, geração da coleção Postman v2.1, criação de `requests.http` e otimização do `Dockerfile` multi-stage. |

---

## 2. Os 5 Grandes Insights e Decisões Técnicas do Desenvolvedor

A qualidade do projeto resultou diretamente de intervenções e direcionamentos estratégicos do desenvolvedor humano, que frequentemente superaram as propostas automáticas da IA:

### Insight 1: Previsão de Impacto e Evolução de Schema no Banco de Dados (Parte 2 - Cliente Gama)
* **A Pergunta do Desenvolvedor:**
  > *"Analisando o PDF do case, existe a pergunta de que mudanças ocorreriam com a inserção da terceira empresa. Uma das mudanças não seria adicionar colunas no banco de dados, já que antes não estava previsto ter fator de conversão e outros campos?"*
* **Contexto e Mérito Técnico:**
  Ao deparar-se com o Cliente Gama (que compra em caixas `CX` com fator de conversão 12 e preços em centavos), a abordagem mais simples e comumente sugerida por IAs seria normalizar o dado em memória para unidade (`UN`) e descartar os campos originais. 
  O desenvolvedor antecipou que isso representaria uma **perda grave de rastreabilidade de negócio**: se o comprador da Gama abrir o portal de compras e vir "120 unidades", o sistema perde o valor probatório da compra original ("10 caixas"), dificultando auditorias de recebimento e resolução de disputas comerciais de avaria de embalagem.
* **Resultado Implementado (ADR-0003):**
  O desenvolvedor orientou a evolução do schema da tabela `purchase_order_items`, adicionando 3 colunas anuláveis (`original_uom`, `original_quantity`, `conversion_factor`). Essa abordagem garantiu:
  1. Rastreabilidade comercial completa para a Gama;
  2. **Zero breaking changes** e compatibilidade retroativa integral com Alfa e Beta;
  3. Preservação do motor de conciliação, que continuou operando sobre as grandezas canônicas em `UN`.

---

### Insight 2: Padrão *Chain of Responsibility* e Feedback Granular Item a Item
* **A Diretriz do Desenvolvedor:**
  > *"Para fazer as verificações do reconciliation, ao invés de chamar um por um na service seria interessante criar um Chain of Responsibility, não? Além disso, quero capturar exatamente qual item foi divergente por exemplo para ter um feedback bem mais responsivo com lineNumber, materialCode, expectedValue, actualValue e difference."*
* **Contexto e Mérito Técnico:**
  A implementação inicial da conferência Three-Way Matching tendia a um método procedural extenso (`if/else`) centralizado dentro de `ReconciliationService`. O desenvolvedor reconheceu que isso violaria o princípio Aberto/Fechado (*Open/Closed Principle*) e tornaria a adição de novas regras fiscais arriscada e propensa a efeitos colaterais.
  Além disso, o desenvolvedor exigiu uma experiência de desenvolvedor (DX) muito superior para os clientes da API: rejeições não deveriam retornar apenas um status genérico de erro, mas apontar cirurgicamente onde ocorreu o problema.
* **Resultado Implementado (ADR-0002):**
  - Criação da interface `ReconciliationRule` e do encadeador `ReconciliationRuleChain`;
  - Decomposição das regras em elos atômicos e testáveis: `OrderExistenceRule`, `VendorTaxIdMatchRule`, `ItemExistenceRule`, `UnitPriceToleranceRule` e `CumulativeQuantityRule`;
  - Retorno de objetos estruturados de auditoria (`ReconciliationDivergenceDto`) contendo o número exato da linha faturada, código do material, valor esperado, valor faturado e a diferença matemática calculada (ex: `+6.1000` ou `+4.0000`).

---

### Insight 3: Reflexão Crítica sobre Governança de Segurança: `ROLE_PLATFORM` vs `ROLE_CLIENT`
* **O Questionamento do Desenvolvedor:**
  > *"Além disso, a autenticação da ROLE_PLATFORM que é a da V360 seria errada se fosse uma 'admin' com acesso a todos os dados internos?"*
* **Contexto e Mérito Técnico:**
  O desenvolvedor questionou criticamente se permitir que a plataforma V360 atuasse como um "superadmin" com acesso irrestrito violaria os princípios de segurança multi-tenant.
* **Resultado Implementado (ADR-0004 & ADR-0005):**
  Esse questionamento provocou uma modelagem formal de governança no Spring Security:
  - **`ROLE_CLIENT`:** Opera sob isolamento estrito de tenant. Clientes (Alfa, Beta, Gama) só podem consultar, ingerir e reconciliar pedidos vinculados ao seu próprio `tenantCode`. Qualquer tentativa de acesso cruzado resulta em `403 FORBIDDEN`.
  - **`ROLE_PLATFORM`:** Representa a operadora central do SaaS B2B da V360. Para que o Three-Way Matching funcione de ponta a ponta no ecossistema, a plataforma precisa de visão transversal: é a V360 que recebe e audita as notas fiscais dos fornecedores externos contra as ordens dos clientes contratantes, além de consolidar relatórios analíticos de inteligência de compras. Essa decisão foi fundamentada e protegida por segregação explícita no filtro JWT.

---

### Insight 4: Otimização de Developer Experience (DX) no Arquivo `requests.http`
* **O Apontamento do Desenvolvedor:**
  > *"Preciso que implemente a autenticação do Beta também no request, não consigo inserir os dados do Beta por causa disso..."*
* **Contexto e Mérito Técnico:**
  Durante a validação prática, a IA havia posicionado o endpoint de autenticação do Cliente Beta no meio do arquivo de requisições, como parte de um cenário de teste secundário. Isso gerava atrito e confusão para quem estivesse avaliando a API de forma sequencial.
* **Resultado Implementado:**
  O desenvolvedor ordenou a consolidação de todas as chamadas de emissão de token (`authAlfa`, `authBeta`, `authGama`, `authPlatform`) nas primeiras 60 linhas de `requests.http`. As variáveis de token foram encadeadas dinamicamente (`@tokenAlfa = {{authAlfa.response.body.accessToken}}`), permitindo que avaliadores executem os testes com 1 clique diretamente no VS Code ou IntelliJ.

---

### Insight 5: Postura Rigorosa de Gatekeeper (*Human-in-the-Loop*)
* **A Postura do Desenvolvedor:**
  > *"Implemente o ticket 06 porém não quero dar commit ainda, pois vou querer testar antes de sinalizar o fim da parte 1. Assim, em vez de dar o commit ao final, me indique como testar e o que testar... Espera meu OK para dar o commit."*
* **Contexto e Mérito Técnico:**
  O desenvolvedor assumiu papel ativo de gatekeeper:
  1. Barrou a automatização cega de commits no repositório Git;
  2. Exigiu roteiros de teste claros antes de aprovar cada marco do projeto;
  3. Submeteu o código a ciclos formais de `/code-review` com subagentes especializados antes de marcar a tag de versão `parte-1`;
  4. Garantiu que a suíte completa de **105 testes automatizados** passasse com 100% de sucesso antes de autorizar a entrega da Parte 2.

---

## 3. Catálogo de Prompts Eficazes (Engenharia de Prompt)

Os prompts que produziram os melhores resultados técnicos seguiram uma estrutura comum: **contextualização do problema de negócio + restrição técnica explícita + padrão de design esperado**.

### Exemplo 1: Refatoração para Chain of Responsibility com Auditoria Granular
* **Prompt Enviado:**
  ```text
  para fazer as verificacoes do reconciliation, ao inves de chamar um por um na service 
  seria interessante criar um chain of responsability nao? alem disso quero capturar exatamente 
  qual item foi divergente por exemplo para ter um feedback bem mais responsivo com 
  lineNumber, materialCode, expectedValue, actualValue e difference
  ```
* **Por que funcionou:**
  Em vez de pedir "melhore o código de conciliação", o prompt nomeou o padrão GoF (*Chain of Responsibility*) e especificou os 5 campos exatos do contrato de dados da divergência, eliminando qualquer ambiguidade de interpretação.

### Exemplo 2: Ingestão Flat JSON com Conversão de Embalagem (Parte 2 - Gama)
* **Prompt Enviado:**
  ```text
  /implement implemente a parte 2 em seguida. O cliente Gama envia JSON flat com campos:
  num_pedido, cnpj_fornecedor, cod_material, qtd_ped, qtd_rec, preco_unit_centavos, data_entrega (timestamp unix), fator_conv.
  Gere o adapter GamaJsonAdapter convertendo centavos para reais, multiplicando quantidades pelo fator,
  calculando o preco unitario base dividido pelo fator, e preservando os campos originais de embalagem no pedido.
  ```
* **Por que funcionou:**
  O prompt delimitou as fórmulas matemáticas exatas e a regra de integridade com o banco de dados canônico, permitindo a geração precisa de DTOs, mappers e testes de integração sem regressão.

---

## 4. Momentos de Correção e Alinhamento da IA

O uso de IA exigiu supervisão técnica ativa. Em vários momentos, a IA sugeriu implementações simplistas que foram identificadas e corrigidas pelo desenvolvedor:

### Correção 1: Validação de Saldo Cumulativo em Notas com Múltiplas Linhas (*Split-Lines*)
* **Proposta Inicial da IA:**
  A IA implementou uma validação ingênua de quantidade item a item: `if (invoiceItem.quantity > orderItem.getPendingQuantity())`.
* **Problema Identificado:**
  Se uma mesma Nota Fiscal contivesse duas linhas faturando o mesmo material (ex: linha 1 com 30 unidades e linha 2 com 20 unidades de um material com saldo pendente de 40), a validação da IA aprovaria ambas as linhas individualmente, embora a soma (50 unidades) estourasse o saldo em aberto (40 unidades).
* **Correção do Desenvolvedor:**
  Instruiu a introdução de um acumulador de consumo de saldo por código de material dentro de `ReconciliationContext`, garantindo a validação cumulativa sequencial durante a conferência da fatura.

### Correção 2: Tolerância de Preço Unitário (Monetária vs. Percentual)
* **Proposta Inicial da IA:**
  A IA propôs aplicar uma tolerância percentual relativa ($\pm 1\%$).
* **Problema Identificado:**
  Em materiais de alto valor agregado (ex: transformadores ou peças industriais de R$ 50.000,00), uma tolerância de 1% permitiria divergências de até R$ 500,00 por unidade, violando a integridade financeira.
* **Correção do Desenvolvedor:**
  O desenvolvedor alinhou a regra estrita do case: tolerância **monetária absoluta** de até **R$ 0,01 por unidade**, utilizando `BigDecimal` com arredondamento `RoundingMode.HALF_UP`.

### Correção 3: Descarte de Embalagem Comercial em Memória
* **Proposta Inicial da IA:**
  Ao planejar a Parte 2, a IA sugeriu converter as caixas da Gama em unidades soltas na memória e gravá-las nas colunas existentes sem tocar no schema de dados.
* **Correção do Desenvolvedor:**
  O desenvolvedor barrou essa simplificação com a pergunta reflexiva sobre o schema, forçando a criação de colunas de auditoria comercial (`original_uom`, `original_quantity`, `conversion_factor`) e garantindo integridade e conformidade de nível corporativo.

---

## 5. Garantia de Domínio Técnico e Qualidade do Código

Para assegurar que a solução final fosse robusta, manutenível e de total domínio da equipe:

1. **Suíte Completa de 105 Testes Automatizados (Zero Falhas):**
   - 105 testes automatizados cobrindo testes unitários, testes de adapters (incluindo tratamento de erros de formato e dados nulos), regras isoladas de matching e testes de integração de controllers com `MockMvc`;
   - Todos os 105 testes passam com sucesso (`Tests run: 105, Failures: 0, Errors: 0, Skipped: 0`).
2. **Revisões de Código Formais via Subagentes Independentes:**
   - A cada ticket, subagentes autônomos inspecionaram o diff do git procurando por *Code Smells* (Feature Envy, Primitive Obsession, Repeated Switches, Shotgun Surgery) e conformidade com os ADRs;
   - Todas as sugestões de melhoria foram incorporadas antes dos commits.
3. **Padrão Canônico e Separação de Responsabilidades:**
   - A taxonomia de classes, entidades e DTOs reflete a linguagem ubíqua do domínio Procure-to-Pay (P2P);
   - O núcleo da aplicação independe de tecnologias de banco ou de detalhes de frameworks externos, assegurando longevidade e facilidade de manutenção.
