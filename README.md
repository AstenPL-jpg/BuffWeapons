# BuffWeapons (Paper 26.2)

Każdy gracz dostaje losową broń z buffami (trójząb, włócznia, kusza, miecz, topór), a do tego ultimate.

## Budowanie

Potrzebna jest Java 25 (Paper 26.x tego wymaga) i Gradle:

    gradle build

Gotowy plik: `build/libs/BuffWeapons-1.0.0.jar`. Wrzuć go do `plugins/`.

## Komendy

- `/bronie` (alias `/weapons`) otwiera menu z bronią, buffami, ultimate, czasem i wymaganiami
- `/uzyjultimate` (alias `/ultimate`) aktywuje gotowe ultimate
- `/ultimatesettings` wybór sposobów aktywacji: Shift+F, Shift+Q, komenda
- `/bronieadmin set|reroll|ready|give|reload` (uprawnienie `buffweapons.admin`)

## Reroll

Broń losuje się przy pierwszym wejściu. Zmienić ją można tylko Kryształem Rerolla (PPM).
Receptura (4 diamenty w rogach, 4 perły Endu po bokach, gwiazda Netheru w środku) jest w `config.yml`.

Wszystkie liczby (czasy, wymagania, mnożniki, szanse) są w `config.yml`.
