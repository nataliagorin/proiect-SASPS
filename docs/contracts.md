# Contracte comune

Tipurile din `ro.saga.transfer.common` sunt folosite de toate variantele de transfer, de benchmark și de partea web. Sunt înghețate la merge-ul PR-ului `feature/contracts`; orice schimbare ulterioară se discută în echipă, pentru că strică munca celorlalți.

## Tipuri

| Tip | Rol |
|---|---|
| `TransferService` | `TransferResult transfer(TransferRequest)`. Singura interfață pe care o folosește benchmark-ul. |
| `TransferRequest` | `idempotencyKey`, banca și contul sursă, banca și contul destinație, `amount`. Validează la construire: cheie nenulă, bănci diferite, sumă pozitivă cu cel mult 2 zecimale. |
| `TransferResult` | `transferId`, `outcome`, `status`, `message` (în engleză). `isSuccess()` este adevărat doar pentru `COMPLETED`. |
| `TransferOutcome` | `COMPLETED`, `REJECTED_INSUFFICIENT_FUNDS`, `REJECTED_FRAUD`, `FAILED_TECHNICAL`, `COMPENSATED`. |
| `TransferStatus` | `PENDING`, `COMPLETED`, `FAILED`, `COMPENSATED`. Se salvează ca text în coloana `transfer.status`. |
| `Bank` | `ALPHA`, `BETA` (`displayName()`: Alpha Bank, Beta Bank). |
| `FaultPoint`, `FaultInjector` | Locurile unde se injectează erori și sursa lor comună, cu probabilitate și seed. `FaultInjector.NONE` nu pică niciodată. |
| `BankUnavailableException` | Aruncată de injector și de serviciile simulate. Toate variantele o tratează ca eșec tehnic. |
| `FraudCheckService`, `FraudDecision` | Verificare antifraudă simulată: `APPROVED` sau `REJECTED`; poate arunca `BankUnavailableException` la timeout. |
| `AuditService` | Audit/notificare la finalul transferului. Dacă pică, se reia; nu anulează transferul. |

## Fluxul și locurile de eșec

| Pas | Ce face | Tip | `FaultPoint` |
|---|---|---|---|
| 1 | Validare, verificare `idempotencyKey` | fără efect | – |
| 2 | Debit în banca sursă, transfer `PENDING` | compensabil | `AFTER_DEBIT` |
| 3 | Verificare antifraudă | fără efect | `FRAUD_TIMEOUT` |
| 4 | Credit în banca destinație (idempotent) | pivot | `AFTER_CREDIT` |
| 5 | `COMPLETED` + audit/notificare | reluabil | `AUDIT_FAILURE` |
| – | Refund (compensare) | – | `DURING_COMPENSATION` |

## Rezultat și stare

| Situație | `outcome` | `status` la întoarcerea apelului |
|---|---|---|
| Totul a mers | `COMPLETED` | `COMPLETED` |
| Fonduri insuficiente, nimic nu s-a schimbat | `REJECTED_INSUFFICIENT_FUNDS` | `FAILED` |
| Antifraudă a respins, debitul a fost returnat | `REJECTED_FRAUD` | `COMPENSATED` |
| Eroare tehnică, debitul a fost returnat | `COMPENSATED` | `COMPENSATED` |
| Eroare tehnică înainte de orice efect | `FAILED_TECHNICAL` | `FAILED` |
| Eroare tehnică, compensarea nu a reușit încă | `FAILED_TECHNICAL` | `PENDING` (îl preia recuperarea) |

## Reguli de acceptare pentru orice variantă

1. După oprirea sarcinii, suma totală din cele două bănci este egală cu cea inițială (200.000 lei).
2. Niciun sold negativ.
3. După ce sistemul se liniștește, niciun transfer nu rămâne `PENDING`.
4. Aceeași `idempotencyKey` trimisă de două ori are efectul unei singure cereri.
5. După repornirea aplicației în mijlocul sarcinii, recuperarea aduce sistemul în starea 1–3.
6. Cu același seed, rezultatele sunt reproductibile.

## Fereastra de inconsistență

Timpul dintre `created_at` (commit-ul debitului) și `finalized_at` (starea terminală) al unui transfer, plus suma maximă aflată simultan în zbor. Se calculează din baza de date după rulare, la fel pentru toate variantele.

## Proprietăți de configurare (nume convenite, fiecare PR le adaugă pe ale lui)

| Proprietate | Cine o adaugă | Sens |
|---|---|---|
| `app.fault.seed`, `app.fault.rate`, `app.fault.points` | `feature/fault-injection` | Seed, probabilitate (0–1) și punctele active |
| `app.fraud.latency-ms`, `app.fraud.max-amount`, `app.fraud.blocked-accounts` | `feature/fault-injection` | Latența simulată și regula determinist de respingere |
| `app.datasource.alpha-bank.hikari.maximum-pool-size`, `app.datasource.beta-bank.hikari.maximum-pool-size` | `feature/benchmark-profile` | Dimensiunea pool-ului per bancă |
| `app.compensation.max-attempts`, `app.compensation.backoff-ms` | varianta V1 | Retry la compensare |
| `app.recovery.interval-ms`, `app.recovery.stale-after-ms` | varianta V1 | Frecvența și pragul jobului de recuperare |
| `app.benchmark.variants`, `.transfers`, `.warmup-transfers`, `.failure-rates`, `.threads`, `.repetitions`, `.seed`, `.output-dir` | `feature/benchmark` | Matricea de rulare, în `application-benchmark.yml` |

## Schema CSV a benchmark-ului

Un rând pe configurație și repetare, în `results/`. Timpii sunt în milisecunde.

```
run_id, git_sha, timestamp, variant, scenario, repetition, seed, failure_rate, threads, pool_size,
transfers, completed, rejected_insufficient, rejected_fraud, failed_technical, compensated, pending_left,
money_before, money_after, money_lost,
inflight_mean_ms, inflight_p95_ms, inflight_max_money,
latency_mean_ms, latency_p50_ms, latency_p95_ms, latency_p99_ms,
duration_s, throughput_tps
```

Latențele brute ale fiecărui transfer se salvează separat în `results/raw/`, iar p95 se calculează pe setul unit al repetărilor, nu ca medie de p95.
