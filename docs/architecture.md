# Arhitectura

Aplicația este un singur serviciu Spring Boot care lucrează cu două baze de date PostgreSQL separate, câte una pentru fiecare bancă. Între cele două baze nu există nicio tranzacție comună.

```mermaid
flowchart TB
    client(["Client / Gatling"])
    runner(["BenchmarkRunner<br/>profil benchmark"])

    subgraph app["Aplicația Spring Boot · ro.saga.transfer"]
        direction TB

        web["<b>web</b><br/>POST /transfers · GET /health/banks"]
        variants["<b>TransferService</b><br/>V1 plain · V2 orchestration · V3 choreography · …"]
        metrics["<b>TransferMetrics</b><br/>timer transfer.duration"]

        subgraph shared["Strat comun (identic pentru toate variantele)"]
            direction LR
            banks["<b>Banks</b> → BankOperations<br/>debit · credit · refund · rânduri transfer"]
            fraud["<b>FraudCheckService</b><br/>simulat: latență, regulă, timeout"]
            audit["<b>AuditService</b><br/>simulat"]
            faults["<b>FaultInjector</b><br/>seed · rată · FaultPoint"]
            ledger["<b>LedgerService</b> · InvariantChecker"]
        end
    end

    subgraph docker["Docker Compose"]
        direction LR
        proxy["Toxiproxy<br/>:15432 · :15433<br/>(doar profil chaos)"]
        alphaDatabase[("alpha_bank<br/>PostgreSQL 17 · :5432")]
        betaDatabase[("beta_bank<br/>PostgreSQL 17 · :5433")]
    end

    client -->|HTTP| web
    web --> metrics
    runner --> metrics
    metrics --> variants
    variants --> banks
    variants --> fraud
    variants --> audit
    variants -.-> faults
    fraud -.-> faults
    audit -.-> faults
    runner --> ledger
    banks -->|JDBC| alphaDatabase
    banks -->|JDBC| betaDatabase
    ledger --> alphaDatabase
    ledger --> betaDatabase
    proxy -.-> alphaDatabase
    proxy -.-> betaDatabase
    alphaDatabase x--x|fără tranzacție comună| betaDatabase

    classDef entry fill:#e0f2fe,stroke:#0284c7,color:#0c4a6e
    classDef core fill:#ede9fe,stroke:#7c3aed,color:#3b0764
    classDef sim fill:#fef9c3,stroke:#ca8a04,color:#713f12
    classDef alphaNode fill:#dcfce7,stroke:#16a34a,color:#14532d
    classDef betaNode fill:#ffedd5,stroke:#ea580c,color:#7c2d12

    class client,runner,web entry
    class variants,metrics,banks,ledger core
    class fraud,audit,faults,proxy sim
    class alphaDatabase alphaNode
    class betaDatabase betaNode

    style shared fill:#f5f3ff,stroke:#7c3aed,color:#3b0764
    style docker fill:#f8fafc,stroke:#64748b,stroke-dasharray: 5 5
```

Liniile punctate sunt locurile unde se injectează erori: `FaultInjector` în cod, Toxiproxy în rețea.

## Fluxul V1 (Plain-Correct)

Cei cinci pași din [contracts.md](contracts.md), cu locurile de eșec. Aceeași logică o refac V2, V3 și V5 cu pattern-uri.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant V1 as PlainTransferService
    participant A as Alpha Bank
    participant F as FraudCheckService
    participant B as Beta Bank
    participant Au as AuditService

    C->>V1: transfer(request)
    V1->>V1: validare, idempotencyKey nou
    V1->>A: debitAndRecordPending (PENDING + debit, o tranzacție)
    Note over V1,A: FaultPoint AFTER_DEBIT
    V1->>F: check(request)
    Note over F: FRAUD_TIMEOUT sau REJECTED
    alt antifraudă respinge sau eroare
        V1->>A: compensate (refund + COMPENSATED, retry cu backoff)
        Note over V1,A: DURING_COMPENSATION → rămâne PENDING, îl preia RecoveryJob
        V1-->>C: REJECTED_FRAUD / COMPENSATED
    else aprobat
        V1->>B: creditOnce (rând + credit, idempotent)
        Note over V1,B: pivot · AFTER_CREDIT
        V1->>A: markCompleted (finalized_at)
        V1->>Au: record (retry, nu anulează transferul)
        Note over Au: AUDIT_FAILURE
        V1-->>C: COMPLETED
    end
```

## Problema fără coordonare

De ce e nevoie de compensare, idempotență și recuperare: fără ele, o bancă care nu răspunde după debit lasă banii „în aer”. Scenariul e demonstrat de `NoCoordinationDemoTest`, nu e o variantă de benchmark.

```mermaid
sequenceDiagram
    participant S as Serviciu fără coordonare
    participant A as Alpha Bank
    participant B as Beta Bank

    S->>A: debit 100 lei (commit)
    Note over A: sold −100
    S-xB: credit 100 lei
    Note over B: nu răspunde / aplicația cade
    Note over A,B: suma totală: 199.900 lei în loc de 200.000<br/>nimeni nu mai știe de cei 100 lei
```

## Componente

| Componentă | Pachet | Rol |
|---|---|---|
| `BankHealthController` | `web` | Expune `GET /health/banks`: 200 dacă ambele bănci răspund, 503 altfel. |
| `BankHealthService` | `bank` | Verifică conexiunea la fiecare bancă. |
| `AlphaBankDataSourceConfig`, `BetaBankDataSourceConfig` | `config` | Câte un DataSource, Flyway, EntityManagerFactory și TransactionManager pentru fiecare bancă. |
| `BankPersistenceFactory` | `config` | Construiește componentele de persistență comune celor două bănci. |
| `AlphaBankAccount`, `AlphaBankAccountRepository` | `bank.alpha` | Entitatea și repository-ul pentru conturile Alpha Bank. |
| `BetaBankAccount`, `BetaBankAccountRepository` | `bank.beta` | Entitatea și repository-ul pentru conturile Beta Bank. |
| `Banks`, `BankOperations` | `bank` | Operațiile pe solduri și pe rândurile din `transfer`, comune tuturor variantelor. |
| `LedgerService`, `InvariantChecker` | `bank` | Suma totală, banii în zbor, fereastra de inconsistență, regulile I1–I3. |
| `SimulatedFaultInjector` | `fault` | Erori cu probabilitate `app.fault.rate` pe `FaultPoint`-urile din `app.fault.points`, reproductibile cu `app.fault.seed`. |
| `SimulatedFraudCheckService` | `fraud` | Respinge peste `app.fraud.max-amount` sau din `app.fraud.blocked-accounts`; latență `app.fraud.latency-ms`; timeout prin `FRAUD_TIMEOUT`. |
| `SimulatedAuditService` | `audit` | Pică la `AUDIT_FAILURE`, altfel numără înregistrările. |
| `TransferMetrics` | `observability` | Învelește orice `TransferService` cu timer-ul `transfer.duration` (etichete `variant`, `outcome`). |
| `PlainTransferService` | `plain` | Implementarea baseline V1 a `TransferService`: flux procedural în 5 pași, verificare antifraudă, credit idempotent, compensare cu backoff. |
| `RecoveryJob` | `plain` | Job `@Scheduled` pentru reconcilierea transferurilor `PENDING` vechi pe baza existenței creditului în destinație. |
| `TransferController` | `web` | Expune `POST /transfers` și `GET /transfers/{idempotencyKey}`, învelind execuția cu `TransferMetrics`. |

## Decizii

- **Două baze de date separate.** Simulează două bănci independente. Fiecare bancă are propriul server PostgreSQL în `docker-compose.yml`.
- **Persistență separată per bancă.** Fiecare bancă are propriul `EntityManagerFactory`, `TransactionManager` și propriile migrații Flyway (`db/migration/alpha_bank`, `db/migration/beta_bank`), rulate automat la pornire. Auto-configurarea Spring Boot pentru DataSource, JPA și Flyway este dezactivată, fiindcă funcționează doar cu o singură bază.
- **Schema gestionată de Flyway.** Hibernate doar validează că entitățile corespund schemei (`ddl-auto=validate`).
- **Fără `@Primary`.** Orice tranzacție trebuie să numească explicit banca pe care lucrează, de exemplu `@Transactional(AlphaBankDataSourceConfig.TRANSACTION_MANAGER)`.
- **Prepared transactions activate.** Ambele servere PostgreSQL pornesc cu `max_prepared_transactions=100`, necesar pentru `PREPARE TRANSACTION`.
- **Detectare rapidă a unei bănci căzute.** Timeout-ul de conexiune Hikari este de 3 secunde, configurabil per bancă în `application.yml`.
- **Pool configurabil.** `maximum-pool-size` (implicit 10) se setează per bancă și e factor experimental.
- **Erori reproductibile.** Fiecare thread are propriul `Random`, cu seed din `app.fault.seed` și numele thread-ului. Cu același seed și aceleași nume de thread-uri, erorile apar în aceleași locuri. Benchmark-ul poate schimba configurația între rulări cu `reconfigure(...)`.
- **Erori de rețea cu Toxiproxy.** `docker-compose.chaos.yml` pune un proxy în fața fiecărei bănci (`15432`, `15433`, API pe `8474`). Aplicația trece prin el doar cu profilul `chaos`.

## Ce urmează

Varianta V1 Plain-Correct este complet implementată în pachetul `plain` și acoperită de teste de integrare, concurență și anduranță. Urmează variantele cu design patterns pentru M2 și M3 (Saga cu orchestrare în `orchestration`, Saga cu coreografie în `choreography`, 2PC în `twophase` și Workflow Temporal). Pentru anti-pattern-ul shared database va fi adăugat un al treilea container PostgreSQL.
