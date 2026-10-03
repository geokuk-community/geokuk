# Jak přispět

Hlášení chyb, nápady i úpravy kódu jsou vítané.

## Hlášení chyby

V programu zvolte Nápověda > Zadat problém. Otevře se nové issue na
GitHubu s předvyplněnými informacemi o programu, stačí doplnit, co se
stalo a jak to zopakovat. Informace o programu jdou zobrazit
i zkopírovat v Nápověda > Informace pro hlášení chyby.

Zranitelnosti nehlaste veřejně, postup je v [SECURITY.md](SECURITY.md).

## Úprava kódu

Potřebujete JDK 21 a Git. Maven se stáhne sám.

```sh
./mvnw -B clean package     # sestavení, výsledek v target/
./mvnw -B test              # testy
```

Ve Windows místo `./mvnw` použijte `.\mvnw.cmd`.

- Větev založte od aktuálního `main`, jeden pull request = jedna věc.
- Oprava chyby má mít test, který bez opravy neprojde.
- Všechny testy musí projít. Pull request sloučí správci, až na něm
  projde workflow Build.
- Texty v programu, komentáře a popisy commitů píšeme česky
  s diakritikou, zdrojové soubory jsou v UTF-8.
- V popisu pull requestu uveďte, co se mění, proč, jak jste to ověřili,
  a navrhněte řádek do [CHANGELOG.md](CHANGELOG.md).
- Větší změnu nebo novou funkci nejdřív navrhněte v issue.

## Mapové podklady

Nový mapový podklad musí splňovat podmínky poskytovatele: licence,
atribuce, API klíč a limity, včetně zákazu hromadného stahování, pokud
ho poskytovatel zakazuje. Cizí ani testovací klíče do repozitáře
nepatří. Vlastní podklad si můžete přidat i bez úprav programu jako
uživatelskou mapu, viz README.
