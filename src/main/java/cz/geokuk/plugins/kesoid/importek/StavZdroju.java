package cz.geokuk.plugins.kesoid.importek;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Neměnný snímek stavu všech položek zdrojů, pořadí jako při načítání. */
public final class StavZdroju {
	public static final StavZdroju PRAZDNY = new StavZdroju(Collections.emptyList());

	private final List<StavPolozky> polozky;

	StavZdroju(final List<StavPolozky> polozky) {
		this.polozky = Collections.unmodifiableList(new ArrayList<>(polozky));
	}

	public List<StavPolozky> getPolozky() {
		return polozky;
	}

	public List<StavPolozky> getPolozky(final TypZdroje typ) {
		final List<StavPolozky> vysledek = new ArrayList<>();
		for (final StavPolozky p : polozky) {
			if (p.getTyp() == typ) {
				vysledek.add(p);
			}
		}
		return vysledek;
	}

	/** Je zapnutá aspoň jedna položka typu. */
	public boolean isTypZapnut(final TypZdroje typ) {
		return polozky.stream().anyMatch(p -> p.getTyp() == typ && p.isZapnuto());
	}

	/** Je zapnutá každá položka typu (typ aspoň jednu má). */
	public boolean isCelyTypZapnut(final TypZdroje typ) {
		final List<StavPolozky> typove = getPolozky(typ);
		return !typove.isEmpty() && typove.stream().allMatch(StavPolozky::isZapnuto);
	}

	/**
	 * Stav přepínače typu podle zapnutých položek: chyba, pak čekání na zápis, pak načítání (i čekání na řadu), jinak načteno; bez zapnuté položky vypnuto.
	 */
	public StavZdroje getStavTypu(final TypZdroje typ) {
		boolean zapnuto = false;
		boolean cekaNaZapis = false;
		boolean nacita = false;
		for (final StavPolozky p : polozky) {
			if (p.getTyp() != typ || !p.isZapnuto()) {
				continue;
			}
			zapnuto = true;
			switch (p.getStav()) {
			case CHYBA:
				return StavZdroje.CHYBA;
			case CEKA_NA_ZAPIS:
				cekaNaZapis = true;
				break;
			case NACITA_SE:
			case CEKA_NA_RADU:
				nacita = true;
				break;
			default:
				break;
			}
		}
		if (!zapnuto) {
			return StavZdroje.VYPNUTO;
		}
		return cekaNaZapis ? StavZdroje.CEKA_NA_ZAPIS : nacita ? StavZdroje.NACITA_SE : StavZdroje.NACTENO;
	}

	/** Některá zapnutá položka se načítá nebo čeká na řadu. */
	public boolean isNacitaSe() {
		return polozky.stream().anyMatch(p -> p.isZapnuto() && (p.getStav() == StavZdroje.NACITA_SE || p.getStav() == StavZdroje.CEKA_NA_RADU));
	}
}
