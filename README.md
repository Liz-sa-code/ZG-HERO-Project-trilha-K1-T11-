# Crawler TISS — ANS

**Autora:** Liz Soares

## Descrição

Aplicação desenvolvida em Groovy para automatizar a coleta de documentos públicos do padrão TISS (Troca de Informações na Saúde Suplementar), disponibilizados pela Agência Nacional de Saúde Suplementar (ANS).

## Funcionalidades

### 1. Download do componente de comunicação

Acessa a página do padrão TISS e procura links relacionados ao componente de comunicação para baixar os arquivos identificados.

### 2. Histórico das versões

Consulta a página de histórico dos componentes do padrão TISS e exporta os registros desde janeiro de 2016 para um arquivo CSV com os campos:

- Competência;
- Publicação;
- Início de vigência.

### 3. Tabela de erros

Acessa a página de tabelas relacionadas e procura o arquivo da tabela de erros no envio para a ANS.

## Tecnologias

- Groovy;
- Gradle;
- HTTPBuilder;
- Jsoup;
- Spock.

## Requisitos

- JDK 17 ou superior;
- Gradle;
- Conexão com a internet.

## Como executar

Na raiz do projeto, execute:

gradle run


Para executar os testes:


gradle test


## Diretórios de saída

Após a execução, os arquivos serão organizados em:


Downloads/

├── Arquivos_padrao_TISS/

├── Tabela_erros/

└── historico_tiss.csv


