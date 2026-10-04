package cz.geokuk.plugins.kesoid;

/** Nejvíc waypointů ve výřezu, které se ještě kreslí jako ikony a jako tečky. Teček je vždy aspoň tolik jako ikon. */
public final class LimityKresleni {

	public static final int MIN = 30_000;
	public static final int MAX = 2_000_000;
	public static final int VYCHOZI_IKON = 90_000;
	public static final int VYCHOZI_TECEK = 300_000;
	public static final LimityKresleni VYCHOZI = new LimityKresleni(VYCHOZI_IKON, VYCHOZI_TECEK);

	private final int ikon;
	private final int tecek;

	private LimityKresleni(final int ikon, final int tecek) {
		this.ikon = ikon;
		this.tecek = tecek;
	}

	/** Hodnoty mimo rozsah ořízne, teček nastaví aspoň tolik jako ikon. */
	public static LimityKresleni of(final int ikon, final int tecek) {
		final int i = orizni(ikon);
		return new LimityKresleni(i, Math.max(i, orizni(tecek)));
	}

	/** Limity zadané vlastnostmi {@code geokuk.limitIkon} a {@code geokuk.limitTecek} (pro měření) přebijí uložené. */
	public LimityKresleni sVlastnostmi() {
		return of(Integer.getInteger("geokuk.limitIkon", ikon), Integer.getInteger("geokuk.limitTecek", tecek));
	}

	private static int orizni(final int hodnota) {
		return Math.max(MIN, Math.min(MAX, hodnota));
	}

	public int getIkon() {
		return ikon;
	}

	public int getTecek() {
		return tecek;
	}

	@Override
	public boolean equals(final Object o) {
		return o instanceof LimityKresleni && ((LimityKresleni) o).ikon == ikon && ((LimityKresleni) o).tecek == tecek;
	}

	@Override
	public int hashCode() {
		return 31 * ikon + tecek;
	}

	@Override
	public String toString() {
		return "ikon " + ikon + ", teček " + tecek;
	}
}
