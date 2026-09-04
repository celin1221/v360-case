# Handoff: Continuidade do Desenvolvimento em Outra Máquina

Este documento serve como guia rápido para você (ou qualquer agente de IA) retomar exatamente de onde paramos em outro computador.

---

## 1. Estado Atual do Projeto

- [x] **Alinhamento e Desafio Compreendido:** Problema de negócio (Procure-to-Pay, Three-Way Matching), formatos dos clientes e critérios de avaliação da liderança.
- [x] **Decisões de Arquitetura Registradas:**
  - `docs/adr/0001-canonical-data-model-and-adapter-architecture.md` (Strategy Pattern)
  - `docs/adr/0002-repository-port-and-in-memory-adapter.md` (Porta desacoplada com impl JPA e In-Memory)
  - `docs/adr/0003-base-unit-normalization-with-commercial-packaging-preservation.md` (Conversão Gama)
  - `docs/adr/0004-client-identity-and-tenant-code.md` (Identificadores imutáveis de inquilino)
  - `docs/adr/0005-oauth2-client-credentials-jwt-security.md` (Segurança M2M com JWT)
- [x] **Glossário de Domínio:** `CONTEXT.md` atualizado com termos canônicos e termos a evitar.
- [x] **Especificação Técnica Aprovada:** `.scratch/purchase-order-gateway/spec.md`.
- [x] **Tickets de Entrega Estruturados:** 8 tickets verticais em `.scratch/purchase-order-gateway/issues/`.
- [x] **Amostras de Dados Criadas:** Pasta `sample-data/` com os arquivos de Alfa, Beta e Gama.

---

## 2. Próxima Atividade Imediata

👉 **Implementar o Ticket 01:**
Arquivo: `.scratch/purchase-order-gateway/issues/01-project-skeleton-and-oauth2-m2m.md`
- Scaffold do Spring Boot 3 com Java 21 e Maven Wrapper.
- Configuração de dependências (`web`, `data-jpa`, `validation`, `security`, `h2`, `springdoc-openapi`, `jjwt`).
- Endpoint `POST /api/v1/auth/token` e filtro de segurança JWT.
- Configuração do Swagger UI em `/swagger-ui.html`.

---

## 3. Prompt de Retomada para Usar na IA no Outro PC

Ao abrir o repositório clonado no outro computador, envie este prompt exato para o assistente de IA:

```text
Olá! Estou dando continuidade ao desenvolvimento do desafio V360 (Conector de Pedidos de Compra) neste novo computador. 

Por favor, leia os arquivos:
1. HANDOFF.md (resumo de onde paramos)
2. AGENTS.md e CONTEXT.md (instruções e termos canônicos de negócio)
3. docs/adr/ (as 5 decisões de arquitetura aprovadas)
4. .scratch/purchase-order-gateway/spec.md (a especificação técnica)

Nosso próximo passo na fronteira de trabalho é implementar o Ticket 01 (.scratch/purchase-order-gateway/issues/01-project-skeleton-and-oauth2-m2m.md). Podemos começar?
```
