# Modelul de date

Fiecare bancă are propria bază PostgreSQL, cu aceleași tabele. Schema vine din migrațiile Flyway din `src/main/resources/db/migration/{alpha_bank,beta_bank}`.

## Tabele

| Tabel | Coloane |
|---|---|
| `account` | `id`, `owner`, `balance` (nu poate fi negativ) |
| `transfer` | `id`, `from_account`, `to_account`, `amount`, `status`, `created_at`, `idempotency_key` (unică), `failure_reason`, `finalized_at`, `attempt_count` |

Migrații: `V1` schema, `V2` date inițiale (100 de conturi cu câte 1000 lei în fiecare bancă, 200.000 lei în total, ID-uri 1–100), `V3` coloanele pentru idempotență și fereastra de inconsistență. `status` poate fi doar `PENDING`, `COMPLETED`, `FAILED` sau `COMPENSATED` (verificat și în bază).

## Convenții pentru variantele de transfer

1. Rândul din banca sursă se inserează `PENDING` în aceeași tranzacție locală cu debitul.
2. În banca destinație se inserează un rând cu aceeași `idempotency_key`, în aceeași tranzacție cu creditul. Acest rând nu e niciodată `PENDING`, de obicei `COMPLETED`. Cheia unică face creditul idempotent.
3. `finalized_at` se setează doar pe rândul din banca sursă, când ajunge în stare terminală.
4. `created_at` îl pune baza de date; aplicația nu îl scrie.

`LedgerService` se bazează pe aceste convenții pentru banii în zbor și pentru fereastra de inconsistență.

## Cod

| Clasă | Rol |
|---|---|
| `AlphaBankOperations`, `BetaBankOperations` | `debit`, `credit`, `refund`, `balanceOf`, fiecare într-o tranzacție locală a băncii lui. Interfața comună este `BankOperations`. |
| `AlphaBankTransfer`, `BetaBankTransfer` + repository-uri | Entitățile tabelului `transfer` (`findByIdempotencyKey`, `findByStatus`). |
| `LedgerService` | `getTotalMoney()`, `getInFlightMoney()`, `resetData()`, `inconsistencyWindowsMillis()` |
| `InvariantChecker` | Verifică regulile 1–3 din `docs/contracts.md`; întoarce un `InvariantReport` |

Debit-ul este un singur `UPDATE ... WHERE balance >= :amount`, deci verificarea și modificarea sunt atomice. Un debit peste sold aruncă `InsufficientFundsException`, iar un cont inexistent `AccountNotFoundException`. Nu se citește soldul în aplicație ca să se modifice apoi, pentru că sub concurență s-ar pierde modificări.

## Rulare

```bash
./mvnw clean install
```

Testele pornesc singure două baze temporare (Docker trebuie să fie pornit). `BankOperationsTest` conține testul de concurență: 16 thread-uri × 1000 de operații pe aceleași conturi, după care suma totală rămâne 200.000 lei și niciun sold nu e negativ.
