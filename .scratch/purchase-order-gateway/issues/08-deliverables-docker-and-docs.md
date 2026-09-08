# 08: Entregáveis Finais, Coleções de Teste, Docker e Documentação

**What to build:** Todos os artefatos de entrega e facilidade de teste solicitados pela banca: coleção de chamadas prontas com encadeamento de token OAuth2, conteinerização Docker, e a documentação completa cobrindo decisões de negócio, regras de tolerância, guia de execução e relato do uso de IA.

**Blocked by:** 07-client-gama-ingestion-and-conversion

**Status:** done

- [x] Arquivo `requests.http` com fluxo completo autenticado (obtenção de token, consultas, filtros, conferências que aprovam e reprovam, e relatório)
- [x] Arquivo de coleção Postman correspondente (`v360-collection.json`)
- [x] `Dockerfile` multi-stage com build e runtime otimizados para Java 21
- [x] `docker-compose.yml` para execução rápida com 1 comando
- [x] `README.md` abrangente:
  - O problema de negócio (Procure-to-Pay, Three-Way Matching e o papel da camada de integração)
  - Decisões de arquitetura (Padrão Strategy, Modelo Canônico, Isolamento de Tenant, OAuth2 JWT)
  - Seção obrigatória: "O que mudou da Parte 1 para a Parte 2"
  - O que faria diferente com mais tempo
  - Instruções claras de execução local e via Docker
- [x] `AI_USAGE.md` obrigatório:
  - Ferramentas de IA utilizadas e em quais etapas
  - Exemplo de prompt que funcionou bem e como foi aproveitado
  - Exemplo de condução/ajuste onde a IA foi corrigida
  - Como foi garantido o domínio total sobre o código entregue
- [ ] Commit e tag final do projeto (movido para a etapa de revisão final na Issue 09)
