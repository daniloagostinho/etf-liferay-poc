# ETF Liferay POC

## 🚀 Guia de Execução

Para o Liferay sem Docker rode os comandos:

### Instalação Inicial

```bash
chmod +x ./gradlew
./gradlew initBundle
./gradlew :modules:etf-simulator-api:deploy
./gradlew :client-extensions:liferay-sample-custom-element-3:deploy
```

### Deploy Completo

Se quiser garantir tudo de uma vez (todos os CEs + módulos):

```bash
./gradlew deploy
```

## 📋 Pré-requisitos

- **Java**: Versão compatível com seu DXP (normalmente JDK 17)
- **Node/Yarn**: Necessário para o build dos Client Extensions
- **Internet**: Obrigatória no primeiro `initBundle` (será necessário baixar bundle e dependências)

## 🔗 Endpoints da API

Com o servidor ligado, a API fica disponível nos seguintes endpoints:

```
GET  /o/etf-simulator/v1/health
GET  /o/etf-simulator/v1/etfs
GET  /o/etf-simulator/v1/benchmarks
GET  /o/etf-simulator/v1/simulations
POST /o/etf-simulator/v1/simulate
```

## 🧠 Backend (Liferay + Java)

### Tecnologias

- OSGi JAX-RS no Liferay DXP
- Java (módulo em `modules/etf-simulator-api`)
- API REST própria da PoC
- Elasticsearch 7 (sidecar local do Liferay) para persistência e consulta

### Regras de negócio implementadas

- Simulação com juros compostos mensais + aportes mensais
- Taxa líquida anual = taxa esperada - taxa de administração (pode ser negativa)
- Validação de payload no backend (faixas mín/máx)
- Mensagens de erro claras para parâmetros inválidos
- Persistência da simulação no Elasticsearch com retorno de status (`simulationPersistence`)

### Uso real do Elasticsearch na PoC

O Elasticsearch deixou de ser apenas “health check” e passou a ser usado de fato:

- Índice `etf_poc_etfs`
	- armazena ETFs de referência
	- endpoint `/etfs` lê desse índice
	- se o índice estiver vazio, o backend faz seed inicial automático

- Índice `etf_poc_benchmarks`
	- armazena benchmarks da PoC (ex.: Ibovespa e CDI)
	- endpoint `/benchmarks` lê desse índice
	- também possui seed automático

- Índice `etf_poc_simulations`
	- grava cada execução de simulação (request + resumo + série mensal)
	- endpoint `/simulations` retorna histórico recente
	- endpoint `/simulate` retorna status de persistência com `saved` e `documentId`

### Por que o Elasticsearch foi usado

- Demonstrar busca/indexação real além de health check
- Persistir histórico de simulações para auditoria e demonstração de integração
- Centralizar dados de referência (ETFs/benchmarks) com seed automático

Observação: por ser PoC local, o backend mantém aviso explícito de não uso em produção.

## 🎨 Frontend (Angular Client Extension)

### Tecnologias/libs

- Angular 16
- Tailwind CSS 3.4
- ng2-charts + Chart.js
- FormsModule + HttpClientModule
- PostCSS + Autoprefixer (pipeline de estilos)

### Como o frontend usa a API

- Carrega ETFs em `/etfs`
- Carrega benchmarks em `/benchmarks`
- Executa simulação em `/simulate`
- Carrega histórico de simulações em `/simulations`
- Exibe gráfico comparativo (portfólio x Ibovespa x CDI)
- Mostra avisos de PoC/benchmark hipotético quando retornados pelo backend
- Exibe status da gravação da última simulação no Elasticsearch

### Funcionalidades de UI implementadas

- Painel de parâmetros com sliders e inputs numéricos
- Cards de KPI: Total Investido, Ganho Estimado, Retorno e Valor Final
- Gráfico de linhas com seleção de período (36M, 24M, 12M, 6M, 3M, Ano atual)
- Tabela de histórico integrada ao Elasticsearch (datas e métricas formatadas)

## ⚠️ Escopo da PoC

- O objetivo é demonstrar integração Liferay + Angular + Elasticsearch com regra simples e compreensível.
- Não representa recomendação financeira nem modelo de risco completo.
