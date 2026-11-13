# Transfer bancar distribuit, cu și fără design patterns

[![Build](https://github.com/nataliagorin/proiect-SASPS/actions/workflows/build.yml/badge.svg)](https://github.com/nataliagorin/proiect-SASPS/actions/workflows/build.yml)

Proiectul compară implementări **fără** și **cu** design patterns ale aceluiași transfer între două bănci diferite (Alpha Bank și Beta Bank). Între bănci nu există o tranzacție ACID comună, așa că transferul este o secvență de tranzacții locale: validare, debit, verificare antifraudă, credit, finalizare și audit, cu compensare la eșec.

Toate variantele sunt corecte: niciun ban pierdut sau creat, niciun transfer rămas `PENDING`. Le comparăm prin:

- **costul corectitudinii**: fereastra de inconsistență (cât stau banii „în zbor”), timpul de recuperare după erori sau crash;
- **performanță**: latență și throughput, sub rate de eșec și număr de thread-uri diferite;
- **structura codului**: metrici și grafuri de dependențe, plus costul adăugării unui pas nou.

| Variantă | Pattern-uri | Milestone |
|---|---|---|
| V1 Plain-Correct | niciunul (cod procedural) | M1 |
| V2 Saga orchestrată | Command, State | M2 |
| V3 Saga coreografiată | Observer | M2 |
| V4 Two-Phase Commit | referință arhitecturală | M2 |
| V5 Workflow Temporal | orchestrare durabilă | M2/M3 |
| V6 Saga + Circuit Breaker | Circuit Breaker | M3 |
| V7 Ledger cu Event Sourcing | Event Sourcing | M3 |
| A1–A4 Anti-pattern-uri | lipsa idempotenței, compensare fără retry, dual write, shared database | M3 |

Contractele comune sunt în [docs/contracts.md](docs/contracts.md), modelul de date în [docs/data-model.md](docs/data-model.md), iar metodologia experimentelor în [docs/experiments.md](docs/experiments.md).

## Pattern-uri folosite

Saga, Command, State, Observer, Circuit Breaker, Event Sourcing

## Echipa

- Ștefan Alexia
- Boboc Cristina
- Lepădatu Darius
- Gorin Natalia

## Cerințe

- **JDK 25**
- **Docker** (Docker Desktop pe Windows/macOS)

Maven nu trebuie instalat: proiectul folosește Maven Wrapper (`mvnw`).

## Build și teste

```bash
./mvnw clean install        # pe Windows: .\mvnw.cmd clean install
```

**Docker trebuie să fie pornit și pentru build**, nu doar pentru rulare: testele folosesc [Testcontainers](https://testcontainers.com/) și pornesc singure două containere PostgreSQL temporare. Testul de pornire verifică că aplicația pornește, că migrațiile Flyway rulează pe ambele bănci și că `GET /health/banks` răspunde 200. Nu e nevoie de `docker compose up` pentru teste.

Același build rulează automat în GitHub Actions la fiecare push în `main` și la fiecare Pull Request.

## Rulare

1. Pornește bazele de date:

   ```bash
   docker compose up -d
   ```

   Se pornesc două containere PostgreSQL 17: `saga-alpha-bank` pe portul `5432` și `saga-beta-bank` pe portul `5433`.

2. Pornește aplicația:

   ```bash
   ./mvnw spring-boot:run
   ```

   Pe Windows (PowerShell sau cmd):

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   La pornire, Flyway rulează automat migrațiile pe ambele baze. Aplicația ascultă pe portul `8080`.

3. Oprire:

   ```bash
   docker compose down      # oprește containerele, păstrează datele
   docker compose down -v   # oprește containerele și șterge datele
   ```

> Dacă aplicația nu se poate conecta la baze (de exemplu `password authentication failed for user "saga"`), verifică să nu mai ruleze alt PostgreSQL pe porturile `5432` sau `5433`, local sau în WSL.

## Verificare

Starea containerelor (ambele trebuie să fie `healthy`):

```bash
docker compose ps
```

Conexiunea aplicației la ambele bănci:

```bash
curl http://localhost:8080/health/banks
```

Pe Windows, în PowerShell, folosește `curl.exe` sau `Invoke-RestMethod http://localhost:8080/health/banks`.

Răspuns așteptat (HTTP 200):

```json
{"status":"UP","banks":{"alpha_bank":{"status":"UP"},"beta_bank":{"status":"UP"}}}
```

Dacă o bancă nu răspunde, endpoint-ul întoarce HTTP 503 în aproximativ 3 secunde, cu `"status":"DOWN"` și mesajul de eroare pentru banca respectivă. Poți încerca cu `docker compose stop beta-bank`, apoi `docker compose start beta-bank`.

Migrațiile aplicate:

```bash
docker exec saga-alpha-bank psql -U saga -d alpha_bank -c "select version, description, success from flyway_schema_history"
docker exec saga-beta-bank psql -U saga -d beta_bank -c "select version, description, success from flyway_schema_history"
```

## Erori simulate

Implicit nu pică nimic. Erorile din cod se pornesc din proprietăți, de exemplu:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.fault.rate=0.1 --app.fault.points=AFTER_DEBIT --app.fraud.max-amount=500"
```

Erorile de rețea (latență, bancă căzută) trec prin Toxiproxy:

```bash
docker compose -f docker-compose.yml -f docker-compose.chaos.yml up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=chaos
```

Beta Bank „cade” și revine prin API-ul Toxiproxy:

```bash
curl -X POST localhost:8474/proxies/beta_bank -d '{"enabled":false}'
curl -X POST localhost:8474/proxies/beta_bank -d '{"enabled":true}'
```

Metricile sunt la `GET /actuator/metrics/transfer.duration`.

## Arhitectura

Diagrama și deciziile de arhitectură sunt în [docs/architecture.md](docs/architecture.md).

## Structura repository-ului

```
.
├── .github/workflows/build.yml         # build-ul automat în GitHub Actions
├── .editorconfig                       # reguli comune de formatare
├── docker-compose.yml                  # cele două baze PostgreSQL (alpha_bank, beta_bank)
├── docker-compose.chaos.yml            # Toxiproxy în fața băncilor
├── docker/toxiproxy.json               # proxy-urile Toxiproxy
├── docs/
│   ├── architecture.md                 # diagrama și deciziile de arhitectură
│   ├── contracts.md                    # contractele comune tuturor variantelor
│   ├── data-model.md                   # tabelele și operațiile pe bănci
│   └── experiments.md                  # ipoteze, factori, metrici, protocol
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/               # Maven Wrapper
├── src/test/java/ro/saga/transfer/
│   └── ApplicationStartupTest.java     # testul de pornire cu Testcontainers
└── src/main/
    ├── java/ro/saga/transfer/
    │   ├── SagaTransferApplication.java
    │   ├── audit/                      # audit simulat
    │   ├── bank/                       # operațiile pe bănci, LedgerService, invarianți
    │   │   ├── alpha/                  # entitatea și repository-ul Alpha Bank
    │   │   └── beta/                   # entitatea și repository-ul Beta Bank
    │   ├── benchmark/                  # măsurătorile (urmează)
    │   ├── choreography/               # Saga cu coreografie (urmează)
    │   ├── common/                     # contractele comune variantelor de transfer
    │   ├── config/                     # DataSource, JPA, Flyway
    │   ├── fault/                      # FaultInjector simulat
    │   ├── fraud/                      # antifraudă simulată
    │   ├── observability/              # timer-ul transfer.duration
    │   ├── orchestration/              # Saga cu orchestrare (urmează)
    │   ├── plain/                      # V1 Plain-Correct (urmează)
    │   ├── twophase/                   # Two-phase commit (urmează)
    │   └── web/                        # controller-ele REST
    └── resources/
        ├── application.yml             # conexiunile la bănci, erori simulate
        ├── application-benchmark.yml   # profilul benchmark
        ├── application-chaos.yml       # conexiuni prin Toxiproxy
        └── db/migration/
            ├── alpha_bank/             # migrațiile Flyway pentru Alpha Bank
            └── beta_bank/              # migrațiile Flyway pentru Beta Bank
```

Pachetele sunt organizate pe funcționalități, nu pe straturi: fiecare bancă și fiecare variantă de transfer are propriul pachet.
