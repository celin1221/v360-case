# 09: Revisão Geral do Sistema, README.md e AI_USAGE.md

**What to do:** Revisão minuciosa e validação end-to-end de todo o ecossistema da solução antes da sinalização e commit final de encerramento do projeto. O foco principal da revisão é a qualidade e consistência do `README.md` e do `AI_USAGE.md`, além de uma inspeção global dos fluxos da aplicação.

**Blocked by:** 08-deliverables-docker-and-docs

**Status:** open / in-review

### Itens de Revisão:

- [ ] **Revisão Detalhada do `README.md`:**
  - [ ] Validar a clareza da explicação do problema de negócio (Procure-to-Pay, Three-Way Matching e papel da V360)
  - [ ] Conferir a acurácia dos diagramas e da descrição dos padrões (Ports & Adapters, Chain of Responsibility, Modelo Canônico)
  - [ ] Verificar a resposta à pergunta obrigatória da Parte 2 ("O que mudou da Parte 1 para a Parte 2") e a explicação do impacto no banco de dados / schema
  - [ ] Testar comandos de execução local e via Docker Compose
  - [ ] Checar a tabela de credenciais OAuth2 e documentação dos endpoints

- [ ] **Revisão Aprofundada do `AI_USAGE.md`:**
  - [ ] Avaliar a descrição dos 5 grandes insights do desenvolvedor humano (Schema na Parte 2, Chain of Responsibility com feedback granular, governança ROLE_PLATFORM vs ROLE_CLIENT, DX em requests.http e postura de Gatekeeper)
  - [ ] Verificar a precisão dos exemplos de prompts eficazes e momentos de correção/alinhamento da IA (saldo cumulativo em split-lines, tolerância de R$ 0,01 absoluta, preservação de metadados de embalagem)
  - [ ] Garantir que o documento evidencia com transparência e destaque a condução de liderança técnica pelo desenvolvedor

- [ ] **Revisão Global do Sistema:**
  - [ ] Executar a suíte de testes (`.\mvnw.cmd test` / `./mvnw test`) e garantir 105/105 testes passando
  - [ ] Validar o fluxo de ponta a ponta no `requests.http` ou Postman (ingestão de Alfa, Beta, Gama, consultas com paginação/filtros, conferências Three-Way Matching de sucesso e reprovação, e relatório de auditoria)
  - [ ] Conferir os contratos de retorno de erro e divergências granulares
  - [ ] Validar o build e subida do contêiner Docker via `docker compose up --build`

- [ ] **Encerramento:**
  - [ ] Ajustes finais decorrentes dos apontamentos da revisão
  - [ ] Commit final e marcação de tag de encerramento
