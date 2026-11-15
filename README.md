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

## Executare transferuri (REST API)

Swagger UI este disponibil la [http://localhost:8080/](http://localhost:8080/) (redirecționează către `/swagger-ui.html`), iar specificația OpenAPI la `/v3/api-docs`. De acolo se pot testa direct toate endpoint-urile de mai jos.

Aplicația expune endpoint-uri REST pentru crearea și interogarea transferurilor (folosind varianta activă din `app.web.variant`, implicit `V1`):

1. **Inițierea unui transfer între bănci:**

   ```bash
   curl -i -X POST http://localhost:8080/transfers \
     -H "Content-Type: application/json" \
     -H "Idempotency-Key: tx-1001" \
     -d '{"fromBank":"ALPHA","fromAccountId":1,"toBank":"BETA","toAccountId":2,"amount":100.00}'
   ```

   Pe Windows (PowerShell):

   ```powershell
   Invoke-RestMethod -Uri http://localhost:8080/transfers -Method POST `
     -Headers @{ "Idempotency-Key" = "tx-1001" } `
     -ContentType "application/json" `
     -Body '{"fromBank":"ALPHA","fromAccountId":1,"toBank":"BETA","toAccountId":2,"amount":100.00}'
   ```

   Răspuns așteptat (HTTP 200 OK):

   ```json
   {"transferId":1,"outcome":"COMPLETED","status":"COMPLETED","message":"Transfer completed"}
   ```

2. **Interogarea stării unui transfer după cheia unică de idempotență:**

   ```bash
   curl http://localhost:8080/transfers/tx-1001
   ```

   Răspuns așteptat (HTTP 200 OK):

   ```json
   {
     "id": 1,
     "idempotencyKey": "tx-1001",
     "fromAccount": 1,
     "toAccount": 2,
     "amount": 100.00,
     "status": "COMPLETED",
     "createdAt": "2026-11-14T12:00:00Z",
     "finalizedAt": "2026-11-14T12:00:00.050Z",
     "failureReason": null,
     "attemptCount": 0
   }
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

## Benchmark

Benchmark-ul măsoară varianta de transfer pe bazele reale și scrie rezultatele în `results/`. Metodologia e în [docs/experiments.md](docs/experiments.md), schema CSV în [docs/contracts.md](docs/contracts.md).

Toată campania M1 (aproximativ o oră; laptop la priză, aplicații închise):

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64   # dacă java -version nu arată 25
scripts/run-m1.sh
```

Scriptul pornește bazele, construiește jar-ul, rulează scenariile `baseline`, `fraud`, `pool` (5/10/20), `contention` și `sampling`, desenează graficele și regenerează grafurile de dependențe. Pentru o verificare rapidă (câteva minute): `scripts/run-m1.sh --quick`. Doar unele scenarii: `--scenarios "baseline sampling"`.

Rezultate:

| Ce | Unde |
|---|---|
| Un rând pe rulare | `results/runs.csv` |
| Latențe, ferestre de inconsistență, bani în zbor | `results/raw/` |
| Mașina, JVM-ul, `git_sha` | `results/environment.json` |
| Graficele G1–G4 (SVG și PNG) și tabelul cu cifre | `results/plots/`, `results/summary.md` |
| Grafurile de clase G10 | `analysis/graphs/packages.svg`, `analysis/graphs/v1-classes.svg` |

O singură configurație, direct din jar:

```bash
./mvnw -DskipTests package
java -jar target/saga-transfer-0.0.1-SNAPSHOT.jar --spring.profiles.active=benchmark \
  --app.benchmark.transfers=1000 --app.benchmark.failure-rates=0.1 --app.benchmark.threads=8 --app.benchmark.repetitions=1
```

Graficele cer Python 3 (`python3 -m venv .venv && .venv/bin/pip install -r analysis/requirements.txt`; scriptul le instalează singur), iar grafurile cer Graphviz (`sudo apt install graphviz`). Testele analizei: `.venv/bin/python -m unittest discover -s analysis`.

## Arhitectura

Diagrama și deciziile de arhitectură sunt în [docs/architecture.md](docs/architecture.md).

## Structura repository-ului

```
.
├── .github/workflows/build.yml         # build-ul automat în GitHub Actions
├── .github/workflows/smoke-benchmark.yml # benchmark mic, pornit manual
├── analysis/
│   ├── plot_m1.py, test_plot_m1.py     # graficele G1–G4 și summary.md, cu testele lor
│   ├── deps.sh                         # grafurile de dependențe G10 (jdeps + Graphviz)
│   └── graphs/                         # packages.svg, v1-classes.svg
├── scripts/run-m1.sh                   # campania de măsurători M1
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
    │   ├── benchmark/                  # BenchmarkRunner: matricea, sarcina, CSV-ul
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
