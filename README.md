# BankStatement — Extrato Bancário Multiconta e Multimoeda

Arquitetura de Microsserviços poliglota para um Sistema Bancário de Extrato Multiconta e Multimoeda, implementando o **Padrão Saga Orquestrado** com **Redpanda** (Kafka-compatible), **Transactional Outbox** e observabilidade com **Prometheus + Grafana**.

> Implementação prática da arquitetura proposta no TCC: *"Arquitetura de Microsserviços para um Sistema Bancário de Extrato Multiconta e Multimoeda"* — ARAUJO, Julli Mayanne.

---

## Arquitetura Geral

```mermaid
graph TB
    subgraph Frontend
        UI[React / Next.js<br/>:3000]
    end

    subgraph API Gateway
        GW[Next.js Rewrites<br/>Proxy para microsserviços]
    end

    subgraph Orchestration
        ORC[Orchestrator Service<br/>Java / Spring Boot<br/>:8080]
    end

    subgraph Microservices
        ACC[Account Service<br/>Java / Spring Boot<br/>:8081]
        LED[Ledger Service<br/>Java / Spring Boot<br/>:8082]
        TXN[Transaction Service<br/>Go / Gin<br/>:8083]
        EXC[Exchange Service<br/>Python / FastAPI<br/>:8084]
        STM[Statement Service<br/>Node.js / Express<br/>:8085]
    end

    subgraph Message Broker
        RP[Redpanda<br/>Kafka-compatible<br/>:19092]
    end

    subgraph Databases
        PG1[(PostgreSQL<br/>orchestrator_db)]
        PG2[(PostgreSQL<br/>account_db)]
        PG3[(PostgreSQL<br/>ledger_db)]
        MG1[(MongoDB<br/>exchange_db)]
        MG2[(MongoDB<br/>statement_db)]
        RD[(Redis<br/>Cache)]
    end

    subgraph Observability
        PROM[Prometheus<br/>:9090]
        GRAF[Grafana<br/>:3001]
    end

    UI --> GW
    GW --> ORC
    GW --> ACC
    GW --> TXN
    GW --> EXC
    GW --> STM

    ORC <--> RP
    ACC <--> RP
    LED <--> RP
    TXN --> RP
    EXC <--> RP
    STM <-- consume --> RP

    ORC --> PG1
    ACC --> PG2
    LED --> PG3
    EXC --> MG1
    STM --> MG2
    STM --> RD

    PROM --> ORC
    PROM --> ACC
    PROM --> LED
    PROM --> TXN
    PROM --> EXC
    PROM --> STM
    GRAF --> PROM
```

## Fluxo da Saga Orquestrada

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant TX as Transaction<br/>(Go)
    participant RP as Redpanda<br/>(Kafka)
    participant OR as Orchestrator<br/>(Java)
    participant AC as Account<br/>(Java)
    participant EX as Exchange<br/>(Python)
    participant LE as Ledger<br/>(Java)
    participant ST as Statement<br/>(Node.js)

    FE->>TX: POST /api/transactions
    TX->>RP: publish transaction-events
    RP->>OR: consume transaction-events
    OR->>OR: Criar SagaInstance (STARTED)

    Note over OR: Step 1 - Validação de Conta
    OR->>RP: publish account-validation
    RP->>AC: consume account-validation
    AC->>AC: Validar conta e saldo
    AC->>RP: publish account-validation-reply
    RP->>OR: consume reply
    OR->>OR: Atualizar saga (ACCOUNT_VALIDATED)

    Note over OR: Step 2 - Câmbio (se multimoeda)
    OR->>RP: publish exchange-rate-request
    RP->>EX: consume exchange-rate-request
    EX->>EX: Calcular taxa de câmbio
    EX->>RP: publish exchange-rate-reply
    RP->>OR: consume reply
    OR->>OR: Atualizar saga (CURRENCY_CONVERTED)

    Note over OR: Step 3 - Registro no Ledger
    OR->>RP: publish ledger-commands
    RP->>LE: consume ledger-commands
    LE->>LE: Registrar dupla-entrada
    LE->>RP: publish ledger-confirmed
    RP->>OR: consume reply
    OR->>OR: Atualizar saga (LEDGER_RECORDED)

    Note over OR: Step 4 - Atualizar Extrato
    OR->>RP: publish statement-updates
    RP->>ST: consume statement-updates / ledger-confirmed
    ST->>ST: Atualizar modelo de leitura (CQRS)
    OR->>OR: Saga COMPLETED ✓

    Note over OR,AC: Em caso de falha → compensation-commands (rollback automático)
```

## Tópicos Kafka (Redpanda)

```mermaid
graph LR
    subgraph Producers
        TX[Transaction Service]
        OR[Orchestrator]
        AC[Account Service]
        EX[Exchange Service]
        LE[Ledger Service]
    end

    subgraph Topics
        T1[transaction-events]
        T2[account-validation]
        T3[account-validation-reply]
        T4[exchange-rate-request]
        T5[exchange-rate-reply]
        T6[ledger-commands]
        T7[ledger-confirmed]
        T8[statement-updates]
        T9[compensation-commands]
    end

    subgraph Consumers
        OR2[Orchestrator]
        AC2[Account Service]
        EX2[Exchange Service]
        LE2[Ledger Service]
        ST[Statement Service]
    end

    TX --> T1
    T1 --> OR2

    OR --> T2
    T2 --> AC2
    AC --> T3
    T3 --> OR2

    OR --> T4
    T4 --> EX2
    EX --> T5
    T5 --> OR2

    OR --> T6
    T6 --> LE2
    LE --> T7
    T7 --> OR2
    T7 --> ST

    OR --> T8
    T8 --> ST

    OR --> T9
    T9 --> AC2
    T9 --> LE2
```

## Padrão Outbox (Transactional Outbox)

```mermaid
graph LR
    subgraph "Transação de Negócio (mesma TX)"
        BIZ[Business Logic] --> DB[(PostgreSQL)]
        BIZ --> OBX[outbox_events table]
    end

    subgraph "Scheduler Assíncrono"
        SCH[OutboxPublisher<br/>poll 1s] --> OBX
        SCH --> KFK[Redpanda / Kafka]
    end

    subgraph "Status do Evento"
        S1[PENDING] --> S2[PUBLISHED]
        S1 --> S3[FAILED<br/>retry < 5x]
        S3 --> S2
    end

    style OBX fill:#fff3e0,stroke:#ff6f00
    style KFK fill:#e8f5e9,stroke:#2e7d32
```

## Fluxo de Dados End-to-End

```mermaid
graph TB
    subgraph "Caminho de Escrita (Write Path)"
        A[Usuário cria transação] --> B[Transaction Service - Go]
        B --> C[Kafka: transaction-events]
        C --> D[Orchestrator - Java]
        D --> E{Saga Steps}
        E --> F[Account: Validação + Reserva]
        E --> G[Exchange: Conversão Cambial]
        E --> H[Ledger: Dupla-Entrada Contábil]
        F --> D
        G --> D
        H --> D
    end

    subgraph "Outbox Pattern"
        D --> OB[outbox_events table<br/>PostgreSQL]
        OB --> SCH[OutboxPublisher<br/>Scheduler 1s]
        SCH --> K2[Kafka Topics]
    end

    subgraph "Caminho de Leitura (Read Path - CQRS)"
        K2 --> I[Statement Service - Node.js]
        I --> J[(MongoDB<br/>statement_db)]
        I --> K[(Redis Cache)]
        K --> L[API /statements/:id]
        J --> L
        L --> M[Frontend - React]
    end

    style A fill:#e3f2fd
    style M fill:#e3f2fd
    style OB fill:#fff3e0,stroke:#ff6f00
```

## Microsserviços

| Serviço | Stack | Porta | Banco | Responsabilidade |
|---------|-------|-------|-------|------------------|
| **orchestrator-service** | Java 17 / Spring Boot 3.2 | 8080 | PostgreSQL (ACID) | Saga Orchestrator — coordena fluxo transacional |
| **account-service** | Java 17 / Spring Boot 3.2 | 8081 | PostgreSQL (ACID) | Gestão de contas multimoeda, validação de saldo |
| **ledger-service** | Java 17 / Spring Boot 3.2 | 8082 | PostgreSQL (ACID) | Contabilidade dupla-entrada, event store imutável |
| **transaction-service** | Go 1.21 / Gin | 8083 | Stateless | Ingestão de transações, publica eventos no Kafka |
| **exchange-service** | Python 3.11 / FastAPI | 8084 | MongoDB (NoSQL) | Taxas de câmbio, conversão multimoeda |
| **statement-service** | Node.js 20 / Express | 8085 | MongoDB + Redis | Modelo de leitura CQRS, extrato com cache |
| **frontend** | React / Next.js 14 | 3000 | — | UI mobile-first estilo banco digital |

### Database per Service — ACID vs NoSQL

| Banco | Serviços | Justificativa |
|-------|----------|---------------|
| **PostgreSQL (ACID)** | orchestrator, account, ledger | Dinheiro, saldos, contabilidade — consistência forte obrigatória |
| **MongoDB (NoSQL)** | exchange, statement | Modelos de leitura/referência — alta performance de consulta |
| **Redis** | statement (cache) | Cache de extratos frequentes — TTL 60s |
| **Stateless** | transaction | Apenas valida e publica no Kafka |

---

## Como Executar

### Pré-requisitos
- Docker e Docker Compose

### Subir tudo com um comando

```bash
docker-compose up --build
```

### Acessar Serviços

| Serviço | URL | Descrição |
|---------|-----|-----------|
| **Frontend (App)** | http://localhost:3000 | App bancário mobile-first |
| **Grafana** | http://localhost:3001 | Dashboards de observabilidade (admin/admin) |
| **Prometheus** | http://localhost:9090 | Métricas brutas |
| **Redpanda Console** | http://localhost:8888 | Visualizar tópicos Kafka |
| Orchestrator API | http://localhost:8080 | Saga orchestrator |
| Account API | http://localhost:8081 | Gestão de contas |
| Ledger API | http://localhost:8082 | Contabilidade |
| Transaction API | http://localhost:8083 | Ingestão de transações |
| Exchange API | http://localhost:8084 | Câmbio |
| Statement API | http://localhost:8085 | Extrato (CQRS read) |

---

## Endpoints REST

### Transaction Service (Go) — `:8083`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/api/transactions` | Criar transação (publica evento no Kafka) |
| `GET` | `/api/transactions/health` | Health check |
| `GET` | `/api/transactions/metrics` | Métricas Prometheus |

```json
// POST /api/transactions
{
  "sourceAccountId": "account-joao-001",
  "targetAccountId": "account-maria-002",
  "transactionType": "TRANSFER_OUT",
  "amount": 100.00,
  "currency": "BRL",
  "description": "Pagamento aluguel"
}
```

### Orchestrator Service (Java) — `:8080`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/api/saga/transaction` | Iniciar saga de transação |
| `GET` | `/api/saga/status/{sagaId}` | Status da saga (steps, estado atual) |
| `GET` | `/api/saga/all` | Listar todas as sagas |
| `GET` | `/api/outbox/pending` | Eventos outbox pendentes |
| `GET` | `/api/outbox/failed` | Eventos outbox com falha |
| `GET` | `/api/outbox/saga/{sagaId}` | Eventos outbox de uma saga |
| `GET` | `/api/outbox/stats` | Estatísticas do outbox (total, pending, published, failed) |
| `GET` | `/actuator/prometheus` | Métricas Prometheus |
| `GET` | `/actuator/health` | Health check |

```json
// POST /api/saga/transaction
{
  "sourceAccountId": "account-joao-001",
  "targetAccountId": "account-maria-002",
  "transactionType": "TRANSFER_OUT",
  "amount": 100.00,
  "currency": "BRL"
}

// GET /api/saga/status/{sagaId} — Response
{
  "sagaId": "uuid",
  "currentStatus": "COMPLETED",
  "steps": [
    { "stepName": "ACCOUNT_VALIDATION", "service": "account-service", "status": "ACCOUNT_VALIDATED", "timestamp": "..." },
    { "stepName": "LEDGER_RECORD", "service": "ledger-service", "status": "LEDGER_RECORDED", "timestamp": "..." }
  ]
}

// GET /api/outbox/stats — Response
{
  "total": 42,
  "pending": 0,
  "published": 40,
  "failed": 2
}
```

### Account Service (Java) — `:8081`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/accounts` | Listar todas as contas |
| `GET` | `/api/accounts/{accountId}` | Detalhes de uma conta (com saldos) |
| `GET` | `/actuator/prometheus` | Métricas Prometheus |
| `GET` | `/actuator/health` | Health check |

```json
// GET /api/accounts/{accountId} — Response
{
  "accountId": "account-joao-001",
  "holderName": "João Silva",
  "holderDocument": "123.456.789-00",
  "status": "ACTIVE",
  "balances": [
    { "currency": "BRL", "balance": 10000.00 },
    { "currency": "USD", "balance": 1000.00 },
    { "currency": "EUR", "balance": 500.00 }
  ]
}
```

### Ledger Service (Java) — `:8082`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/ledger/entries` | Listar entradas do ledger |
| `GET` | `/api/ledger/entries/{accountId}` | Entradas por conta |
| `GET` | `/actuator/prometheus` | Métricas Prometheus |
| `GET` | `/actuator/health` | Health check |

### Exchange Service (Python) — `:8084`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/exchange/rates` | Todas as taxas de câmbio |
| `GET` | `/api/exchange/rate/{source}/{target}` | Taxa específica (ex: BRL/USD) |
| `GET` | `/api/exchange/convert/{source}/{target}/{amount}` | Converter valor |
| `GET` | `/api/exchange/health` | Health check |
| `GET` | `/metrics` | Métricas Prometheus |

```json
// GET /api/exchange/rate/BRL/USD — Response
{
  "sourceCurrency": "BRL",
  "targetCurrency": "USD",
  "rate": 0.18
}

// GET /api/exchange/convert/BRL/USD/1000 — Response
{
  "sourceCurrency": "BRL",
  "targetCurrency": "USD",
  "sourceAmount": 1000.0,
  "rate": 0.18,
  "convertedAmount": 180.0
}
```

### Statement Service (Node.js) — `:8085`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/statements/{accountId}` | Extrato completo (com saldos) |
| `GET` | `/api/statements/{accountId}?currency=BRL` | Extrato filtrado por moeda |
| `GET` | `/api/statements/{accountId}?limit=20&offset=0` | Paginação |
| `GET` | `/api/statements/{accountId}/balances` | Saldos por moeda |
| `GET` | `/api/statements/health` | Health check |
| `GET` | `/metrics` | Métricas Prometheus |

```json
// GET /api/statements/account-joao-001 — Response
{
  "accountId": "account-joao-001",
  "entries": [
    {
      "entryId": "uuid-debit",
      "sagaId": "saga-uuid",
      "accountId": "account-joao-001",
      "direction": "DEBIT",
      "amount": 100.00,
      "currency": "BRL",
      "description": "Débito - Saga xxx",
      "timestamp": "2026-05-06T18:00:00Z"
    }
  ],
  "currentBalances": [
    { "currency": "BRL", "balance": 9900.00 },
    { "currency": "USD", "balance": 1000.00 }
  ],
  "totalCredits": 500.00,
  "totalDebits": 600.00,
  "totalEntries": 12
}
```

---

## Observabilidade (Grafana + Prometheus)

Após `docker-compose up`, acesse:

- **Grafana**: http://localhost:3001 (login: `admin` / `admin`)
- **Prometheus**: http://localhost:9090

### Dashboard pré-configurado

O dashboard **"Bank Statement Microservices - Overview"** é provisionado automaticamente e inclui:

| Painel | Descrição |
|--------|-----------|
| Service Health | Status UP/DOWN de cada serviço |
| HTTP Request Rate | Requisições/segundo por serviço |
| HTTP Latency p95 | Percentil 95 de latência |
| Saga Status | Total de sagas por status |
| Outbox Events | Eventos outbox (pending, published, failed) |
| Kafka Consumer Lag | Lag dos consumers |
| JVM Memory | Uso de heap dos serviços Java |
| DB Connections | Conexões HikariCP (active/idle) |
| Transactions Published | Counter do Go service |
| Exchange Conversions | Counter do Python service |
| Statement Entries | Counter do Node.js service |

### Métricas por serviço

| Serviço | Endpoint | Tecnologia |
|---------|----------|------------|
| Java (3 serviços) | `/actuator/prometheus` | Spring Boot Actuator + Micrometer |
| Go | `/api/transactions/metrics` | Prometheus text format manual |
| Python | `/metrics` | prometheus-client |
| Node.js | `/metrics` | prom-client |

---

## Chaos Testing

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

| Teste | Descrição |
|-------|-----------|
| **flood** | Envio massivo de transações simultâneas (throughput + latência) |
| **concurrent_transfers** | Transferências simultâneas entre mesmas contas (consistência) |
| **multi_currency_storm** | Câmbios cruzados concorrentes BRL→USD→EUR→GBP |
| **service_kill** | Derruba um container durante processamento (compensação) |
| **reconciliation** | Verifica consistência de saldos entre account, ledger e statement |

> Acompanhe os testes em tempo real no **Grafana** (http://localhost:3001)

---

## Padrões Arquiteturais

| Padrão | Descrição |
|--------|-----------|
| **Saga Orquestrado** | Orquestrador central coordena transações distribuídas com compensação |
| **Transactional Outbox** | Eventos persistidos no banco antes do Kafka — resiliência a falhas do broker |
| **CQRS** | Separação entre caminho de escrita (ledger) e leitura (statement) |
| **Event Sourcing** | Ledger como repositório imutável de eventos (append-only) |
| **Database per Service** | Cada serviço com seu banco, ACID onde necessário |
| **Double-Entry Bookkeeping** | Toda transação gera débito e crédito correspondentes |
| **Idempotência** | Proteção contra processamento duplicado via idempotencyKey |

---

## Estrutura do Projeto

```
bank-statement-microservices/
├── orchestrator-service/     # Java — Saga Orchestrator
├── account-service/          # Java — Gestão de contas
├── ledger-service/           # Java — Contabilidade dupla-entrada
├── transaction-service/      # Go — Ingestão de transações
├── exchange-service/         # Python — Câmbio multimoeda
├── statement-service/        # Node.js — Extrato (CQRS read model)
├── common/                   # Java — Módulo compartilhado (DTOs, Outbox)
├── frontend/                 # React / Next.js — UI banco digital
├── infra/
│   ├── init-databases.sql    # Criação dos bancos PostgreSQL
│   ├── prometheus/           # Configuração do Prometheus
│   └── grafana/              # Provisioning + dashboards Grafana
├── chaos-test/               # Suite de testes de caos (Python)
├── article/                  # Artigo SBBD 2026 (LaTeX SBC)
├── api-collection.http       # Collection HTTP para testar endpoints
└── docker-compose.yml        # Infraestrutura completa (1 comando)
```

---

## Referências

- ARAUJO, Julli Mayanne. *Arquitetura de Microsserviços para um Sistema Bancário de Extrato Multiconta e Multimoeda*. TCC, 2026.
- KLEPPMANN, Martin. *Designing Data-Intensive Applications*. O'Reilly, 2017.
- NEWMAN, Sam. *Building Microservices*. O'Reilly, 2015.
- TANENBAUM, A. S.; VAN STEEN, M. *Sistemas Distribuídos*. Pearson, 2017.

## Licença

MIT License
