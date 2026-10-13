package cz.geokuk.plugins.kesoid.mapicon;

import java.awt.Color;
import java.util.*;

/**
 * Zvýraznění keší barevným čtvercem pod ikonou. Pravidlo je podmínka z alel (všechny zároveň) a barva; když vyhovuje víc pravidel, platí první.
 * Textová podoba pro nastavení: název a pravidla oddělená středníkem, pravidlo je barva {@code #RRGGBB} a kvalifikovaná jména alel oddělená {@code |}.
 */
public final class Zvyrazneni {

	private static final String ODDELOVAC = ";";
	private static final String ODDELOVAC_ALEL = "|";

	private static final Color ZLUTA = new Color(255, 255, 0);
	private static final Color ORANZOVA = new Color(255, 153, 51);

	public static final Zvyrazneni BEZ = new Zvyrazneni("Bez zvýraznění", Collections.emptyList());
	public static final Zvyrazneni TEREN = new Zvyrazneni("Terén 4,5 a 5",
			Arrays.asList(new Pravidlo(ZLUTA, "gc:druh", "~~:stav", "ter45:ter"), new Pravidlo(ORANZOVA, "gc:druh", "~~:stav", "ter50:ter")));
	public static final Zvyrazneni OBTIZNOST = new Zvyrazneni("Obtížnost 4,5 a 5",
			Arrays.asList(new Pravidlo(ZLUTA, "gc:druh", "~~:stav", "dif45:dif"), new Pravidlo(ORANZOVA, "gc:druh", "~~:stav", "dif50:dif")));
	public static final Zvyrazneni VYCHOZI = TEREN;
	public static final List<Zvyrazneni> VESTAVENA = Collections.unmodifiableList(Arrays.asList(BEZ, TEREN, OBTIZNOST));

	/** Podmínka z alel a barva čtverce. */
	public static final class Pravidlo {
		private final Color barva;
		private final SortedSet<String> alely;

		public Pravidlo(final Color barva, final Collection<String> alely) {
			this.barva = new Color(barva.getRGB() & 0xFFFFFF);
			this.alely = Collections.unmodifiableSortedSet(new TreeSet<>(alely));
		}

		Pravidlo(final Color barva, final String... alely) {
			this(barva, Arrays.asList(alely));
		}

		public Color getBarva() {
			return barva;
		}

		/** Kvalifikovaná jména alel, která musí mít waypoint všechna. */
		public SortedSet<String> getAlely() {
			return alely;
		}

		String naText() {
			return String.format("#%06X", barva.getRGB() & 0xFFFFFF) + ' ' + String.join(ODDELOVAC_ALEL, alely);
		}

		static Pravidlo zTextu(final String text) {
			final int mezera = text.indexOf(' ');
			if (!text.startsWith("#") || mezera < 0) {
				return null;
			}
			try {
				final Color barva = new Color(Integer.parseInt(text.substring(1, mezera), 16));
				final List<String> alely = new ArrayList<>();
				for (final String a : text.substring(mezera + 1).split("\\" + ODDELOVAC_ALEL)) {
					if (!a.isEmpty()) {
						alely.add(a);
					}
				}
				return new Pravidlo(barva, alely);
			} catch (final NumberFormatException e) {
				return null;
			}
		}

		@Override
		public boolean equals(final Object o) {
			return o instanceof Pravidlo && barva.equals(((Pravidlo) o).barva) && alely.equals(((Pravidlo) o).alely);
		}

		@Override
		public int hashCode() {
			return Objects.hash(barva, alely);
		}

		@Override
		public String toString() {
			return naText();
		}
	}

	private final String nazev;
	private final List<Pravidlo> pravidla;

	public Zvyrazneni(final String nazev, final List<Pravidlo> pravidla) {
		this.nazev = nazev.replace(ODDELOVAC, ",").trim();
		this.pravidla = Collections.unmodifiableList(new ArrayList<>(pravidla));
	}

	public String getNazev() {
		return nazev;
	}

	public List<Pravidlo> getPravidla() {
		return pravidla;
	}

	public boolean isVestavene() {
		return VESTAVENA.contains(this);
	}

	public String naText() {
		final StringBuilder sb = new StringBuilder(nazev);
		for (final Pravidlo p : pravidla) {
			sb.append(ODDELOVAC).append(p.naText());
		}
		return sb.toString();
	}

	/** Zvýraznění z nastavení; null při prázdném textu. Nečitelná pravidla se vynechají. */
	public static Zvyrazneni zTextu(final String text) {
		if (text == null || text.isEmpty()) {
			return null;
		}
		final String[] casti = text.split(ODDELOVAC, -1);
		final List<Pravidlo> pravidla = new ArrayList<>();
		for (int i = 1; i < casti.length; i++) {
			final Pravidlo p = Pravidlo.zTextu(casti[i]);
			if (p != null) {
				pravidla.add(p);
			}
		}
		return new Zvyrazneni(casti[0], pravidla);
	}

	/** Stejná pravidla, na názvu nezáleží. */
	public boolean stejnaPravidla(final Zvyrazneni z) {
		return z != null && pravidla.equals(z.pravidla);
	}

	@Override
	public boolean equals(final Object o) {
		return o instanceof Zvyrazneni && nazev.equals(((Zvyrazneni) o).nazev) && pravidla.equals(((Zvyrazneni) o).pravidla);
	}

	@Override
	public int hashCode() {
		return Objects.hash(nazev, pravidla);
	}

	@Override
	public String toString() {
		return naText();
	}
}
