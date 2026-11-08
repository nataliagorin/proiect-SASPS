# Saga Pattern: Transfer bancar

Proiectul este un studiu comparativ al pattern-ului **Saga**, aplicat unui scenariu de transfer bancar între două bănci diferite. Într-un astfel de transfer nu putem folosi o singură tranzacție ACID, așa că consistența este asigurată printr-o secvență de tranzacții locale, fiecare cu o compensare asociată în caz de eșec.

Vor fi implementate și comparate:

- varianta **naivă**, fără garanții de consistență distribuită
- Saga cu **orchestrare** (coordonator central)
- Saga cu **coreografie** (comunicare prin evenimente)
- **Two-phase commit (2PC)**, ca punct de referință

Pentru fiecare variantă vor fi măsurate latența, throughput-ul, comportamentul la eșecuri parțiale și corectitudinea (suma totală a banilor din sistem rămâne constantă). Proiectul va ilustra și o serie de **anti-pattern-uri** (lipsa idempotenței, dual write, compensări fără retry, bază de date partajată) și efectele lor.

În acest moment este gata infrastructura: aplicația Spring Boot, cele două bănci (Alpha Bank și Beta Bank) cu baze de date PostgreSQL separate, migrațiile Flyway și un endpoint de verificare a conexiunilor.

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

## Arhitectura

Diagrama și deciziile de arhitectură sunt în [docs/architecture.md](docs/architecture.md).

## Structura repository-ului

```
.
├── docker-compose.yml                  # cele două baze PostgreSQL (alpha_bank, beta_bank)
├── docs/
│   └── architecture.md                 # diagrama și deciziile de arhitectură
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/               # Maven Wrapper
└── src/main/
    ├── java/ro/saga/transfer/
    │   ├── SagaTransferApplication.java
    │   ├── bank/                       # serviciile comune băncilor (verificarea conexiunilor)
    │   │   ├── alpha/                  # entitatea și repository-ul Alpha Bank
    │   │   └── beta/                   # entitatea și repository-ul Beta Bank
    │   ├── benchmark/                  # măsurătorile (urmează)
    │   ├── choreography/               # Saga cu coreografie (urmează)
    │   ├── common/                     # tipurile comune variantelor de transfer (urmează)
    │   ├── config/                     # DataSource, JPA și Flyway pentru fiecare bancă
    │   ├── naive/                      # varianta naivă (urmează)
    │   ├── orchestration/              # Saga cu orchestrare (urmează)
    │   ├── twophase/                   # Two-phase commit (urmează)
    │   └── web/                        # controller-ele REST
    └── resources/
        ├── application.yml             # conexiunile la cele două bănci
        └── db/migration/
            ├── alpha_bank/             # migrațiile Flyway pentru Alpha Bank
            └── beta_bank/              # migrațiile Flyway pentru Beta Bank
```

Pachetele sunt organizate pe funcționalități, nu pe straturi: fiecare bancă și fiecare variantă de transfer are propriul pachet.
