# 03: Ingestão do Cliente Beta Alimentos (CSVs Divididos no Padrão BR)

**What to build:** O conector de ingestão do Cliente Beta Alimentos capaz de processar dois arquivos CSV separados por ponto e vírgula (`cabecalho.csv` e `itens.csv`), interpretar o formato numérico brasileiro (vírgula como decimal, ponto como milhar), datas no formato `DD/MM/YYYY`, máscaras de CNPJ, mapear situações (`EM ABERTO`, `ENCERRADO`, `BLOQUEADO`), relacionar itens aos pedidos e salvar com upsert idempotente.

**Blocked by:** 01-project-skeleton-and-oauth2-m2m

**Status:** done

- [x] Componente `BetaCsvAdapter` que faz o parse de números com pontuação BR (`1.200,000` $\rightarrow$ `1200.00`, `6,49` $\rightarrow$ `6.49`)
- [x] Parser de datas brasileiras (`15/08/2026` $\rightarrow$ `LocalDate`)
- [x] Sanitização de CNPJ com máscara para 14 dígitos numéricos
- [x] Endpoint `POST /api/v1/ingestion/beta` recebendo os arquivos multipart (`headerFile` e `itemsFile`) ou conteúdo bruto
- [x] Vinculação correta entre cabeçalhos e itens via `NUMERO_PEDIDO`
- [x] Upsert idempotente no repositório
- [x] Testes unitários com as amostras do enunciado e testes de integração do endpoint
