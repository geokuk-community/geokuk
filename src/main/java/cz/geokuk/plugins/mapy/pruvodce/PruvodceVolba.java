package cz.geokuk.plugins.mapy.pruvodce;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast;

/** Co si uživatel v průvodci vybral: mapy (klíče z katalogu), podklad a vrstvy. Neměnné. */
public final class PruvodceVolba {

	private final List<String> klice;
	private final PodkladPruvodce podklad;
	private final Set<Cast> vrstvy;

	public PruvodceVolba(final List<String> klice, final PodkladPruvodce podklad, final Set<Cast> vrstvy) {
		this.klice = Collections.unmodifiableList(new ArrayList<>(klice));
		this.podklad = podklad;
		final Set<Cast> v = EnumSet.noneOf(Cast.class);
		v.addAll(vrstvy);
		v.remove(Cast.MAPA);
		this.vrstvy = Collections.unmodifiableSet(v);
	}

	/** Klíče map (cesty mapy na serveru bez přípony) ze zvolených zemí. */
	public List<String> getKlice() {
		return klice;
	}

	public PodkladPruvodce getPodklad() {
		return podklad;
	}

	/** Zaškrtnuté vrstvy: trasy, vrstevnice, stínování. */
	public Set<Cast> getVrstvy() {
		return vrstvy;
	}
}
