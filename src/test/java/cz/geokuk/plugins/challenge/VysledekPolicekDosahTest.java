package cz.geokuk.plugins.challenge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;

/** Dosah výpočtu kandidátů D/T a podmínka zobrazení dovětku o něm. */
public class VysledekPolicekDosahTest {

	private static final Wgs STRED = new Wgs(50, 15);
	private static final int LIMIT = 3;

	private static KandidatPolicka kandidat(final String stitek, final int i, final double metru) {
		return new KandidatPolicka("GC" + stitek + i, 50, 15, stitek, stitek, null, metru);
	}

	/** Políčko {@code stitek} má {@code pocet} kandidátů ve vzdálenostech 1000, 2000, … metrů. */
	private static void pridej(final List<KandidatPolicka> k, final String stitek, final int pocet) {
		for (int i = 1; i <= pocet; i++) {
			k.add(kandidat(stitek, i, i * 1000.0));
		}
	}

	private static VysledekPolicek vysledek(final List<KandidatPolicka> k, final String... chybejici) {
		return new VysledekPolicek(81, Arrays.asList(chybejici), k, LIMIT);
	}

	@Test
	public void bezPlnehoPolickaJeDosahNeomezeny() {
		final List<KandidatPolicka> k = new ArrayList<>();
		pridej(k, "D1/T1", LIMIT - 1);
		assertTrue(Double.isInfinite(vysledek(k, "D1/T1").dosahM()));
	}

	@Test
	public void dosahJeNejmensiVzdalenostPoslednihoKandidataPlnychPolicek() {
		final List<KandidatPolicka> k = new ArrayList<>();
		pridej(k, "D1/T1", LIMIT);
		k.add(kandidat("D2/T1", 1, 900));
		k.add(kandidat("D2/T1", 2, 1500));
		k.add(kandidat("D2/T1", 3, 1700));
		pridej(k, "D3/T1", 2);
		assertEquals(1700, vysledek(k, "D1/T1", "D2/T1", "D3/T1").dosahM(), 1e-9);
	}

	@Test
	public void neznamyLimitNeboVzdalenostDavaNeomezenyDosah() {
		final List<KandidatPolicka> k = new ArrayList<>();
		pridej(k, "D1/T1", LIMIT);
		assertTrue(Double.isInfinite(new VysledekPolicek(81, Arrays.asList("D1/T1"), k, 0).dosahM()));
		final List<KandidatPolicka> bez = new ArrayList<>();
		for (int i = 0; i < LIMIT; i++) {
			bez.add(kandidat("D1/T1", i, Double.NaN));
		}
		assertTrue(Double.isInfinite(vysledek(bez, "D1/T1").dosahM()));
	}

	@Test
	public void dovetekJenKdyzVyrezPresahujeDosah() {
		final List<KandidatPolicka> k = new ArrayList<>();
		pridej(k, "D1/T1", LIMIT);
		final VysledekPolicek v = vysledek(k, "D1/T1");
		final Wgs blizko = new Wgs(50.01, 15);
		final Wgs daleko = new Wgs(50.1, 15);
		assertTrue(Double.isNaN(v.dosahPresazenyM(STRED, blizko, blizko)));
		assertEquals(3000, v.dosahPresazenyM(STRED, blizko, daleko), 1e-9);
	}

	@Test
	public void dovetekNeniBezOrezaniKandidatu() {
		final List<KandidatPolicka> k = new ArrayList<>();
		pridej(k, "D1/T1", LIMIT - 1);
		assertTrue(Double.isNaN(vysledek(k, "D1/T1").dosahPresazenyM(STRED, new Wgs(51, 15))));
	}

	@Test
	public void text() {
		assertEquals("Kandidáti spočítáni do 3,2 km od středu mapy – posunutím mapy se dopočítají další", TextyKresleni.dosahKandidatu(3200));
		assertEquals(TextyKresleni.dosahKandidatu(3200), TextyPopupuPlanovace.dosahKandidatu(3200));
		assertEquals(" ", TextyPopupuPlanovace.dosahKandidatu(Double.NaN));
	}
}
