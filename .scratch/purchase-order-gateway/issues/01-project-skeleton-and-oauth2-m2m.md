# 01: Setup do Projeto, Infraestrutura e Autenticação OAuth2 M2M

**What to build:** Uma aplicação backend Spring Boot 3 funcional em Java 21 com Maven Wrapper, documentação OpenAPI/Swagger interativa em `/swagger-ui.html`, persistência relacional com H2 em arquivo e em memória, e o endpoint `POST /api/v1/auth/token` emitindo tokens JWT assinados com escopo de tenant (`ROLE_PLATFORM` e `ROLE_CLIENT`).

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Projeto Spring Boot 3 configurado com Java 21 e executável via `./mvnw.cmd` e `./mvnw`
- [ ] Configuração de banco H2 persistente em arquivo (`./data/v360db`) e console web (`/h2-console`)
- [ ] Suporte a perfil In-Memory para testes desacoplados do banco de dados
- [ ] Endpoint `POST /api/v1/auth/token` gerando JWT assinado com claims de roles e tenant
- [ ] Filtro de segurança Spring Security validando Bearer JWT nas rotas protegidas
- [ ] Configuração do Springdoc OpenAPI com botão "Authorize" para Bearer JWT
- [ ] Testes de integração validando emissão de token e proteção de endpoints
