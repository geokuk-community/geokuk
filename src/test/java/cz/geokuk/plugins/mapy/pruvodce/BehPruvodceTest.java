package cz.geokuk.plugins.mapy.pruvodce;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import cz.geokuk.plugins.mapy.pruvodce.BehPruvodce.Stav;
import cz.geokuk.plugins.mapy.pruvodce.PlanPruvodce.Polozka;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.MatriceMap;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast;
import cz.geokuk.plugins.mapy.sprava.StavMapy;
import cz.geokuk.plugins.mapy.sprava.StavMapyProGui;
import cz.geokuk.plugins.mapy.sprava.StavVrstvy;
import cz.geokuk.plugins.mapy.sprava.VrstvyProGui;

/** Průběh průvodce: položky po jedné, přepnutí zobrazení v pravý čas, zrušení, chyba. */
public class BehPruvodceTest {

	private static final String CZ = "europe/czech-republic";

	/** Stav „disku“ a stahování, jak by ho hlásil program; id mapy nebo vrstvy → jeden z MAME/NEMAME/STAHUJE/CHYBA. */
	private static final class Kontext implements PruvodceKontext {
		final Map<String, String> stav = new HashMap<>();
		final List<String> stazene = new ArrayList<>();
		final List<PruvodceVolba> prepnuti = new ArrayList<>();
		int zruseni;
		boolean obsazeno;
		int pokusy;

		String dej(final String id) {
			return stav.getOrDefault(id, "NEMAME");
		}

		@Override
		public KatalogMap getKatalog() {
			return KatalogMap.vestaveny();
		}

		@Override
		public List<MatriceMap.Radek> matice() {
			return MatriceMap.sestav(KatalogMap.vestaveny(),
					m -> StavMapyProGui.stav(m, "MAME".equals(dej(m.getId())) ? StavMapy.Mistni.MAME : StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY,
							"STAHUJE".equals(dej(m.getId())) ? StavMapy.Faze.STAHUJE : "CHYBA".equals(dej(m.getId())) ? StavMapy.Faze.CHYBA : StavMapy.Faze.NIC, 0, -1, "CHYBA".equals(dej(m.getId())) ? "chyba" : null),
					VrstvyProGui.katalogCesko(),
					v -> VrstvyProGui.stav(v, "MAME".equals(dej(v.getId())) ? StavVrstvy.Mistni.MAME : StavVrstvy.Mistni.NEMAME,
							"STAHUJE".equals(dej(v.getId())) ? StavMapy.Faze.STAHUJE : "CHYBA".equals(dej(v.getId())) ? StavMapy.Faze.CHYBA : StavMapy.Faze.NIC, 0, "CHYBA".equals(dej(v.getId())) ? "chyba" : null));
		}

		@Override
		public long volneMisto() {
			return 100_000_000_000L;
		}

		@Override
		public boolean stahni(final Polozka polozka) {
			if (obsazeno) {
				pokusy++;
				return false;
			}
			stazene.add(polozka.getId());
			stav.put(polozka.getId(), "STAHUJE");
			return true;
		}

		@Override
		public void zrusStahovani() {
			zruseni++;
		}

		@Override
		public void prepni(final PruvodceVolba volba) {
			prepnuti.add(volba);
		}
	}

	private static PlanPruvodce plan(final Kontext k, final PodkladPruvodce podklad, final Cast... vrstvy) {
		return PlanPruvodce.sestav(new PruvodceVolba(Collections.singletonList(CZ), podklad, vrstvy.length == 0 ? EnumSet.noneOf(Cast.class) : EnumSet.copyOf(java.util.Arrays.asList(vrstvy))), k.matice());
	}

	@Test
	public void offlineStahujePoJedneAPrepneJakmileJeMapa() {
		final Kontext k = new Kontext();
		final List<Stav> zmeny = new ArrayList<>();
		final BehPruvodce beh = new BehPruvodce(k, plan(k, PodkladPruvodce.TURISTICKA_OFFLINE, Cast.TRASY), zmeny::add);
		beh.spust();
		assertEquals(Collections.singletonList(CZ), k.stazene);
		assertTrue("na mapu se čeká, zobrazení se zatím nepřepíná", k.prepnuti.isEmpty());
		beh.krok();
		assertEquals("mapa se stahuje, nic dalšího se nespouští", 1, k.stazene.size());
		k.stav.put(CZ, "MAME");
		beh.krok();
		assertEquals("po mapě se pustí trasy", Arrays.asList(CZ, "routes/" + CZ), k.stazene);
		assertEquals("zobrazení se přepne hned po mapě, nečeká na trasy", 1, k.prepnuti.size());
		k.stav.put("routes/" + CZ, "MAME");
		beh.krok();
		assertEquals(Stav.HOTOVO, beh.getStav());
		assertEquals("přepnutí jen jednou", 1, k.prepnuti.size());
		assertEquals(Collections.singletonList(Stav.HOTOVO), zmeny);
	}

	@Test
	public void onlinePodkladSTrasamiPrepneHned() {
		final Kontext k = new Kontext();
		final BehPruvodce beh = new BehPruvodce(k, plan(k, PodkladPruvodce.CUZK_TOPO, Cast.TRASY), s -> {});
		beh.spust();
		assertEquals("přepnuto před stažením tras", 1, k.prepnuti.size());
		assertEquals(Collections.singletonList("routes/" + CZ), k.stazene);
		k.stav.put("routes/" + CZ, "MAME");
		beh.krok();
		assertEquals(Stav.HOTOVO, beh.getStav());
		assertEquals(1, k.prepnuti.size());
	}

	@Test
	public void nicKeStazeniPrepneAHotovo() {
		final Kontext k = new Kontext();
		k.stav.put(CZ, "MAME");
		final BehPruvodce beh = new BehPruvodce(k, plan(k, PodkladPruvodce.TURISTICKA_OFFLINE), s -> {});
		beh.spust();
		assertEquals(Stav.HOTOVO, beh.getStav());
		assertEquals(1, k.prepnuti.size());
		assertTrue(k.stazene.isEmpty());
	}

	@Test
	public void chybaZastaviAZapamatujeSelhanouPolozku() {
		final Kontext k = new Kontext();
		final BehPruvodce beh = new BehPruvodce(k, plan(k, PodkladPruvodce.TURISTICKA_OFFLINE, Cast.TRASY), s -> {});
		beh.spust();
		k.stav.put(CZ, "CHYBA");
		beh.krok();
		assertEquals(Stav.ZASTAVENO, beh.getStav());
		assertNotNull(beh.getSelhala());
		assertEquals(CZ, beh.getSelhala().getId());
		assertTrue("po chybě se nic nepřepíná", k.prepnuti.isEmpty());
		k.stav.put(CZ, "MAME");
		beh.krok();
		assertEquals("zastavený průvodce už nepokračuje", 1, k.stazene.size());
	}

	@Test
	public void zruseniZrusiStahovaniAZastavi() {
		final Kontext k = new Kontext();
		final List<Stav> zmeny = new ArrayList<>();
		final BehPruvodce beh = new BehPruvodce(k, plan(k, PodkladPruvodce.TURISTICKA_OFFLINE, Cast.VRSTEVNICE), zmeny::add);
		beh.spust();
		beh.zrus();
		assertEquals(1, k.zruseni);
		assertEquals(Stav.ZASTAVENO, beh.getStav());
		assertNull(beh.getSelhala());
		assertEquals(Collections.singletonList(Stav.ZASTAVENO), zmeny);
		beh.zrus();
		assertEquals("podruhé nic", 1, k.zruseni);
	}

	/** Stahovač je zaneprázdněný (stahuje se z popupu): položka se nepovažuje za spuštěnou a zkusí se znovu. */
	@Test
	public void obsazenyStahovacSeZkusiZnovu() {
		final Kontext k = new Kontext();
		k.obsazeno = true;
		final BehPruvodce beh = new BehPruvodce(k, plan(k, PodkladPruvodce.TURISTICKA_OFFLINE), s -> {});
		beh.spust();
		beh.krok();
		assertEquals(Stav.BEZI, beh.getStav());
		assertTrue("zkouší se dál, nic nezastavilo", k.pokusy >= 2 && k.stazene.isEmpty());
		k.obsazeno = false;
		beh.krok();
		assertEquals(Collections.singletonList(CZ), k.stazene);
	}

	@Test
	public void chybaMaDuvodAZrusenaPolozkaNe() {
		final Kontext k = new Kontext();
		final BehPruvodce beh = new BehPruvodce(k, plan(k, PodkladPruvodce.TURISTICKA_OFFLINE), s -> {});
		beh.spust();
		k.stav.put(CZ, "CHYBA");
		beh.krok();
		assertEquals("chyba", beh.getDuvod());
		assertNotNull(beh.getSelhala());

		final Kontext z = new Kontext();
		final BehPruvodce zrusena = new BehPruvodce(z, plan(z, PodkladPruvodce.TURISTICKA_OFFLINE), s -> {});
		zrusena.spust();
		z.stav.put(CZ, "NEMAME"); // zrušeno v popupu: není stahování ani chyba
		zrusena.krok();
		zrusena.krok();
		assertEquals(Stav.ZASTAVENO, zrusena.getStav());
		assertNull("zrušení není chyba, žádná hláška", zrusena.getSelhala());
	}
}
