# Testy poprawek 1.0.3

Data: 6 września 2026. Minecraft Java 1.20.1, Forge 47.4.23, Java 17.0.20.1, IC2 Refactored 2.10.33-ex120. Mody opcjonalne: BuildCraft CE 8.0.13+1.20.1+forge, Forestry 2.10.2, Refined Storage 1.12.4, Patchouli 85-FORGE, z `R:\Codex-misc\Mods`.

## Poprawki

- Oryginalny adapter `ForgeStrictEnergyHandler` zachowuje właściciela capability. Po rzeczywistym, niezerowym odbiorze lub wydaniu FE oznacza jego załadowany chunk do zapisu przez istniejące `WorldUtils.saveChunk`. Symulacje, transfer zerowy/odrzucony i usunięty właściciel nie powodują zapisu. Nie wymusza ładowania chunków ani aktualizacji sąsiadów.
- RS 1.12.4 zapisuje energię kontrolera w osobnym `SavedData` sieci, a nie w NBT bloku. Mała opcjonalna klasa `RefinedStoragePersistence` używa wyłącznie publicznego API RS, aby oznaczyć te dane do zapisu. Transfer pozostaje oparty na `ForgeCapabilities.ENERGY`; kod produkcyjny nie odwołuje się do klas implementacyjnych RS.
- Nie zmieniono algorytmu rozdziału energii Mekanism, przeliczników, pakietów, NBT transmitterów ani limitów tierów. Nie dodano osobnego mostka Forestry ani listy maszyn FE.
- Minifikacja JSON-ów w Gradle jawnie odczytuje i zapisuje UTF-8. Wcześniejsze użycie domyślnego kodowania Windows uszkadzało strzałki, symbole statusu i tłumaczenia.
- GUI ma aktualny tytuł moda, kolumny wejściowe zależne od szerokości okna, etykiety wyznaczane z pozycji pól, pełną szerokość pól nadpisań, krótszą stronę Energy i zielony komunikat poprawnego zapisu. Zmiana rozmiaru zachowuje niezapisane wartości pól.

## Testy automatyczne

`scripts/test-integrations.ps1 -BuildJar`: **23/23** wymaganych testów przeszło po poprawce SavedData RS; log `build/tmp/integration-suite.log`, zakończenie testów 01:54:57.

`scripts/test-integrations.ps1 -WithoutOptionalMods`: **8/8** wymaganych testów przeszło po poprawce SavedData RS; log `build/tmp/base-suite.log`, zakończenie testów 01:59:35. Ten profil wyłącza BuildCraft i Forestry, ale nadal zawiera IC2 i Refined Storage; nie stanowi testu uruchomienia bez RS.

Zachowano wszystkie wcześniejsze próby Quarry, generatorów, BatBoxa, budżetu MJ, paliw, FE, transportu przedmiotów i płynów oraz pakietu Boost. Nowe regresje sprawdzają:

1. Rzeczywisty kontroler RS: wstrzyknięcie publicznego API; symulowany odbiór nie zmienia energii/chunka/SavedData, rzeczywisty odbiór 1000 FE oznacza chunk oraz dane sieci do zapisu. Odtworzenie niezależnego menedżera z zapisanych danych zachowuje energię. Refleksja do utworzenia niezależnej instancji występuje tylko w GameTeście.
2. Rzeczywisty Carpenter Forestry: analogiczna próba przez standardowy adapter capability, bez integracji zależnej od klas Forestry.
3. Przypadki brzegowe adaptera: symulacje, zły indeks kontenera, zerowy transfer, pełny/pusty bufor, rzeczywisty odbiór i wydawanie energii, capability bez właściciela blokowego oraz usunięty właściciel.

Sprawdzenie flagi niezapisanego chunka odbywa się po jej świadomym wyzerowaniu w jednorazowym świecie GameTest. Sam round-trip NBT nie zastępuje testu zapisu świata w kliencie.

## Kontrola GUI

Osobny klient developerski `runClient`, okno 1280 × 720, skala GUI 2, kopia świata testowego w `build/gui-client-1.0.3`. Sterowanie myszą i klawiaturą przez normalne okno gry; zrzuty w `build/gui-captures-1.0.3`. Komendy kreatywne służą do teleportacji i otrzymania przedmiotów, nie do wstrzykiwania energii. Paliwo wkładane w GUI generatora, odczyty wykonywane w GUI maszyn, zapis przez Save and Quit.

- Przeklikano wszystkie osiem zakładek konfiguracji. Tytuł, strzałki i symbole statusu są poprawne, pola i etykiety nie nachodzą na siebie ani stopkę w testowanym rozmiarze. Ujemny ręczny przelicznik został odrzucony z czerwonym komunikatem; przywrócono 2.5 EU/MJ, tryb AUTO oraz 4 FE/EU i sprawdzono zielone potwierdzenie zapisu. Zrzuty: [Energy](images/1.0.3/config-energy.png), [Compatibility](images/1.0.3/config-compatibility.png).
- Pierwsza poprawka oznaczająca tylko chunk zachowała 20 000 RF w Carpenterze, ale RS spadł z 20 000 do 0 FE po wczytaniu świata. Ten wynik ujawnił osobny magazyn danych RS. Wcześniejszy test tworzący ponownie tylko kontroler był niewystarczający, bo podłączał go do nadal istniejącej sieci; zastąpiono go niezależnym odtworzeniem SavedData.
- Końcowa próba po restarcie klienta z poprawką API RS: jedno wiadro lawy w generatorze podniosło Carpenter z 20 000 do 40 000 RF i RS z 0 do 20 000 FE. Paliwo wyczerpane, brak dalszego zasilania, brak receptury w Carpenterze, odczyt RS 0 FE/t. Save and Quit o 02:10:06, log potwierdza zapis wszystkich wymiarów; ponowne otwarcie świata, kontrola 02:12–02:13: **Carpenter nadal 40 000 RF, RS nadal 20 000 FE — PASS**. Zrzuty [Carpenter przed](images/1.0.3/carpenter-before-reload.png) / [po](images/1.0.3/carpenter-after-reload.png), [RS przed](images/1.0.3/rs-before-reload.png) / [po](images/1.0.3/rs-after-reload.png).
- Ponownie przytrzymano i puszczono Left Ctrl/Boost w świecie; klient pozostał aktywny, bez crasha.
- W niezapisanym polu FE/EU wpisano 4.1, przełączono F11 z okna 1280 × 720 do pełnego ekranu 1920 × 1080 i z powrotem. Wartość 4.1 zachowana w obu rozmiarach, układ strony Energy poprawny. Następnie przywrócono i zapisano 4.0 — PASS.

Nie jest to pełna certyfikacja wszystkich funkcji moda. Ta tura skupia się na zaobserwowanych błędach GUI i utracie energii; wcześniejsze scenariusze Quarry, itemów, płynów i Boost są pokryte regresjami automatycznymi. Nie powtórzono wszystkich tierów, ustawień, multiplayera ani wszystkich skal GUI. Klient GUI działał na developerskim classpath; sam reobfuskowany JAR przeszedł osobny audyt, nie uruchomienie w zewnętrznym launcherze.

Log zawiera również komunikaty dotyczące modeli BuildCrafta, jego niedostępnej wewnętrznej integracji `buildcraft.compat.ic2.Ic2Compat` oraz nieobecnego modelu Robita. Nie były przedmiotem tej poprawki; nie należy interpretować wyniku testów jako logu pozbawionego ostrzeżeń/błędów. Testowana integracja Quarry korzysta z mostka tego dodatku, nie z brakującej klasy compat BuildCrafta.

## Artefakt wydania

`build/libs/IC2-Universal-Energy-Extension-1.20.1-1.0.3.jar`

- `compileJava`, `jar` i `reobfJar` wykonane poprawnie.
- 12 222 938 bajtów; o 2171 bajtów więcej niż 1.0.2.
- SHA-256: `C879DC888A773A4D2F6E58DFAE5D35C6697D60E00A6EEF670885D114D433391B`.
- 3189 klas Java 17; jedyna nowa klasa produkcyjna to 25-wierszowe powiadomienie zapisu przez publiczne API RS. Nie tworzono dużego zbiorczego managera.
- Jeden mod `ic2universalenergy`, bez osadzonych modów, GameTestów ani diagnostyki testowej; zachowana licencja MIT Mekanism.
- Audyt wydania porównuje wszystkie wartości tłumaczeń EN/PL z plikami źródłowymi UTF-8. Porównanie przeszło.

Workflow `.github/workflows/release.yml` usunięto także z `main` w osobnym commicie `954802b`. Nie zmieniano źródeł upstream Mekanism ani IC2.
