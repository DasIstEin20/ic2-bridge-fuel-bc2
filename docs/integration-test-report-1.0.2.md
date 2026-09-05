# Testy IC2 Universal Energy Extension 1.0.2

Data: 2026-09-05. Testy wykonane autonomicznie na rzeczywistym serwerze Forge GameTest, nie na atrapach Quarry ani generatorów.

## Środowisko

Minecraft Java 1.20.1, Forge 47.4.23, Java 17.0.20.1. IC2 Refactored 2.10.33-ex120, BuildCraft CE 8.0.13+1.20.1+forge, Forestry 2.10.2, Refined Storage 1.12.4, Patchouli 85-FORGE. Opcjonalne mody pochodzą z `R:\Codex-misc\Mods`. Wszystkie cache, światy i wyniki testów znajdują się na R:.

## Wynik integracyjny: 20/20

| Obszar | Zweryfikowane zachowanie |
| --- | --- |
| Quarry + BatBox | Bezpośrednio i przez izolowany kabel miedziany IC2: ubywa EU i Quarry buduje ramę. W obu układach po 220 tickach pozostało 22 960 z 30 000 EU, powstało 89 bloków ramy. |
| Quarry + dwa generatory geotermalne | Każdy generator dostał 1000 mB lawy przez prawdziwe capability płynów. Układ bezpośredni i układ przez kabel IC2 wykonały pracę, każdy postawił 92 bloki ramy. |
| Błędna strona BatBoxa | Brak poboru EU, brak MJ i brak pracy Quarry. Wyjście BatBoxa musi być skierowane ku kablowi/Quarry. |
| Wątek EnergyNet | Zapotrzebowanie Quarry jest jednakowe na głównym i roboczym wątku: 60 000 EU. |
| Limit i przelicznik MJ | Dwa pakiety po 6 EU przy limicie 8 EU/t wspólnie przyjmują tylko 8 EU. Sprawdzone dwa przeliczniki: 5 i 2,5 EU/MJ, opróżnienie kolejki oraz odrzucanie energii po wyłączeniu mostka. |
| Forestry | BatBox → Universal Cable → Carpenter: 7936 FE w Carpenterze + 16 EU w BatBoxie = początkowe 2000 EU. Brak osobnego adaptera IC2 na maszynie Forestry. |
| Universal Cable + RS | Zasilanie kontrolera, bilans energii, pełny kontroler, brak odbiornika i zmiana FE/EU w czasie działania. |
| Paliwa | Wszystkie 9 profili BC zgadza się z jego rzeczywistym rejestrem paliw; paliwo IC2 jest obecne w rejestrze spalania BC. |
| Mechanical Pipe | Przesył wody pomiędzy zbiornikami BC; suma płynu w źródle, rurze i odbiorniku pozostaje równa 1000 mB. |
| Logistical Transporter | Przeniesienie wszystkich 16 sztabek żelaza między skrzyniami bez duplikacji. |
| Kontrola natywnego IC2 | BatBox → kabel miedziany IC2 → BatBox przesyła energię. |
| Granica API BC | Silnik Stirlinga BC 8.0.13 nie udostępnia `IMjPassiveProvider` i nie jest błędnie rejestrowany jako pasywne źródło EU. To test ograniczenia API, nie udanego transferu silnik BC → BatBox. |
| Crash klawisza Boost | Kodowanie oryginalnego `PacketKey` dla naciśnięcia i zwolnienia klawisza na zainicjalizowanym kanale Forge. |

Log potwierdzający: `build/tmp/integration-suite.log`, komunikat `All 20 required tests passed`.

Dodatkowo: **6/6 podstawowych GameTestów przeszło bez BuildCrafta, Forestry i Patchouli**, w osobnym świecie, z IC2 i Refined Storage. Log: `build/tmp/base-suite.log`, komunikat `All 6 required tests passed`. Poprawiono filtrowanie przestrzeni nazw, tak aby profil podstawowy nie uruchamiał testów wymagających nieobecnych modów.

## Znalezione i poprawione problemy

- Mostek MJ odpytywał świat z wątku EnergyNet: przed poprawką ten sam odbiornik zgłaszał 60 000 EU na serwerze i 0 EU w wątku roboczym. Obecnie capability i świat są obsługiwane na głównym wątku; worker używa synchronizowanego stanu i kolejki.
- Rejestracja paliw czytała konfigurację SERVER przed jej załadowaniem. Odbywa się teraz dopiero po załadowaniu konfiguracji świata.
- Stały profil `fuel_gaseous` dawał 150 EU/mB zamiast 37,5 EU/mB wynikających z dostarczonego BC. Gęstości energii są teraz pobierane z rejestru zainstalowanego BuildCrafta.
- Nieaktualne adaptery FE po usunięciu/wymianie maszyny są wyrejestrowywane z EnergyNet.
- Zgodnie z decyzją właściciela usunięto osobny mostek Forestry, jego opcję i element GUI. Forestry korzysta wyłącznie z oryginalnej sieci FE Universal Cable. Nie zmieniano logiki sieci ani zasobów transmitterów Mekanism.

Weryfikacja wykryła także błędy samych stanowisk testowych: zbyt małą strukturę, obracanie BatBoxa bez wywołania API klucza IC2 oraz stawianie nowych przewodów przed usunięciem poprzednich wpisów EnergyNet. Zostały poprawione w kodzie GameTest, nie obchodzone zmianą IC2. Pełne źródła IC2 2.10.35 służyły pomocniczo; zachowanie porównano z runtime 2.10.33. Żadnych źródeł IC2 nie edytowano.

## Wydanie

`build/libs/IC2-Universal-Energy-Extension-1.20.1-1.0.2.jar`

- `compileJava`, `jar` i `reobfJar`: wykonane poprawnie.
- Jeden mod: `ic2universalenergy`; bez osadzonych IC2, BC, Forestry, RS ani osobnego Transportera.
- 3188 klas, wszystkie jako bytecode Java 17.
- Bez GameTestów, pomocniczych klas diagnostycznych z testów i nowej struktury testowej.
- W porównaniu z JAR-em 1.0.1 nie dodano żadnej klasy produkcyjnej; usunięto cztery klasy mostka Forestry (klasę główną i trzy wewnętrzne).
- Zachowana licencja i atrybucja MIT Mekanism.
- Rozmiar: 12 220 767 bajtów, o 13 771 bajtów mniej od poprzedniego JAR-a 1.0.1.
- SHA-256: `80D32AF29499B826FE967794AD5B2B0AE030A5CC74E2B0F4D5690474AAB7CF46`.

## Odtworzenie i granice weryfikacji

`scripts/test-integrations.ps1 -BuildJar` uruchamia zestaw integracyjny i audyt wydania. `scripts/test-integrations.ps1 -WithoutOptionalMods` używa oddzielnego świata i nie ładuje BC, Forestry ani Patchouli. Sam JAR można sprawdzić przez `scripts/verify-release.ps1 -JarPath <plik.jar>`.

Nie jest to pełna certyfikacja wszystkich funkcji. Nie przeprowadzono sesji graficznego klienta, testów renderowania/GUI, wszystkich tierów i trybów Configuratora, Pressurized Tubes, Thermodynamic Conductors, kolorowania/złożonego routingu ani długotrwałego testu chunkloadingu i restartów sieci pod obciążeniem. Test Quarry potwierdza pobór energii i budowę ramy, nie ukończenie całego wykopu. Nie testowano całej paczki beta testera ani BC 8.0.17 / Forestry 2.11.0 z wcześniejszego crash reportu — dostarczony katalog zawiera inne wersje. Nie potwierdzono dodatniego transferu pasywnego MJ→EU na fizycznym źródle BC; dostarczone silniki nie oferują wymaganego API.
