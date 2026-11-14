package cz.geokuk.plugins.challenge;

/** Texty značek plánovače na mapě. */
final class TextyKresleni {

	private TextyKresleni() {
	}

	static String kandidatu(final int pocet) {
		if (pocet == 1) {
			return "1 kandidát";
		}
		return pocet + (pocet >= 2 && pocet <= 4 ? " kandidáti" : " kandidátů");
	}

	/** Štítek vybraného sektoru: „Sektor 213° · 5 kandidátů · 2,8 km“. */
	static String vybranySektor(final int sektor, final int pocet, final double metru) {
		return "Sektor " + sektor + "° · " + kandidatu(pocet) + (Double.isNaN(metru) ? "" : " · " + StitkyPruhu.vzdalenost(metru));
	}

	/** Štítek vybrané keše: „Sektor 213° · 5 kandidátů · nejbližší 46 km od středu“, u sektoru bez kandidátů jen „Sektor 213°“. */
	static String sektorVybrane(final int sektor, final PrehledChallenge prehled) {
		final Object zdroj = prehled.zdroj();
		final VysledekSektoru.Kandidati k = zdroj instanceof VysledekSektoru ? ((VysledekSektoru) zdroj).kandidati(sektor) : null;
		if (k == null || k.pocet() == 0) {
			return "Sektor " + sektor + "°";
		}
		return "Sektor " + sektor + "° · " + kandidatu(k.pocet()) + (Double.isNaN(k.nejblizsiVzdalenost()) ? "" : " · nejbližší " + StitkyPruhu.vzdalenost(k.nejblizsiVzdalenost()) + " od středu");
	}

	/** Štítek středu: „Střed: domov“, „Střed: GC56R49“. */
	static String stred(final String popis) {
		return "Střed: " + popis;
	}

	/** Štítek středu, když kružnice v okně není vidět: „Střed: domov · kružnice 144 km“. */
	static String stred(final String popis, final double polomerKruznice) {
		return Double.isNaN(polomerKruznice) ? stred(popis) : stred(popis) + " · kružnice " + StitkyPruhu.vzdalenost(polomerKruznice);
	}

	/** Šipka u okraje mapy: „Střed: domov · 25 km“, případně s poloměrem kružnice: „Střed: domov · 25 km · kružnice 144 km“. */
	static String stredMimoVyrez(final String popis, final double metru, final double polomerKruznice) {
		return stred(popis) + " · " + StitkyPruhu.vzdalenost(metru) + (Double.isNaN(polomerKruznice) ? "" : " · kružnice " + StitkyPruhu.vzdalenost(polomerKruznice));
	}

	/** Popisek na kružnici: „2,9 km“. */
	static String popisekKruznice(final double polomerKruznice) {
		return StitkyPruhu.vzdalenost(polomerKruznice);
	}
}
