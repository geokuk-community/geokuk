package cz.geokuk.plugins.cesty.data;

import java.util.*;

import cz.geokuk.core.coordinates.*;
import cz.geokuk.plugins.cesty.routovani.Trasa;

public class Usek extends Bousek0 {

	private Bod bvpred;
	private Bod bvzad;
	/** O kolik metrů se smí krajní bod posunout, aby spočtená trasa platila (přichycení bodu na keš po načtení). */
	private static final double TOLERANCE_KONCU_TRASY = 2;

	private TypUseku typ = TypUseku.ROVNE;
	private double dalka = -1;

	/** Výsledek hledání trasy po cestách a krajní body, pro které platí; null = nespočteno. */
	private Trasa.Stav stavTrasy;
	private Mou trasaOd;
	private Mou trasaDo;
	/** Body trasy od {@link #trasaOd} do {@link #trasaDo}, null když trasa nalezena není. */
	private List<Mou> trasa;

	/**
	 * Spočítá bod, který leží na přímce m1m2 a je nejblíže m3. To je průsečík kolmice.
	 *
	 * @param m1
	 * @param m2
	 * @param m3
	 * @return Průsečík kolmice, nikdy nevrací null.
	 */
	private static Mou prusecikKolmice(final Mou m1, final Mou m2, final Mou m3) {
		// TODO : what to do if m1 == m2?
		if (m1.equals(m2)) {
			return new Mou(m3);
		}

		final double x1 = m1.xx;
		final double y1 = m1.yy;
		final double x2 = m2.xx;
		final double y2 = m2.yy;
		final double x3 = m3.xx;
		final double y3 = m3.yy;

		final double a11 = x1 - x2;
		final double a12 = y1 - y2;
		final double a21 = a12;
		final double a22 = -a11;
		final double b1 = -(a22 * x3 + -a12 * y3);
		final double b2 = -(x1 * y2 - x2 * y1);

		final double d = a11 * a22 - a12 * a21;
		final double d1 = b1 * a22 - a12 * b2;
		final double d2 = a11 * b2 - b1 * a21;

		final double x = d1 / d;
		final double y = d2 / d;

		final Mou mou = new Mou((int) x, (int) y);
		// System.out.printf("pruseCikk [%d,%d] --- [%d,%d] | [%d,%d] = [%d,%d]%n", x1, y1, x2, y2, x3, y3, x, y);
		// System.out.printf("pruseCikk (%d,%d) %n", a11, a12);
		return mou;

	}

	Usek(final Cesta cesta) {
		super(cesta);
	}

	/**
	 * Spočítá kvadrát vzdálenosti bodu k úsečce úseku, což může být i ke krajním bodům.
	 *
	 * @param mou
	 * @return
	 */
	@Override
	public long computeKvadratVzdalenosti(final Mou mou) {
		final List<Mou> t = mou == null ? null : getTrasa();
		if (t != null) {
			return kvadratVzdalenostiKTrase(t, mou);
		}
		final Mou m = getNejblizsiBodKolmoKUsecce(mou);
		long kvadrat;
		if (mou == null) { // je to jeden z krajnich bodul
			kvadrat = Math.min(bvzad.getMou().getKvadratVzdalenosti(m), bvpred.getMou().getKvadratVzdalenosti(m));
		} else {
			// je to komo k usecce, takze to je bod na usecce
			kvadrat = mou.getKvadratVzdalenosti(m);
		}
		return kvadrat;
	}

	/** Délka trasy v metrech od jejího začátku k místu na trase nejblíž bodu. */
	private static double delkaTrasyKBodu(final List<Mou> trasa, final Mou mou) {
		double nejmene = Double.MAX_VALUE;
		double vysledek = 0;
		double predtim = 0;
		for (int i = 1; i < trasa.size(); i++) {
			final Mou a = trasa.get(i - 1);
			final Mou b = trasa.get(i);
			final double dx = (double) b.xx - a.xx;
			final double dy = (double) b.yy - a.yy;
			final double d2 = dx * dx + dy * dy;
			double t = d2 == 0 ? 0 : (((double) mou.xx - a.xx) * dx + ((double) mou.yy - a.yy) * dy) / d2;
			t = Math.max(0, Math.min(1, t));
			final double ex = a.xx + t * dx - mou.xx;
			final double ey = a.yy + t * dy - mou.yy;
			final double useku = FGeoKonvertor.dalka(a, b);
			if (ex * ex + ey * ey < nejmene) {
				nejmene = ex * ex + ey * ey;
				vysledek = predtim + t * useku;
			}
			predtim += useku;
		}
		return vysledek;
	}

	/** Kvadrát vzdálenosti bodu k lomené čáře trasy po cestách. */
	private static long kvadratVzdalenostiKTrase(final List<Mou> trasa, final Mou mou) {
		double nejmene = Double.MAX_VALUE;
		for (int i = 1; i < trasa.size(); i++) {
			final Mou a = trasa.get(i - 1);
			final Mou b = trasa.get(i);
			final double dx = (double) b.xx - a.xx;
			final double dy = (double) b.yy - a.yy;
			final double d2 = dx * dx + dy * dy;
			double t = d2 == 0 ? 0 : (((double) mou.xx - a.xx) * dx + ((double) mou.yy - a.yy) * dy) / d2;
			t = Math.max(0, Math.min(1, t));
			final double ex = a.xx + t * dx - mou.xx;
			final double ey = a.yy + t * dy - mou.yy;
			nejmene = Math.min(nejmene, ex * ex + ey * ey);
		}
		return nejmene >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) nejmene;
	}

	public long computeKvadratVzdalenostiBoduKUsecce(final Mou mou) {
		final Mou m = getNejblizsiBodKolmoKUsecce(mou);
		if (m == null) {
			return Long.MAX_VALUE;
		}
		final long kvadrat = mou.getKvadratVzdalenosti(m);
		return kvadrat;
	}

	/**
	 * Spočítá kvadrát vzdálenosti bodu k úsečce úseku, což může být i ke krajním bodům.
	 *
	 * @param mou
	 * @return
	 */
	public long computeKvadratVzdalenostiKPrimce(final Mou mou) {
		final Mou m = getNejblizsiBodKPrimce(mou);
		final long kvadrat = mou.getKvadratVzdalenosti(m);
		return kvadrat;
	}

	@Override
	public double dalka() {
		if (isVzdusny()) {
			return 0;
		}
		if (dalka < 0) {
			final List<Mou> t = getTrasa();
			if (t == null) {
				dalka = FGeoKonvertor.dalka(bvpred, bvzad);
			} else {
				double d = 0;
				for (int i = 1; i < t.size(); i++) {
					d += FGeoKonvertor.dalka(t.get(i - 1), t.get(i));
				}
				dalka = d;
			}
		}
		return dalka;
	}

	@Override
	public double dalkaCestaVpred(final Mou aMou) {
		final double delka = getMouDelkaVpred(aMou) + bvpred.dalkaCestaVpred(aMou);
		return delka;
	}

	@Override
	public double dalkaCestaVzad(final Mou aMou) {
		final double delka = getMouDelkaVzad(aMou) + bvzad.dalkaCestaVzad(aMou);
		return delka;
	}

	@Override
	public Bousek0 getBousekVpred() {
		return getBvpred();
	}

	@Override
	public Bousek0 getBousekVzad() {
		return getBvzad();
	}

	public Bod getBvpred() {
		return bvpred;
	}

	public Bod getBvzad() {
		return bvzad;
	}

	@Override
	public Bod getKoncovyBodDruheCestyVhodnyProSpojeni() {
		return null;
	}

	public double getMouDelkaVpred(final Mou aMou) {
		if (isVzdusny()) {
			return 0;
		}
		final List<Mou> t = getTrasa();
		if (t != null) {
			return Math.max(0, dalka() - delkaTrasyKBodu(t, aMou));
		}
		final Mou mou = getNejblizsiBodKPrimce(aMou);
		return FGeoKonvertor.dalka(mou, bvpred);
	}

	public double getMouDelkaVzad(final Mou aMou) {
		if (isVzdusny()) {
			return 0;
		}
		final List<Mou> t = getTrasa();
		if (t != null) {
			return delkaTrasyKBodu(t, aMou);
		}
		final Mou mou = getNejblizsiBodKPrimce(aMou);
		return FGeoKonvertor.dalka(mou, bvzad);
	}

	/**
	 * Vrací nejbližší bod k danému úseku, ale jen když pata kolmice leží na úsečce, tedy může vrátit ve speciálním případě i krajní body, ale jen pokud pata kolmice padne přesně na krajní bod.
	 *
	 * @param mou
	 * @return null, když je pata kolmice mimo úsečku.
	 */
	public Mou getNejblizsiBodKolmoKUsecce(final Mou mou) {
		if (mou == null) {
			return null;
		}
		final Mou m = prusecikKolmice(bvzad.getMou(), bvpred.getMou(), mou);
		if (jeVOpsanemObdelniku(m)) {
			return m;
		} else {
			return null;
		}
	}

	/**
	 * Vrací nejbližší bod k přímce zdadané daným úsekem. nevrací tedy krajní body.
	 *
	 * @param mou
	 * @return
	 */
	public Mou getNejblizsiBodKPrimce(final Mou mou) {
		if (mou == null) {
			return null;
		}
		return prusecikKolmice(bvzad.getMou(), bvpred.getMou(), mou);
	}

	public double getUhel() {
		final Moud moud = bvpred.mouable.getMou().sub(bvzad.mouable.getMou());
		final double uhel = Math.atan2(moud.dxx, moud.dyy);
		return uhel;
	}

	public boolean isVzdusny() {
		return typ == TypUseku.VZDUSNY;
	}

	public boolean isPoCeste() {
		return typ == TypUseku.PO_CESTE;
	}

	public TypUseku getTyp() {
		return typ;
	}

	/**
	 * Trasa po cestách od {@link #getBvzad()} k {@link #getBvpred()} včetně krajních bodů trasy.
	 *
	 * @return null, když úsek nevede po cestě, trasa není spočtená nebo nalezená, nebo se od výpočtu posunul krajní bod
	 */
	public List<Mou> getTrasa() {
		if (!isPoCeste() || trasa == null) {
			return null;
		}
		final int smer = smerTrasy();
		if (smer == 0) {
			return null;
		}
		if (smer > 0) {
			return trasa;
		}
		final List<Mou> obracene = new ArrayList<>(trasa);
		Collections.reverse(obracene);
		return Collections.unmodifiableList(obracene);
	}

	/**
	 * Výsledek posledního hledání trasy po cestách pro současné krajní body.
	 *
	 * @return null, když úsek nevede po cestě, ještě se nehledalo, nebo se od hledání posunul krajní bod
	 */
	public Trasa.Stav getStavTrasy() {
		if (!isPoCeste() || stavTrasy == null || smerTrasy() == 0) {
			return null;
		}
		return stavTrasy;
	}

	/** 1 = trasa platí ve směru úseku, -1 = obráceně (cesta se otočila), 0 = neplatí. */
	private int smerTrasy() {
		if (trasaOd == null || bvzad == null || bvpred == null) {
			return 0;
		}
		final Mou zacatek = bvzad.getMou();
		final Mou konec = bvpred.getMou();
		if (blizko(trasaOd, zacatek) && blizko(trasaDo, konec)) {
			return 1;
		}
		if (blizko(trasaOd, konec) && blizko(trasaDo, zacatek)) {
			return -1;
		}
		return 0;
	}

	private static boolean blizko(final Mou a, final Mou b) {
		return a.equals(b) || FGeoKonvertor.dalka(a, b) <= TOLERANCE_KONCU_TRASY;
	}

	@Override
	protected void kontrolaKonzistence() {
		kon(bvpred != null);
		kon(bvzad != null);
		kon(bvpred != bvzad);
		kon(bvpred.getUvzad() == this);
		kon(bvzad.getUvpred() == this);
	}

	/**
	 * V zorném poli bodu je úsek tehdy, když je v obdélníku "opsaném" nad úsekem. Opsaným obdélníkem rozumíme nejmenší možný obdélník, jehož strany jsou rovnoběžné se souřadnicovými osami a to takový, že celý úsek leží v tomto obdélníku.
	 */
	@Override
	boolean jeVOpsanemObdelniku(final Mou mou) {
		if (mou == null) {
			return false;
		}
		final Mou mou1 = bvzad.getMou();
		final Mou mou2 = bvpred.getMou();
		final boolean jex = mou1.xx <= mou.xx && mou.xx <= mou2.xx || mou1.xx >= mou.xx && mou.xx >= mou2.xx;
		final boolean jey = mou1.yy <= mou.yy && mou.yy <= mou2.yy || mou1.yy >= mou.yy && mou.yy >= mou2.yy;
		return jex && jey;
	}

	Bod rozdelAZanikni(final Mouable mouable) {
		final Bod bod = getCesta().createBod(mouable);
		final Usek u1 = getCesta().createUsek();
		final Usek u2 = getCesta().createUsek();
		u1.spoj(bvzad, bod);
		u2.spoj(bod, bvpred);
		if (isPoCeste()) {
			u1.setTyp(TypUseku.PO_CESTE);
			u2.setTyp(TypUseku.PO_CESTE);
		}
		if (isVzdusny()) {
			final long kvadratDalkyVzad = getBvzad().computeKvadratVzdalenosti(bod.getMou());
			final long kvadratDalkyVpred = getBvpred().computeKvadratVzdalenosti(bod.getMou());
			if (kvadratDalkyVpred > kvadratDalkyVzad) {
				u2.setVzdusny(true);
			} else {
				u1.setVzdusny(true);
			}
		}
		return bod;

	}

	void setBvpred(final Bod vpred) {
		bvpred = vpred;
		zneschopniDalku();
	}

	void setBvzad(final Bod vzad) {
		bvzad = vzad;
		zneschopniDalku();
	}

	void setVzdusny(final boolean vzdusny) {
		setTyp(vzdusny ? TypUseku.VZDUSNY : TypUseku.ROVNE);
	}

	void setTyp(final TypUseku typ) {
		this.typ = typ;
		zneschopniDalku();
	}

	/** Uloží výsledek hledání trasy mezi body {@code od} a {@code kam}; trasa platí, dokud krajní body úseku zůstanou na místě. */
	void setTrasa(final Mou od, final Mou kam, final Trasa.Stav stav, final List<Mou> body) {
		trasaOd = od;
		trasaDo = kam;
		stavTrasy = stav;
		trasa = body == null ? null : Collections.unmodifiableList(new ArrayList<>(body));
		zneschopniDalku();
	}

	void spoj(final Bod b1, final Bod b2) {
		bvzad = b1;
		bvpred = b2;
		b1.setUvpred(this);
		b2.setUvzad(this);
	}

	void zneschopniDalku() {
		dalka = -1;
	}

	private void kon(final boolean podm) {
		if (!podm) {
			throw new RuntimeException("Selhala kontrola konzistence useku");
		}
	}

}
