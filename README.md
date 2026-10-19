# Saga Pattern: Transfer bancar

Proiectul este un studiu comparativ al pattern-ului **Saga**, aplicat unui scenariu de transfer bancar între două bănci diferite. Într-un astfel de transfer nu putem folosi o singură tranzacție ACID, așa că consistența este asigurată printr-o secvență de tranzacții locale, fiecare cu o compensare asociată în caz de eșec.

Sunt implementate și comparate:

- Saga cu **orchestrare** (coordonator central)
- Saga cu **coreografie** (comunicare prin evenimente)
- **Two-phase commit (2PC)**, ca punct de referință

Pentru fiecare variantă sunt măsurate latența, throughput-ul, comportamentul la eșecuri parțiale și corectitudinea (suma totală a banilor din sistem rămâne constantă). Proiectul ilustrează și o serie de **anti-pattern-uri** (lipsa idempotenței, dual write, compensări fără retry, bază de date partajată) și efectele lor.

## Pattern-uri folosite

Saga, Command, State, Observer, Circuit Breaker, Event Sourcing

## Echipa

- Ștefan Alexia
- Boboc Cristina
- Lepădatu Darius
- Gorin Natalia

## Structura repository-ului

TBD
