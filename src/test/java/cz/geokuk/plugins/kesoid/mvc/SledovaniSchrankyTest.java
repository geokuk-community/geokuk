package cz.geokuk.plugins.kesoid.mvc;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import cz.geokuk.core.coord.PoziceModel;
import cz.geokuk.core.coord.VyrezModel;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.Kesoid;
import cz.geokuk.plugins.kesoid.Wpt;

public class SledovaniSchrankyTest {

	/** Schránka, která si jen pamatuje posluchače: události formátu si test vyvolává sám, aby běžely ve známém pořadí. */
	private static class RucniSchranka extends Clipboard {
		private final List<java.awt.datatransfer.FlavorListener> posluchaci = new ArrayList<>();

		RucniSchranka(final String jmeno) {
			super(jmeno);
		}

		@Override
		public synchronized void addFlavorListener(final java.awt.datatransfer.FlavorListener listener) {
			posluchaci.add(listener);
		}

		@Override
		public synchronized void removeFlavorListener(final java.awt.datatransfer.FlavorListener listener) {
			posluchaci.remove(listener);
		}

		@Override
		public synchronized java.awt.datatransfer.FlavorListener[] getFlavorListeners() {
			return posluchaci.toArray(new java.awt.datatransfer.FlavorListener[0]);
		}
	}

	private final Clipboard schranka = new RucniSchranka("test");
	private final List<String> predane = new ArrayList<>();
	private final List<Runnable> pozadi = new ArrayList<>();
	private final List<Runnable> edt = new ArrayList<>();
	private int dotazyNaSchranku;
	private int cteni;
	private boolean aktivni;

	/** Ruční časovač: počítá spuštění a zrušení a drží úlohu, kterou test „odtikává“. */
	private int spusteno;
	private int zruseno;
	private Runnable tik;
	private long perioda;

	private final SledovaniSchranky.Casovac casovac = (p, uloha) -> {
		spusteno++;
		perioda = p;
		tik = uloha;
		return () -> zruseno++;
	};

	private SledovaniSchranky sledovani = sledovani(schranka);

	private SledovaniSchranky sledovani(final Clipboard s) {
		return new SledovaniSchranky(() -> {
			dotazyNaSchranku++;
			return s;
		}, () -> aktivni, t -> {
			predane.add(t);
			return true;
		}, edt::add, pozadi::add, casovac);
	}

	private void vloz(final String text) {
		schranka.setContents(new StringSelection(text), null);
	}

	private void provedFronty() {
		for (int i = 0; i < 10 && !(pozadi.isEmpty() && edt.isEmpty()); i++) {
			new ArrayList<>(pozadi).forEach(Runnable::run);
			pozadi.clear();
			new ArrayList<>(edt).forEach(Runnable::run);
			edt.clear();
		}
	}

	private void ohlas(final String text) {
		vloz(text);
		sledovani.flavorsChanged(null);
		provedFronty();
	}

	@Test
	public void vypnutoSchrankuNevytvoriNicNesledujeAniNespustiCasovac() {
		sledovani.nastav(false);
		sledovani.oknoSeZmenilo(false);
		sledovani.oknoSeZmenilo(true);
		sledovani.flavorsChanged(null);
		provedFronty();
		assertEquals("schránka se při vypnuté volbě nevytváří", 0, dotazyNaSchranku);
		assertEquals(0, spusteno);
		assertEquals(0, schranka.getFlavorListeners().length);
		assertTrue(predane.isEmpty());
	}

	@Test
	public void zapnutiVytvoriSchrankuJednouAVypnutiJiOdregistruje() {
		sledovani.nastav(true);
		sledovani.nastav(true);
		assertEquals(1, dotazyNaSchranku);
		assertEquals("opakované zapnutí listener nezdvojí", 1, schranka.getFlavorListeners().length);
		sledovani.oknoSeZmenilo(false);
		assertEquals(1, spusteno);
		sledovani.nastav(false);
		assertEquals(0, schranka.getFlavorListeners().length);
		assertEquals("časovač se po vypnutí zruší", 1, zruseno);
		sledovani.nastav(false);
		assertEquals(1, zruseno);
	}

	@Test
	public void schrankaSeCtePoZmeneFormatuAJenNaPozadi() {
		final Clipboard pocitajici = new RucniSchranka("pocitajici") {
			@Override
			public Object getData(final DataFlavor flavor) throws UnsupportedFlavorException, java.io.IOException {
				cteni++;
				return super.getData(flavor);
			}
		};
		sledovani = sledovani(pocitajici);
		sledovani.nastav(true);
		pocitajici.setContents(new StringSelection("GC19ACP"), null);
		sledovani.flavorsChanged(null);
		assertEquals("volající vlákno schránku nečte", 0, cteni);
		assertTrue(edt.isEmpty());
		new ArrayList<>(pozadi).forEach(Runnable::run);
		assertEquals(1, cteni);
		assertEquals("na vlákno událostí se předá jen výsledek", 1, edt.size());
		assertTrue(predane.isEmpty());
		edt.get(0).run();
		assertEquals(java.util.Arrays.asList("GC19ACP"), predane);
	}

	@Test
	public void neaktivniOknoPredaTextAOpakovaniIgnoruje() {
		sledovani.nastav(true);
		ohlas("https://coord.info/GCBZJGC");
		assertEquals(Arrays.asList("https://coord.info/GCBZJGC"), predane);
		ohlas("https://coord.info/GCBZJGC");
		assertEquals("stejná hodnota se nepředá znovu", 1, predane.size());
		ohlas("GC19ACP");
		assertEquals(Arrays.asList("https://coord.info/GCBZJGC", "GC19ACP"), predane);
		ohlas("https://coord.info/GCBZJGC");
		assertEquals("po jiné hodnotě se původní předá znovu", 3, predane.size());
	}

	@Test
	public void aktivniOknoSchrankuIgnorujeIKdyzPrijdeUdalostNeboTik() {
		sledovani.nastav(true);
		aktivni = true;
		ohlas("GC19ACP");
		assertTrue("kopírování v programu se neřeší", predane.isEmpty());
		aktivni = false;
		ohlas("GC19ACP");
		assertEquals("po opuštění programu už ano", 1, predane.size());
	}

	@Test
	public void druheKopirovaniTextuPoTextuBezUdalostiSeZachytiPravidelnouKontrolou() {
		sledovani.nastav(true);
		aktivni = true;
		vloz("starý obsah");
		aktivni = false;
		sledovani.oknoSeZmenilo(false);
		provedFronty();
		assertEquals("časovač běží jen při neaktivním okně", 1, spusteno);
		assertEquals(SledovaniSchranky.PERIODA_MS, perioda);
		tik.run();
		provedFronty();
		assertTrue("obsah z doby před odchodem z programu se nepředává", predane.isEmpty());

		vloz("GC19ACP");
		tik.run();
		provedFronty();
		assertEquals(Arrays.asList("GC19ACP"), predane);
		vloz("GCBZJGC");
		tik.run();
		provedFronty();
		assertEquals("další text po textu bez události formátu se zachytí", Arrays.asList("GC19ACP", "GCBZJGC"), predane);
		tik.run();
		provedFronty();
		assertEquals("beze změny se nic neopakuje", 2, predane.size());
	}

	@Test
	public void priNavratuDoProgramuSeCasovacZastaviASeStavVynuluje() {
		sledovani.nastav(true);
		aktivni = true;
		vloz("GC19ACP");
		aktivni = false;
		sledovani.oknoSeZmenilo(false);
		provedFronty();
		vloz("GCBZJGC");
		tik.run();
		provedFronty();
		assertEquals(1, predane.size());

		aktivni = true;
		sledovani.oknoSeZmenilo(true);
		assertEquals("po návratu do programu se časovač zruší", 1, zruseno);
		tik.run();
		provedFronty();
		assertEquals("i opožděný tik v aktivním okně nic nepředá", 1, predane.size());

		aktivni = false;
		sledovani.oknoSeZmenilo(false);
		provedFronty();
		assertEquals("při dalším odchodu časovač znovu poběží", 2, spusteno);
		vloz("GC19ACP");
		tik.run();
		provedFronty();
		assertEquals("po dalším odchodu z programu se čte od nového základu a předá se, co přibylo", Arrays.asList("GCBZJGC", "GC19ACP"), predane);
	}

	@Test
	public void stejnyKodSeZMimoProgramPredaZnovuPoNavratuAJinemObsahu() {
		sledovani.nastav(true);
		aktivni = true;
		vloz("jiný text");
		aktivni = false;
		sledovani.oknoSeZmenilo(false);
		provedFronty();
		vloz("GC19ACP");
		tik.run();
		provedFronty();
		assertEquals(Arrays.asList("GC19ACP"), predane);

		aktivni = true;
		sledovani.oknoSeZmenilo(true);
		vloz("něco zkopírovaného v programu");
		aktivni = false;
		sledovani.oknoSeZmenilo(false);
		provedFronty();
		vloz("GC19ACP");
		tik.run();
		provedFronty();
		assertEquals("týž kód zkopírovaný znovu mimo program se předá znovu", Arrays.asList("GC19ACP", "GC19ACP"), predane);
	}

	@Test
	public void prazdnyDlouhyNebeTextovyObsahSeNepredava() {
		sledovani.nastav(true);
		ohlas("");
		ohlas("   \n ");
		final StringBuilder dlouhy = new StringBuilder("GC19ACP ");
		while (dlouhy.length() <= SledovaniSchranky.NEJVIC_ZNAKU) {
			dlouhy.append("x");
		}
		ohlas(dlouhy.toString());
		schranka.setContents(new Transferable() {
			@Override
			public DataFlavor[] getTransferDataFlavors() {
				return new DataFlavor[] { DataFlavor.imageFlavor };
			}

			@Override
			public boolean isDataFlavorSupported(final DataFlavor flavor) {
				return DataFlavor.imageFlavor.equals(flavor);
			}

			@Override
			public Object getTransferData(final DataFlavor flavor) {
				return null;
			}
		}, null);
		sledovani.flavorsChanged(null);
		provedFronty();
		assertTrue(predane.isEmpty());
	}

	@Test
	public void chybyPriCteniSchrankyNicNeshodiAProgramPokracuje() {
		final RuntimeException[] selhani = { new IllegalStateException("Clipboard is currently unavailable") };
		final Throwable[] chyba = { null };
		final Clipboard vadna = new RucniSchranka("vadna") {
			@Override
			public boolean isDataFlavorAvailable(final DataFlavor flavor) {
				if (chyba[0] instanceof Error) {
					throw (Error) chyba[0];
				}
				if (selhani[0] != null) {
					throw selhani[0];
				}
				return super.isDataFlavorAvailable(flavor);
			}

			@Override
			public Object getData(final DataFlavor flavor) throws UnsupportedFlavorException, java.io.IOException {
				if (chyba[0] instanceof java.io.IOException) {
					throw (java.io.IOException) chyba[0];
				}
				return super.getData(flavor);
			}
		};
		sledovani = sledovani(vadna);
		sledovani.nastav(true);
		sledovani.flavorsChanged(null);
		provedFronty();
		selhani[0] = new IllegalArgumentException("špatná data");
		sledovani.flavorsChanged(null);
		provedFronty();
		selhani[0] = null;
		chyba[0] = new OutOfMemoryError("velký text");
		sledovani.flavorsChanged(null);
		provedFronty();
		chyba[0] = new java.io.IOException("nelze číst");
		vadna.setContents(new StringSelection("GC19ACP"), null);
		sledovani.flavorsChanged(null);
		provedFronty();
		assertTrue(predane.isEmpty());
		chyba[0] = null;
		sledovani.flavorsChanged(null);
		provedFronty();
		assertEquals("po odeznění chyby schránka funguje", Arrays.asList("GC19ACP"), predane);
	}

	@Test
	public void praceNaEdtSePoVypnutiNebeAktivaciNespusti() {
		sledovani.nastav(true);
		vloz("GC19ACP");
		sledovani.flavorsChanged(null);
		new ArrayList<>(pozadi).forEach(Runnable::run);
		pozadi.clear();
		assertEquals("práce čeká na vlákně událostí", 1, edt.size());
		sledovani.nastav(false);
		edt.forEach(Runnable::run);
		assertTrue("po vypnutí už se na kešoid nepřechází", predane.isEmpty());

		edt.clear();
		sledovani.nastav(true);
		vloz("GCBZJGC");
		sledovani.flavorsChanged(null);
		new ArrayList<>(pozadi).forEach(Runnable::run);
		pozadi.clear();
		aktivni = true;
		edt.forEach(Runnable::run);
		assertTrue("do programu se uživatel mezitím vrátil", predane.isEmpty());
	}

	@Test
	public void velkyTextSePriPravidelneKontroleNectePoRaz() {
		final Clipboard pocitajici = new RucniSchranka("pocitajici") {
			@Override
			public Object getData(final DataFlavor flavor) throws UnsupportedFlavorException, java.io.IOException {
				cteni++;
				return super.getData(flavor);
			}
		};
		sledovani = sledovani(pocitajici);
		sledovani.nastav(true);
		sledovani.oknoSeZmenilo(false);
		provedFronty();
		final StringBuilder dlouhy = new StringBuilder("GC19ACP ");
		while (dlouhy.length() <= SledovaniSchranky.NEJVIC_ZNAKU) {
			dlouhy.append("x");
		}
		pocitajici.setContents(new StringSelection(dlouhy.toString()), null);
		tik.run();
		provedFronty();
		final int cteniPoPrvnim = pocetCteni();
		for (int i = 0; i < SledovaniSchranky.TIKU_PO_VELKEM_TEXTU - 2; i++) {
			tik.run();
		}
		provedFronty();
		assertEquals("velký text se při dalších tiknutích nečte", cteniPoPrvnim, pocetCteni());
		assertTrue(predane.isEmpty());

		pocitajici.setContents(new StringSelection("GC19ACP"), null);
		sledovani.flavorsChanged(null);
		provedFronty();
		assertEquals("nový obsah schránky se zase čte", Arrays.asList("GC19ACP"), predane);
		pocitajici.setContents(new StringSelection("GCAAAAA"), null);
		tik.run();
		provedFronty();
		assertEquals("po změně formátu se pravidelná kontrola obnoví", Arrays.asList("GC19ACP", "GCAAAAA"), predane);
		predane.remove("GCAAAAA");
		pocitajici.setContents(new StringSelection(dlouhy.toString()), null);
		tik.run();
		provedFronty();
		aktivni = true;
		sledovani.oknoSeZmenilo(true);
		aktivni = false;
		sledovani.oknoSeZmenilo(false);
		provedFronty();
		pocitajici.setContents(new StringSelection("GCBZJGC"), null);
		tik.run();
		provedFronty();
		assertEquals("po návratu do programu se kontrola obnoví", Arrays.asList("GC19ACP", "GCBZJGC"), predane);
	}

	@Test
	public void kodPoVelkemTextuBezUdalostiSeZachytiNejpozdejiPoDesetiTiknutich() {
		final StringBuilder dlouhy = new StringBuilder("GC19ACP ");
		while (dlouhy.length() <= SledovaniSchranky.NEJVIC_ZNAKU) {
			dlouhy.append("x");
		}
		sledovani.nastav(true);
		sledovani.oknoSeZmenilo(false);
		provedFronty();
		vloz(dlouhy.toString());
		tik.run();
		provedFronty();
		vloz("GC19ACP");
		int tiku = 0;
		while (predane.isEmpty() && tiku < 30) {
			tik.run();
			provedFronty();
			tiku++;
		}
		assertEquals(Arrays.asList("GC19ACP"), predane);
		assertTrue("kód se zachytí nejpozději po " + SledovaniSchranky.TIKU_PO_VELKEM_TEXTU + " tiknutích, bylo " + tiku, tiku <= SledovaniSchranky.TIKU_PO_VELKEM_TEXTU);
	}

	private int pocetCteni() {
		return cteni;
	}

	@Test
	public void poslouchacOkenSePriOpakovaneUdalostiRegistrujeJednouAOdregistrujeSe() {
		final java.awt.KeyboardFocusManager manager = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
		final int puvodne = manager.getPropertyChangeListeners("activeWindow").length;
		final PrejdiNaKesoidZeSchrankyAction akce = new PrejdiNaKesoidZeSchrankyAction();
		akce.zaregistrujPoslouchaceOken();
		akce.zaregistrujPoslouchaceOken();
		assertEquals("druhé zapnutí poslouchač nezdvojí", puvodne + 1, manager.getPropertyChangeListeners("activeWindow").length);
		akce.odregistrujPoslouchaceOken();
		assertEquals("jediné odregistrování poslouchač odebere", puvodne, manager.getPropertyChangeListeners("activeWindow").length);
		akce.odregistrujPoslouchaceOken();
		assertEquals(puvodne, manager.getPropertyChangeListeners("activeWindow").length);
	}

	@Test
	public void akceBezZapnutiNevytvariSledovani() {
		final PrejdiNaKesoidZeSchrankyAction akce = new PrejdiNaKesoidZeSchrankyAction();
		akce.onEvent(new SledovaniSchrankyEvent(false));
		assertFalse("při vypnuté volbě se sledování ani schránka nevytvoří", akce.sledovaniVytvoreno());
		assertFalse(akce.isSelected());
	}

	@Test
	public void akcePrejdeNaNalezenyKesoidJenKdyzJe() {
		final Wpt wpt = mock(Wpt.class);
		final Kesoid kes = mock(Kesoid.class);
		when(kes.getIdentifier()).thenReturn("GC19ACP");
		when(kes.getMainWpt()).thenReturn(wpt);
		final KesBag bag = mock(KesBag.class);
		when(bag.getKesoidy()).thenReturn(Arrays.asList(kes));
		final PoziceModel pozice = mock(PoziceModel.class);
		final VyrezModel vyrez = mock(VyrezModel.class);
		final PrejdiNaKesoidZeSchrankyAction akce = new PrejdiNaKesoidZeSchrankyAction();
		akce.inject(pozice);
		akce.inject(vyrez);

		assertFalse("data ještě nejsou načtená", akce.prejdiNa("GC19ACP"));
		akce.onEvent(new KeskyNactenyEvent(bag));
		assertFalse("text bez kódu", akce.prejdiNa("obyčejná věta"));
		assertFalse("kód, který v datech není", akce.prejdiNa("https://coord.info/GCZZZZZ"));
		verify(pozice, never()).setPoziceNaObjekt(wpt);
		assertTrue(akce.prejdiNa("https://coord.info/GC19ACP_nazev"));
		verify(pozice).setPoziceNaObjekt(wpt);
		verify(vyrez).vystredovatNaPozici();
	}
}
