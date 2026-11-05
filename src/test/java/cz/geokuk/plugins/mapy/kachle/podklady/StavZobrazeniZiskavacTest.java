package cz.geokuk.plugins.mapy.kachle.podklady;

import static org.junit.Assert.*;

import java.awt.Image;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;

/** Získávač zapíše stav posledního zobrazení z bajtů v cache; do cache nic nezapisuje. */
public class StavZobrazeniZiskavacTest {

	@Rule
	public final TemporaryFolder tmp = new TemporaryFolder();

	private final AtomicInteger ulozeni = new AtomicInteger();
	private final AtomicInteger cteni = new AtomicInteger();
	private boolean ukladat = true;

	private KachleZiskavac ziskavac(final File slozka, final Set<Ka> vCache) {
		final KachleZiskavac z = new KachleZiskavac();
		z.setKachleManager(new KachleManager() {
			@Override
			public boolean exists(final Ka ki) {
				return vCache.contains(ki);
			}

			@Override
			public Image load(final Ka ki) {
				return null;
			}

			@Override
			public boolean save(final Collection<ItemToSave> itemsToSave) {
				ulozeni.incrementAndGet();
				return true;
			}

			@Override
			public byte[] nactiBajty(final Ka ki, final String typ) {
				cteni.incrementAndGet();
				return vCache.contains(ki) && typ.equals(ki.typToString()) ? (ki.getLoc().getFromSzUnsignedX() + "/" + ki.getLoc().getFromSzUnsignedY()).getBytes() : null;
			}

			@Override
			public File slozka() {
				return slozka;
			}
		});
		z.inject(new KachleModel() {
			@Override
			public boolean isUkladatMapyNaDisk() {
				return ukladat;
			}
		});
		return z;
	}

	private static Ka ka(final EKaType typ, final int x, final int y) {
		return new Ka(KaLoc.ofXyz(x, y, 14), typ);
	}

	@Test
	public void zapiseVyrezPodkladuAPrekryvNaJehoDlazdicich() throws Exception {
		final File s = tmp.newFolder();
		final List<Ka> podklad = Arrays.asList(ka(EKaType.TURIST_M, 10, 20), ka(EKaType.TURIST_M, 11, 20), ka(EKaType.TURIST_M, 12, 20));
		final Ka prekryvNaVyrezu = ka(EKaType.BASE_M, 11, 20);
		final Ka prekryvJinde = ka(EKaType.BASE_M, 500, 500);
		final Set<Ka> vCache = new HashSet<>(Arrays.asList(podklad.get(0), podklad.get(2), prekryvNaVyrezu, prekryvJinde));
		final KachleZiskavac z = ziskavac(s, vCache);
		z.zapamatujZobrazeni(1, Arrays.asList(prekryvJinde, prekryvNaVyrezu), "BASE_M", 14, 0, 0);
		z.zapamatujZobrazeni(0, podklad, "TURIST_M", 14, 111, 222);
		z.zapisStavZobrazeni();
		final StavZobrazeni.Stav st = StavZobrazeni.nacti(s);
		assertNotNull(st);
		assertEquals("TURIST_M", st.hlavicka.podklad);
		assertEquals(111, st.hlavicka.mouX);
		final List<String> zapsane = new ArrayList<>();
		st.zaznamy.forEach(r -> zapsane.add(r.typ + " " + r.x + "/" + r.y + " " + new String(r.data)));
		assertEquals("podklad od středu bez dlaždice, která v cache není, pak překryv jen na dlaždicích podkladu",
				Arrays.asList("TURIST_M 10/20 10/20", "TURIST_M 12/20 12/20", "BASE_M 11/20 11/20"), zapsane);
		assertEquals("do cache dlaždic se nezapisuje", 0, ulozeni.get());
	}

	@Test
	public void nezmenenyVyrezSeZnovuNezapise() throws Exception {
		final File s = tmp.newFolder();
		final List<Ka> podklad = Collections.singletonList(ka(EKaType.TURIST_M, 10, 20));
		final KachleZiskavac z = ziskavac(s, new HashSet<>(podklad));
		z.zapamatujZobrazeni(0, podklad, "TURIST_M", 14, 1, 2);
		z.zapisStavZobrazeni();
		final int poPrvnim = cteni.get();
		assertEquals(1, poPrvnim);
		z.zapisStavZobrazeni();
		z.zapamatujZobrazeni(0, podklad, "TURIST_M", 14, 1, 2);
		z.zapisStavZobrazeni();
		assertEquals("stejný výřez se nečte ani nezapisuje znovu", poPrvnim, cteni.get());
		z.zapamatujZobrazeni(0, Collections.singletonList(ka(EKaType.TURIST_M, 11, 20)), "TURIST_M", 14, 1, 2);
		z.zapisStavZobrazeni();
		assertEquals(poPrvnim + 1, cteni.get());
	}

	@Test
	public void bezUkladaniNaDiskSeNezapisuje() throws Exception {
		final File s = tmp.newFolder();
		final List<Ka> podklad = Collections.singletonList(ka(EKaType.TURIST_M, 10, 20));
		final KachleZiskavac z = ziskavac(s, new HashSet<>(podklad));
		ukladat = false;
		z.zapamatujZobrazeni(0, podklad, "TURIST_M", 14, 1, 2);
		z.zapisStavZobrazeni();
		assertFalse(new File(s, StavZobrazeni.SOUBOR).exists());
		assertEquals(0, cteni.get());
	}

	@Test
	public void bezVyrezuPodkladuSeNezapisuje() throws Exception {
		final File s = tmp.newFolder();
		final KachleZiskavac z = ziskavac(s, new HashSet<>());
		z.zapamatujZobrazeni(1, Collections.singletonList(ka(EKaType.BASE_M, 1, 1)), "BASE_M", 14, 0, 0);
		z.zapisStavZobrazeni();
		assertFalse(new File(s, StavZobrazeni.SOUBOR).exists());
	}

	/** Program končí: stav se zapíše po dopsání dlaždic, i když odložený zápis ještě nenastal. */
	@Test
	public void priUkonceniSeZapise() throws Exception {
		final File s = tmp.newFolder();
		final List<Ka> podklad = Collections.singletonList(ka(EKaType.TURIST_M, 10, 20));
		final KachleZiskavac z = ziskavac(s, new HashSet<>(podklad));
		z.zapamatujZobrazeni(0, podklad, "TURIST_M", 14, 1, 2);
		z.dopisNaDisk();
		assertEquals(1, StavZobrazeni.nacti(s).zaznamy.size());
		z.zapamatujZobrazeni(0, Collections.singletonList(ka(EKaType.TURIST_M, 11, 20)), "TURIST_M", 14, 1, 2);
		assertEquals("po ukončení se nic dalšího nenaplánuje", 1, StavZobrazeni.nacti(s).zaznamy.size());
	}

	/** Nepovedený zápis se nepočítá jako zapsaný: při ukončení se zkusí znovu. */
	@Test
	public void nepovedenyZapisSeZopakujePriUkonceni() throws Exception {
		final File s = tmp.newFolder();
		final List<Ka> podklad = Collections.singletonList(ka(EKaType.TURIST_M, 10, 20));
		final KachleZiskavac z = ziskavac(s, new HashSet<>(podklad));
		z.zapamatujZobrazeni(0, podklad, "TURIST_M", 14, 1, 2);
		final File docasny = new File(s, StavZobrazeni.SOUBOR + ".tmp");
		assertTrue(docasny.mkdir());
		z.zapisStavZobrazeni();
		assertFalse(new File(s, StavZobrazeni.SOUBOR).exists());
		assertFalse("po chybě se dočasný soubor uklidí", docasny.exists());
		z.dopisNaDisk();
		assertEquals(1, StavZobrazeni.nacti(s).zaznamy.size());
	}
}
