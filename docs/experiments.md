# Experimente

Ce măsurăm, cum și de ce. Documentul e baza pentru `BenchmarkRunner`, `application-benchmark.yml` și graficele din `analysis/`. Contractele și schema CSV sunt în [contracts.md](contracts.md), iar tabelele în [data-model.md](data-model.md).

## Întrebarea

Ce aduc design pattern-urile într-un transfer distribuit, dacă toate variantele sunt la fel de corecte? Așteptăm diferențe în fereastra de inconsistență, comportamentul sub stres, recuperare și structura codului, nu în banii pierduți.

## Poarta de acceptare

O variantă intră în benchmark doar dacă, la fiecare rulare, trece regulile I1–I6 din [contracts.md](contracts.md):

| Regulă | Cum se verifică după rulare |
|---|---|
| I1 suma totală = 200.000 lei | `InvariantChecker.check().moneyLost() == 0` |
| I2 niciun sold negativ | `negativeBalances() == 0` |
| I3 niciun `PENDING` după quiescence | `pendingTransfers() == 0` |
| I4 idempotență | test separat: aceeași cheie de două ori (`TransferRecordsTest`, apoi testele de contract din M2) |
| I5 recuperare după restart | scenariul crash (M2) |
| I6 reproductibilitate | același seed → aceleași perechi de conturi, sume și erori injectate |

O rulare care pică I1–I3 se scrie în CSV (`money_lost`, `pending_left`), dar nu intră în grafice.

## Ipoteze

Ambele rezultate, confirmare sau infirmare, sunt valide.

| ID | Ipoteză | Milestone |
|---|---|---|
| H1 | Fereastra de inconsistență crește cu rata de eșec. În V1 e dominată de intervalul `RecoveryJob` pentru cazurile nerezolvate imediat; în V2 și V5 de politica de retry. | M1 (V1), M2 |
| H2 | V1–V5 trec poarta de acceptare la toate ratele de eșec și toate numerele de thread-uri. | M1, M2 |
| H3 | Saga orchestrată adaugă sub ~10% latență față de V1. Coreografia are latență mai mare. Temporal are cea mai mare latență, dar cea mai bună durabilitate. | M2 |
| H4 | 2PC are cel mai mic throughput la contenție mare și multe thread-uri și se comportă cel mai prost când o bancă cade. | M2 |
| H5 | Circuit Breaker reduce p95 sub outage cu ordine de mărime (ms în loc de 3 s). | M3 |
| H6 | Adăugarea pasului de comision atinge mai puține clase și linii în variantele cu pattern-uri decât în V1. | M3 |
| H7 | După crash, V1 depinde de `RecoveryJob`, Temporal recuperează automat. | M2, M3 |
| H8 | Variantele cu pattern-uri au mai multe clase și CBO mai mare, dar complexitate ciclomatică mai mică per metodă. | M2, M3 |
| H9 | Costul unui pas adăugat rămâne aproximativ constant în V2/V3/V5 și crește în V1. | M3 |

## Factori

| Factor | Valori | Proprietate |
|---|---|---|
| Variantă | V1…V7, A1…A4 | `app.benchmark.variants` |
| Rata de eșec tehnic | 0, 5, 10, 20, 30% | `app.benchmark.failure-rates` → `app.fault.rate` |
| Rata de respingere antifraudă | 0, 5% | `app.fraud.max-amount`, `app.fraud.blocked-accounts` |
| Thread-uri | 1, 2, 4, 8, 16 | `app.benchmark.threads` |
| Pool Hikari per bancă | 5, 10, 20 (subset de scenarii) | `app.datasource.*-bank.hikari.maximum-pool-size` |
| Contenție | 100 conturi uniform; 10 conturi Zipf | scenariu |
| Latență injectată pe Beta / antifraudă | 0, 50, 200, 500 ms | Toxiproxy / `app.fraud.latency-ms` |
| Outage Beta | 0 s, 10 s | Toxiproxy |

Variabile de control, identice pentru toate variantele: `BankOperations`, tabelele, serviciul antifraudă simulat, `FaultInjector`, `BenchmarkRunner`, seed-ul, datele inițiale (100 de conturi × 1000 lei în fiecare bancă).

Transferurile merg în ambele direcții (Alpha → Beta și Beta → Alpha, 50/50), ca să nu se golească o bancă. Sumele sunt mici față de sold (1–50 lei), ca `REJECTED_INSUFFICIENT_FUNDS` să fie rar și să nu țină de experiment. Regula antifraudă finală se decide în echipă (secțiunea 13 din plan).

## Metrici

| Metrică | Cum se obține |
|---|---|
| Poarta de corectitudine | `InvariantChecker` după quiescence: `money_lost`, `pending_left`, solduri negative |
| Fereastra de inconsistență | `LedgerService.inconsistencyWindowsMillis()` (`finalized_at - created_at` pe rândurile din banca sursă): medie, p95, max |
| Suma maximă în zbor | eșantionare `LedgerService.getInFlightMoney()` la ~100 ms în timpul rulării; maximul eșantioanelor. Nu se poate calcula după rulare. |
| Rezultate | numărul de `TransferOutcome` întoarse de `TransferService` |
| Latență | `System.nanoTime()` în jurul fiecărui `transfer(...)`: medie, p50, p95, p99, max |
| Throughput | transferuri măsurate / durata fazei de măsurare (fără warmup) |
| Reziliență | timp până la consistență după outage sau crash; procentul de transferuri `COMPLETED` în timpul outage-ului |
| Resurse | CPU, heap, conexiuni active din pool (Micrometer / JFR) |
| Structură | LOC, clase, complexitate ciclomatică și cognitivă, CBO, LCOM, fan-in/fan-out, fișiere și linii atinse la pasul de comision |

Eșecurile de business (`REJECTED_INSUFFICIENT_FUNDS`, `REJECTED_FRAUD`) se numără separat de cele tehnice (`FAILED_TECHNICAL`, `COMPENSATED`).

## Protocol

Pentru fiecare configurație (variantă × rată × thread-uri × …) și fiecare repetare:

1. `LedgerService.resetData()`.
2. **Warmup:** primele `app.benchmark.warmup-transfers` transferuri (implicit 200) se rulează și nu se măsoară. Apoi din nou `resetData()`.
3. **Măsurare:** `app.benchmark.transfers` transferuri (implicit 10.000), împărțite pe thread-uri. Fiecare thread ia perechi de conturi și sume dintr-un `Random` cu seed derivat din `app.benchmark.seed`, repetare și index de thread.
4. **Quiescence:** se așteaptă până `LedgerService.countPendingTransfers() == 0`, cel mult `2 × app.recovery.stale-after-ms + app.recovery.interval-ms`. Dacă nu se ajunge acolo, se notează `pending_left`.
5. Verificare invarianți, apoi un rând în `results/*.csv` și latențele brute în `results/raw/`.

Reguli:

- Cel puțin 3 repetări, 5 pentru scenariile zgomotoase (latență injectată, outage). Se raportează media ± deviația standard.
- p95 nu se mediază. Se calculează pe latențele brute unite din toate repetările; separat se raportează media p95-urilor cu deviația ei.
- Seed-ul fix intră în CSV, ca orice rulare să poată fi refăcută.
- Același laptop, la priză, aplicații închise, `-Xms` = `-Xmx`. Hardware-ul, JVM-ul, flag-urile, versiunea Docker și `git_sha` se scriu în `results/environment.json`.
- Thread-uri de platformă. Thread-urile virtuale sunt doar un experiment opțional.
- Benchmark-ul nu rulează în CI și nici la `mvnw install`. Pornește doar cu profilul `benchmark` (`scripts/run-m1.sh`, apoi `run-all.sh`).

## Scenarii

| Scenariu | Descriere | Milestone |
|---|---|---|
| `baseline` | 100 conturi uniform, fără latență injectată, rate de eșec 0–30% | M1 |
| `pool` | ca `baseline`, cu pool 5/10/20 | M1 |
| `contention` | 10 conturi fierbinți, distribuție Zipf | M1 |
| `fraud` | 5% respingeri antifraudă | M1 |
| `ramp` | thread-uri crescute până la saturație | M2 |
| `soak` | 10–15 min sarcină constantă: scurgeri, `PENDING` rămase, degradare | M2 |
| `spike` | salt de la 1 la 16 thread-uri (Gatling) | M2 |
| `outage` | Beta oprită 10 s în mijlocul sarcinii (Toxiproxy) | M2 |
| `latency` | 50/200/500 ms pe Beta sau pe antifraudă | M2 |
| `crash` | `kill -9` și restart în mijlocul sarcinii | M2 |

### Ce rulăm în M1

În M1 există doar V1, așa că se compară configurații între ele:

- `baseline` cu toate ratele de eșec × 1, 2, 4, 8, 16 thread-uri (G1–G3);
- `pool` 5/10/20 la 8 și 16 thread-uri;
- `contention` la 0% și 10% eșec;
- o rulare cu eșantionarea banilor în zbor pentru G4.

Estimare: 5 rate × 5 thread-uri × 3 repetări = 75 de rulări de 10–30 s, sub o oră.

## Grafice

| ID | Grafic | Milestone |
|---|---|---|
| G1 | Fereastra de inconsistență (medie și p95) vs rata de eșec | M1, M2 |
| G2 | Latență medie, p50, p95, p99 pe variantă (bare + ECDF) | M1, M2 |
| G3 | Throughput vs număr de thread-uri | M1, M2 |
| G4 | Solduri și bani în zbor în timp | M1 |
| G5 | Distribuția rezultatelor, bare stivuite | M2 |
| G6 | Heatmap throughput (thread-uri × rată de eșec) pe variantă | M2 |
| G7 | Recuperarea după outage/crash | M2, M3 |
| G8 | Throughput vs dimensiunea pool-ului | M2 |
| G9 | Metrici structurale pe variantă | M2, M3 |
| G10 | Grafuri de dependențe, câte unul pe variantă | M2, M3 |
| G11 | Costul de extensibilitate (pasul comision) | M3 |
| G12 | Latență sub outage cu și fără Circuit Breaker | M3 |
| G13 | Anti-pattern-uri: bani pierduți, duplicate, `PENDING` rămase | M3 |

Toate graficele au bare de eroare din repetări. Se generează cu `analysis/plot_m1.py` (pandas + matplotlib) din `results/`.

## Limite cunoscute

- Un singur laptop, Postgres local fără latență reală de rețea.
- JIT și GC influențează primele secunde, de aceea există warmup.
- Sarcina e sintetică, iar băncile și antifrauda sunt simulate.
- Pool-ul Hikari (implicit 10, timeout 3 s) poate limita throughput-ul peste 8 thread-uri, de aceea e factor experimental.
