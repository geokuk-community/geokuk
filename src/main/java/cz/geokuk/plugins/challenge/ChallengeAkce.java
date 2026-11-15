package cz.geokuk.plugins.challenge;

/** Společné vlastnosti akcí plánovače a tlačítek pruhu: stav se bere z {@link ChallengeModel}. */
final class ChallengeAkce {

	private ChallengeAkce() {}

	/** Akce „další/předchozí“ a filtr jsou dostupné, když je vidět pruh plánovače. */
	static boolean dostupne(final ChallengeModel model) {
		return model != null && model.jePruhViditelny();
	}

	/**
	 * Posune výběr zobrazené challenge (◀ ▶): u 360° na další nebo předchozí chybějící sektor s kandidáty po kružnici, u matice D/T na políčko po řádcích. Keš nevybírá a mapu
	 * nevystřeďuje. Vrací vybraný sektor („143°“) nebo políčko („D3/T4“), null když není kam nebo chybí model či challenge. Ve vlákně Swingu.
	 */
	static String posun(final ChallengeModel model, final boolean dopredu) {
		final Challenge c = model == null ? null : model.zobrazena();
		if (c == null) {
			return null;
		}
		switch (c.typ()) {
		case SEKTORY_360:
			final int s = model.dalsiSektor(c, dopredu);
			return s == PosunVyberu.ZADNY_SEKTOR ? null : s + "°";
		case DT_MATICE:
			return model.dalsiPolicko(c, dopredu);
		default:
			return null;
		}
	}

	/** Název akce ◀ ▶ podle typu zobrazené challenge. */
	static String nazevPosunu(final Challenge.Typ typ, final boolean dopredu) {
		final String co = typ == Challenge.Typ.DT_MATICE ? "políčko" : "sektor";
		return (dopredu ? "Další " : "Předchozí ") + co;
	}

	/** Tooltip ◀ ▶ podle typu zobrazené challenge. */
	static String tipPosunu(final Challenge.Typ typ, final boolean dopredu) {
		final String poradi = typ == Challenge.Typ.DT_MATICE ? " s kandidáty po řádcích matice" : " s kandidáty po kružnici";
		return nazevPosunu(typ, dopredu) + poradi + "; Shift+šipky skočí na kandidáta ve směru šipky";
	}

	/**
	 * Skočí na nejbližšího kandidáta zobrazené challenge ve směru (kužel ±45° od vybraného kandidáta, jinak od středu mapy): vybere ho i jeho sektor a vystředí na něj mapu. Vrací
	 * kandidáta, null když žádný není nebo chybí model, plánovač či challenge.
	 */
	static VybranyKandidat skoc(final ChallengeModel model, final PlanovacChallenge planovac, final SmerSkoku smer) {
		if (model == null || planovac == null) {
			return null;
		}
		final Challenge c = model.zobrazena();
		final VybranyKandidat k = c == null ? null : planovac.kandidatVeSmeru(c, smer);
		if (k != null && c.typ() == Challenge.Typ.SEKTORY_360 && k.sektor() >= 0) {
			model.setVybranySektor(c, k.sektor());
		}
		return k;
	}

	/** Cíl posunu ◀ ▶ bez změny výběru: „Další políčko: D3/T4 · 12 kandidátů, nejbližší 2,1 km · mapa se neposune“; null, když výsledek ještě není. */
	static String cilPosunu(final ChallengeModel model, final boolean dopredu) {
		final Challenge c = model == null ? null : model.zobrazena();
		if (c == null) {
			return null;
		}
		final String nazev = nazevPosunu(c.typ(), dopredu);
		if (c.typ() == Challenge.Typ.DT_MATICE) {
			final VysledekPolicek v = model.vysledekPolicek(c);
			if (v == null) {
				return null;
			}
			final String stitek = PosunVyberu.dalsiPolicko(v, model.vybranePolicko(c), dopredu);
			if (stitek == null) {
				return bezCile(c.typ(), dopredu);
			}
			final int pocet = v.pocetKandidatu(stitek);
			final double nejblizsi = v.nejblizsiM(stitek);
			return nazev + ": " + stitek + " · " + (v.jePlne(stitek) ? pocet + "+ kandidátů" : TextyPopupuPlanovace.kandidatu(pocet))
					+ (Double.isNaN(nejblizsi) ? "" : ", nejbližší " + StitkyPruhu.vzdalenost(nejblizsi)) + " · mapa se neposune";
		}
		if (c.typ() == Challenge.Typ.SEKTORY_360) {
			final VysledekSektoru v = model.vysledek(c);
			if (v == null) {
				return null;
			}
			final int s = PosunVyberu.dalsiSektor(v, model.vybranySektor(c), dopredu);
			if (s == PosunVyberu.ZADNY_SEKTOR) {
				return bezCile(c.typ(), dopredu);
			}
			final VysledekSektoru.Kandidati k = v.kandidati(s);
			return nazev + ": " + s + "° · " + TextyPopupuPlanovace.kandidatu(k.pocet()) + ", nejbližší " + StitkyPruhu.vzdalenost(k.nejblizsiVzdalenost()) + " od středu";
		}
		return null;
	}

	/** Text tooltipu ◀ ▶ bez cíle: „Žádné další políčko s kandidáty“. */
	static String bezCile(final Challenge.Typ typ, final boolean dopredu) {
		final boolean dt = typ == Challenge.Typ.DT_MATICE;
		return "Žádné " + (dopredu ? "další" : "předchozí") + " " + (dt ? "políčko" : "sektor") + " s kandidáty";
	}

	/** Tooltip ◀ ▶ s konkrétním cílem na prvním řádku; bez výsledku podoba podle {@link #tipPosunu(Challenge.Typ, boolean)}. */
	static String tipPosunu(final ChallengeModel model, final Challenge.Typ typ, final boolean dopredu) {
		final String cil = cilPosunu(model, dopredu);
		if (cil == null) {
			return tipPosunu(typ, dopredu);
		}
		return "<html>" + cil.replace("&", "&amp;").replace("<", "&lt;") + "<br>Shift+šipky skočí na kandidáta ve směru šipky</html>";
	}

	/** Je kam posunout? False jen když výsledek existuje a v daném směru není žádný sektor ani políčko s kandidáty. */
	static boolean jeKamPosunout(final ChallengeModel model, final boolean dopredu) {
		final Challenge c = model == null ? null : model.zobrazena();
		if (c == null) {
			return false;
		}
		final String cil = cilPosunu(model, dopredu);
		return cil == null || !cil.equals(bezCile(c.typ(), dopredu));
	}
}
