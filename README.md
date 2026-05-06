# 🏦 Bank Statement Microservices — Extrato Bancário Multiconta e Multimoeda

Arquitetura de Microsserviços para um Sistema Bancário de Extrato Multiconta e Multimoeda, implementando o **Padrão Saga Orquestrado** com **Redpanda** (Kafka-compatible) como message broker.

Baseado nos conceitos de **CQRS**, **Event Sourcing**, **Clean Architecture** e **Double-Entry Bookkeeping**.

> Implementação prática da arquitetura proposta no TCC: *"Arquitetura de Microsserviços para um Sistema Bancário de Extrato Multiconta e Multimoeda"* — ARAUJO, Julli Mayanne.

---

## 📐 Arquitetura

```
┌─────────────┐     ┌───────────────────────────────────────────────────────┐
│  Frontend   │────▶│              API Gateway / BFF (Next.js)              │
│  (Next.js)  │     └───────────────┬───────────────────────────────────────┘
└─────────────┘                     │
                                    ▼
              ┌─────────────────────────────────────────┐
              │       Orchestrator Service (Java)       │
              │         Saga Orquestrado Pattern        │
              │              PostgreSQL                 │
              └──────┬──────────┬──────────┬────────────┘
                     │          │          │
            ┌────────▼──┐  ┌───▼────┐  ┌──▼──────────┐
            │ Account   │  │ Ledger │  │  Exchange    │
            │ Service   │  │Service │  │  Service     │
            │  (Java)   │  │ (Java) │  │  (Python)   │
            │ PostgreSQL│  │Postgres│  │  MongoDB    │
            └───────────┘  └────────┘  └─────────────┘
                     │          │
                     ▼          ▼
              ┌─────────────────────────┐
              │   Statement Service     │
              │      (Node.js)          │
              │  MongoDB + Redis Cache  │
              └─────────────────────────┘

         ═══════════════════════════════════════
              Redpanda (Kafka-compatible)
              Message Broker / Event Bus
         ═══════════════════════════════════════
```

## 🧩 Microsserviços

| Serviço | Stack | Porta | Banco | Responsabilidade |
|---------|-------|-------|-------|------------------|
| **orchestrator-service** | Java / Spring Boot | 8080 | PostgreSQL (ACID) | Saga Orchestrator — coordena o fluxo transacional |
| **account-service** | Java / Spring Boot | 8081 | PostgreSQL (ACID) | Gestão de contas multimoeda, validação de saldo |
| **ledger-service** | Java / Spring Boot | 8082 | PostgreSQL (ACID) | Contabilidade dupla-entrada, event store imutável |
| **transaction-service** | Go / Gin | 8083 | Stateless | Ingestão de transações, publica eventos no Kafka |
| **exchange-service** | Python / FastAPI | 8084 | MongoDB (NoSQL) | Taxas de câmbio, conversão multimoeda |
| **statement-service** | Node.js / Express | 8085 | MongoDB + Redis | Modelo de leitura CQRS, extrato com cache |
| **frontend** | React / Next.js | 3000 | — | UI mobile-first para demo |

### Database per Service — ACID vs NoSQL

- **PostgreSQL (ACID):** orchestrator, account, ledger — onde dinheiro e consistência contábil importam
- **MongoDB (NoSQL):** exchange, statement — modelos de leitura/referência, alta performance de consulta
- **Redis:** cache do statement-service para extratos frequentes
- **Stateless:** transaction-service — apenas valida e publica no Kafka

## 🚀 Como Executar

### Pré-requisitos
- Docker e Docker Compose

### Subir tudo com um comando

```bash
docker-compose up --build
```

### Acessar

| Serviço | URL |
|---------|-----|
| Frontend (App) | http://localhost:3000 |
| Redpanda Console | http://localhost:8888 |
| Orchestrator API | http://localhost:8080/api/saga |
| Account API | http://localhost:8081/api/accounts |
| Ledger API | http://localhost:8082/api/ledger |
| Transaction API | http://localhost:8083/api/transactions |
| Exchange API | http://localhost:8084/api/exchange |
| Statement API | http://localhost:8085/api/statements |

## 📊 Fluxo da Saga Orquestrada

```
1. Frontend envia transação → Orchestrator Service
2. Orchestrator inicia saga e publica no Kafka:
   ├─ 2.1 account-validation → Account Service valida conta e saldo
   │     └─ Reply: account-validation-reply
   ├─ 2.2 exchange-rate-request → Exchange Service (se multimoeda)
   │     └─ Reply: exchange-rate-reply
   ├─ 2.3 ledger-commands → Ledger Service registra dupla-entrada
   │     └─ Reply: ledger-confirmed
   └─ 2.4 statement-updates → Statement Service atualiza modelo de leitura
3. Orchestrator marca saga como COMPLETED
4. Em caso de falha → Compensação automática (rollback)
```

### Kafka Topics (Redpanda)

| Topic | Descrição |
|-------|-----------|
| `transaction-events` | Eventos de transação ingeridos |
| `account-validation` | Comando: validar conta/saldo |
| `account-validation-reply` | Resposta da validação |
| `exchange-rate-request` | Comando: converter moeda |
| `exchange-rate-reply` | Resposta com taxa de câmbio |
| `ledger-commands` | Comando: registrar no ledger |
| `ledger-confirmed` | Confirmação da dupla-entrada |
| `statement-updates` | Atualizar modelo de leitura |
| `compensation-commands` | Comandos de compensação (rollback) |

## 🧪 Chaos Testing

Suite de testes de caos para validar resiliência da arquitetura:

```bash
cd chaos-test
pip install -r requirements.txt

# Executar todos os testes
python chaos_test.py all

# Testes individuais
python chaos_test.py flood --transactions 500 --workers 20
python chaos_test.py concurrent_transfers --workers 30
python chaos_test.py multi_currency_storm
python chaos_test.py service_kill --target ledger-service
python chaos_test.py reconciliation
```

### Cenários de Teste

| Teste | Descrição |
|-------|-----------|
| **flood** | Envio massivo de transações simultâneas |
| **concurrent_transfers** | Transferências simultâneas entre mesmas contas |
| **multi_currency_storm** | Câmbios cruzados concorrentes |
| **service_kill** | Derruba um serviço durante processamento |
| **reconciliation** | Verifica consistência dos saldos entre serviços |

## 🏗️ Princípios Arquiteturais

- **CQRS** — Separação entre caminho de escrita (ledger) e leitura (statement)
- **Event Sourcing** — Ledger como repositório imutável de eventos
- **Saga Orquestrado** — Orquestrador central coordena transações distribuídas
- **Database per Service** — Cada serviço com seu banco, ACID onde necessário
- **Double-Entry Bookkeeping** — Toda transação gera débito e crédito correspondentes
- **Idempotência** — Proteção contra processamento duplicado
- **Reconciliação** — Mecanismo para verificar consistência entre serviços

## 📚 Referências

- ARAUJO, Julli Mayanne. *Arquitetura de Microsserviços para um Sistema Bancário de Extrato Multiconta e Multimoeda*. TCC, 2026.
- KLEPPMANN, Martin. *Designing Data-Intensive Applications*. O'Reilly, 2017.
- NEWMAN, Sam. *Building Microservices*. O'Reilly, 2015.
- TANENBAUM, A. S.; VAN STEEN, M. *Sistemas Distribuídos*. Pearson, 2017.

## 📄 Licença

MIT License
