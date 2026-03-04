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
POST /o/etf-simulator/v1/simulate
```
