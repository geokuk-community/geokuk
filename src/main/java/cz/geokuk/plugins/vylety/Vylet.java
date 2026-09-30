package cz.geokuk.plugins.vylety;

import java.util.*;

import cz.geokuk.plugins.kesoid.Kesoid;

public class Vylet {

	private final Set<Kesoid> ano = new HashSet<>();
	private final Set<Kesoid> ne = new HashSet<>();
	/** Kódy ze souboru výletu, které nejsou v načtených datech; při zápisu se nesmí ztratit. */
	private final Set<String> neznameAno = new LinkedHashSet<>();
	private final Set<String> neznameNe = new LinkedHashSet<>();

	public Set<Kesoid> get(final EVylet evyl) {
		Set<Kesoid> set;
		switch (evyl) {
		case ANO:
			set = ano;
			break;
		case NE:
			set = ne;
			break;
		default:
			throw new RuntimeException("Neznáme keše, které nevíme, zda jsou ve výletu");
		}
		return Collections.unmodifiableSet(set);
	}

	/** Snímek kódů, se kterým může pracovat jiné vlákno. */
	public List<String> kody(final EVylet evyl) {
		final Set<String> kody = new LinkedHashSet<>(nezname(evyl));
		for (final Kesoid kes : get(evyl)) {
			kody.add(kes.getIdentifier());
		}
		return new ArrayList<>(kody);
	}

	public EVylet get(final Kesoid kes) {
		if (ano.contains(kes)) {
			return EVylet.ANO;
		}
		if (ne.contains(kes)) {
			return EVylet.NE;
		}
		return EVylet.NEVIM;
	}

	EVylet add(final EVylet evyl, final Kesoid kes) {
		final EVylet evylPuvodni = get(kes);
		neznameAno.remove(kes.getIdentifier());
		neznameNe.remove(kes.getIdentifier());
		switch (evyl) {
		case ANO:
			ano.add(kes);
			ne.remove(kes);
			break;
		case NE:
			ne.add(kes);
			ano.remove(kes);
			break;
		case NEVIM:
			ano.remove(kes);
			ne.remove(kes);
			break;
		default:
			assert false;
		}
		return evylPuvodni;
	}

	void removeAll(final EVylet evyl) {
		switch (evyl) {
		case ANO:
			ano.clear();
			neznameAno.clear();
			break;
		case NE:
			ne.clear();
			neznameNe.clear();
			break;
		case NEVIM:
			break;
		default:
			assert false;
		}
	}

	void pridejNezname(final EVylet evyl, final Collection<String> kody) {
		nezname(evyl).addAll(kody);
	}

	private Set<String> nezname(final EVylet evyl) {
		return evyl == EVylet.ANO ? neznameAno : neznameNe;
	}
}
