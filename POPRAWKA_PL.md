# ReAppzuku 1.8.7 — poprawka wykrywania pierwszego planu

Baza: gree1d/ReAppzuku, tag `1.8.7`, commit `f1c622f`.
To nieoficjalna poprawka źródeł. Archiwum nie zawiera zbudowanego APK.

## Co zostało poprawione

Auto-Kill uznawał dowolną wzmiankę o pakiecie w `dumpsys activity activities`
za dowód działania na pierwszym planie. Dlatego Allegro pozostawione w historii
aktywności mogło być pomijane jako `SKIP (foreground): pl.allegro`.

Nowy filtr odczytuje pola aktywnych i widocznych aktywności. Chroni również
widoczne aplikacje w podzielonym ekranie i obraz w obrazie. Gdy urządzenie jest
nieinteraktywne, stary wpis RESUMED nie blokuje zatrzymania ostatnio używanej
aplikacji. Stan jest pobierany po zbieraniu danych pamięci, bliżej chwili wyboru
aplikacji do zatrzymania. Zmiana stanu ekranu przed rozpoczęciem zatrzymywania
powoduje pominięcie tego cyklu.

Przy włączonym ekranie pusty, błędny lub nierozpoznany wynik diagnostyki powoduje
pominięcie cyklu. Reguły blacklisty, listy ukrytych, aplikacji chronionych oraz
wyjątki harmonogramu nadal obowiązują. Ręczne zatrzymywanie zachowuje dotychczasowy
sposób działania. Poprawka nie zmienia metody `force-stop` na `disable` ani `suspend`.

Pierwsza kompilacja w GitHub Actions wykryła także błędną deklarację klasy
`ShizukuUserServiceImpl` jako usługi Androida. Usunięto tę deklarację: klasę Binder
uruchamia bezpośrednio Shizuku. Jej publiczny konstruktor jest teraz jawnie
chroniony przed usunięciem przez R8. Świeża kopia repozytorium uruchamia kontrolę
lint bez odwołania do nieistniejącej bazy wyciszonych ostrzeżeń.

## Co rzeczywiście sprawdzono

- Skompilowano produkcyjną klasę `ForegroundAppDetector` i uruchomiono 18 testów
  regresji na JVM 17; wszystkie przeszły.
- Przypadki obejmują Allegro w tle, faktyczny pierwszy plan, wygaszony ekran,
  podzielony ekran, PiP, starsze i nowsze warianty pól oraz niepełne dane.
- Dane testowe są syntetyczne; nie zawierają zrzutu z telefonu użytkownika.
- Lokalne budowanie APK było niedostępne, ponieważ mechanizm zatwierdzania sieci
  zatrzymał pobieranie Android SDK i Gradle. Budowanie przeniesiono do GitHub Actions.
- Wynik kompilacji, podpisu APK oraz testów dla konkretnego commita należy sprawdzić
  w workflow **Build ReAppzuku Fix**. Pierwszy przebieg przeszedł kompilację Java
  i Kotlin, ale zatrzymał się na opisanych wyżej problemach manifestu i lint.
- Nie testowano działania aplikacji na telefonie. Udana kompilacja nie zastępuje
  próby zatrzymywania aplikacji na konkretnym ROM-ie.

`dumpsys` nie jest stabilnym publicznym API Androida. Inny format w danym ROM-ie
może wymagać dostosowania parsera. Odczyt stanu oraz polecenie zatrzymania nie są
jedną atomową operacją systemu. Poprawka nie rozwiązuje osobnego, istniejącego
problemu dokładności statystyk udanych zatrzymań ani problemów usługi w tle.

## Zbudowanie na Windows

1. Rozpakuj całe archiwum do zwykłego folderu, np. `C:\ReAppzukuFix`.
2. Zainstaluj Android Studio. W SDK Manager zainstaluj platformę Android API 36
   i narzędzia wymagane przez projekt. Projekt używa AGP 8.10.0, Gradle 8.11.1
   oraz JDK 17 lub zgodnego JDK 21 z Android Studio.
3. Uruchom `BUDUJ_APK.cmd`. Skrypt wykrywa standardową instalację Android Studio
   i SDK, uruchamia testy, a następnie buduje wariant `fixed`.
4. Po udanym zakończeniu w folderze projektu powstanie `ReAppzuku-Fix-1.8.7.apk`.

Przy niestandardowych lokalizacjach wskaż `JAVA_HOME` i `ANDROID_HOME` albo otwórz
projekt w Android Studio i uruchom w jego terminalu:

```text
gradlew.bat :app:assembleFixed
```

W tym drugim przypadku wynik znajduje się w
`app\build\outputs\apk\fixed\app-fixed.apk`.

Wariant `fixed` ma nazwę **ReAppzuku Fix** i pakiet
`com.gree1d.reappzuku.fixed`, więc jest przeznaczony do instalacji obok oryginału.
Wykorzystuje lokalny klucz podpisujący wygenerowany przez Gradle na komputerze
budującego; nie zawiera klucza autora oryginału. Nie jest oznaczony jako aplikacja
debugowalna. Zachowaj swój lokalny klucz do podpisywania przyszłych aktualizacji.

## Budowanie w GitHub Actions

W forku użyj workflow **Build ReAppzuku Fix**, znajdującego się w pliku
`.github/workflows/reappzuku-fix.yml`. Po włączeniu Actions uruchom go przyciskiem
**Run workflow**. Uruchamia się również po zmianach na `main` i gałęziach `fix/**`.
Najpierw wykonuje 18 testów parsera, potem buduje wariant `fixed` i sprawdza jego
podpis. Gotowy APK, suma SHA-256, publiczny certyfikat podpisu i identyfikator
commita znajdą się w artefakcie `ReAppzuku-Fix-<numer>` na stronie wykonania.
Status i pobieranie APK: https://github.com/jakamilek/ReAppzuku/actions

Bez konfiguracji sekretu powstaje APK testowy z kluczem wygenerowanym dla danego
uruchomienia. Kolejne takie APK mogą wymagać odinstalowania poprzedniej wersji
**Fix**, co usuwa jej dane. Oryginalna aplikacja ma oddzielny pakiet.
Do przyszłych aktualizacji zachowujących dane właściciel repozytorium może ustawić
sekret Actions `REAPPZUKU_FIX_KEYSTORE_BASE64`: zawartość swojego klucza
`debug.keystore` zakodowaną Base64, z aliasem `androiddebugkey` oraz hasłem
magazynu i klucza `android`. Plik klucza pozostaje prywatny; nie należy dodawać
go do repozytorium ani artefaktów. Wariant `fixed` nie jest debugowalny mimo
używania tej konfiguracji podpisu. Zachowaj kopię klucza poza GitHubem.

Oryginalny workflow **Android CI** służy do wydania autora i wymaga innych
sekretów; do tej poprawki wybierz **Build ReAppzuku Fix**.

## Pierwsze uruchomienie po zbudowaniu

1. Zachowaj oryginalną aplikację i jej konfigurację; na czas próby wyłącz w niej
   automatykę. Ustawienia nowego pakietu są oddzielne.
2. Nadaj **ReAppzuku Fix** dostęp do roota w SukiSU.
3. Na początek dodaj tylko Allegro do blacklisty. Wybierz `am force-stop`, włącz
   usługę w tle i zatrzymywanie po wyłączeniu ekranu. Wyłącz próg RAM na czas próby.
4. Sprawdź kafelek **Stop background apps (Fix)** po wyjściu z Allegro na pulpit.
   Następnie ponownie uruchom Allegro i sprawdź zatrzymanie po wygaszeniu ekranu.
5. Allegro powinno dać się ponownie uruchomić z launchera. Po pomyślnej próbie
   możesz skonfigurować pozostałe aplikacje.

Nie odinstalowuj oryginału tylko po to, by zainstalować ten wariant. Nie zastępuj
go oryginalnym APK pobranym przez wbudowane sprawdzanie aktualizacji: wydania
autora nie zawierają tej nieoficjalnej poprawki.

## Testy bez Android SDK

Linux/macOS z JDK 17 lub nowszym:

```sh
sh tools/run-foreground-tests.sh
```

Testy kompilują tę samą klasę parsera, z której korzysta aplikacja, i nie pobierają
dodatkowych bibliotek. Na Windows są również wykonywane przez `BUDUJ_APK.cmd`.

## Źródła zachowania Androida

- https://developer.android.com/reference/android/os/PowerManager#isInteractive()
- https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/wm/ActivityRecord.java
- https://github.com/gree1d/ReAppzuku/tree/1.8.7

Oryginalna licencja projektu znajduje się w pliku `LICENSE`.
