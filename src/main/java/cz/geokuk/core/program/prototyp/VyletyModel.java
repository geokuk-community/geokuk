package cz.geokuk.core.program.prototyp;

import java.util.*;

/** Zjednodušený model pojmenovaných výletů; výlet je jen seznam Lovím a Ignoruji. */
public class VyletyModel {

	public static final String VYCHOZI = "Výchozí";

	private final Map<String, int[]> vylety = new LinkedHashMap<>();
	private final List<Runnable> posluchaci = new ArrayList<>();
	private String aktivni = VYCHOZI;

	public VyletyModel() {
		vylety.put(VYCHOZI, new int[] { 0, 0 });
	}

	public void addPosluchac(final Runnable p) {
		posluchaci.add(p);
	}

	private void zmeneno() {
		for (final Runnable p : posluchaci) {
			p.run();
		}
	}

	public List<String> getJmena() {
		return new ArrayList<>(vylety.keySet());
	}

	public String getAktivni() {
		return aktivni;
	}

	public int getLovim() {
		return vylety.get(aktivni)[0];
	}

	public int getIgnoruji() {
		return vylety.get(aktivni)[1];
	}

	public boolean jeJmenoVolne(final String jmeno) {
		return !jmeno.trim().isEmpty() && !vylety.containsKey(jmeno.trim());
	}

	public void aktivuj(final String jmeno) {
		if (vylety.containsKey(jmeno)) {
			aktivni = jmeno;
			zmeneno();
		}
	}

	public void novy(final String jmeno) {
		vylety.put(jmeno.trim(), new int[] { 0, 0 });
		aktivni = jmeno.trim();
		zmeneno();
	}

	public void prejmenuj(final String jmeno) {
		final LinkedHashMap<String, int[]> nove = new LinkedHashMap<>();
		for (final Map.Entry<String, int[]> e : vylety.entrySet()) {
			nove.put(e.getKey().equals(aktivni) ? jmeno.trim() : e.getKey(), e.getValue());
		}
		vylety.clear();
		vylety.putAll(nove);
		aktivni = jmeno.trim();
		zmeneno();
	}

	public void duplikuj(final String jmeno) {
		vylety.put(jmeno.trim(), vylety.get(aktivni).clone());
		aktivni = jmeno.trim();
		zmeneno();
	}

	/** Smazat jde i poslední výlet, program pak založí prázdný Výchozí. */
	public void smaz() {
		vylety.remove(aktivni);
		if (vylety.isEmpty()) {
			vylety.put(VYCHOZI, new int[] { 0, 0 });
		}
		aktivni = vylety.keySet().iterator().next();
		zmeneno();
	}

	public void nastavPocty(final String jmeno, final int lovim, final int ignoruji) {
		vylety.put(jmeno, new int[] { lovim, ignoruji });
		zmeneno();
	}
}
