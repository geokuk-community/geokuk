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

	private final Clipboard schranka = new Clipboard("test");
	private final List<String> predane = new ArrayList<>();
	private final List<Runnable> fronta = new ArrayList<>();
	private boolean aktivni;

	private final SledovaniSchranky sledovani = new SledovaniSchranky(schranka, () -> aktivni, t -> {
		predane.add(t);
		return true;
	}, fronta::add);

	private void vlozAOhlas(final String text) {
		schranka.setContents(new StringSelection(text), null);
		sledovani.flavorsChanged(null);
	}

	private void provedFrontu() {
		new ArrayList<>(fronta).forEach(Runnable::run);
		fronta.clear();
	}

	@Test
	public void vypnutoNicNesledujeAUdalostSeIgnoruje() {
		assertEquals(0, schranka.getFlavorListeners().length);
		vlozAOhlas("GC19ACP");
		provedFrontu();
		assertTrue(predane.isEmpty());
	}

	@Test
	public void zapnutiAVypnutiRegistrujeAOdregistrujeListener() {
		sledovani.nastav(true);
		assertEquals(1, schranka.getFlavorListeners().length);
		sledovani.nastav(true);
		assertEquals("opakované zapnutí listener nezdvojí", 1, schranka.getFlavorListeners().length);
		sledovani.nastav(false);
		assertEquals(0, schranka.getFlavorListeners().length);
		sledovani.nastav(false);
		assertEquals(0, schranka.getFlavorListeners().length);
	}

	@Test
	public void neaktivniOknoPredaTextAOpakovaniIgnoruje() {
		sledovani.nastav(true);
		vlozAOhlas("https://coord.info/GCBZJGC");
		provedFrontu();
		assertEquals(Arrays.asList("https://coord.info/GCBZJGC"), predane);
		vlozAOhlas("https://coord.info/GCBZJGC");
		provedFrontu();
		assertEquals("stejná hodnota se nepředá znovu", 1, predane.size());
		vlozAOhlas("GC19ACP");
		provedFrontu();
		assertEquals(Arrays.asList("https://coord.info/GCBZJGC", "GC19ACP"), predane);
		vlozAOhlas("https://coord.info/GCBZJGC");
		provedFrontu();
		assertEquals("po jiné hodnotě se původní předá znovu", 3, predane.size());
	}

	@Test
	public void aktivniOknoSchrankuIgnoruje() {
		sledovani.nastav(true);
		aktivni = true;
		vlozAOhlas("GC19ACP");
		provedFrontu();
		assertTrue("kopírování v programu se neřeší", predane.isEmpty());
		aktivni = false;
		vlozAOhlas("GC19ACP");
		provedFrontu();
		assertEquals("po opuštění programu už ano", 1, predane.size());
	}

	@Test
	public void prazdnyDlouhyNebeTextovyObsahSeNepredava() {
		sledovani.nastav(true);
		vlozAOhlas("");
		vlozAOhlas("   \n ");
		final StringBuilder dlouhy = new StringBuilder("GC19ACP ");
		while (dlouhy.length() <= SledovaniSchranky.NEJVIC_ZNAKU) {
			dlouhy.append("x");
		}
		vlozAOhlas(dlouhy.toString());
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
		provedFrontu();
		assertTrue(predane.isEmpty());
	}

	@Test
	public void schrankaDrzenaJinouAplikaciNeshodiNic() {
		final Clipboard obsazena = new Clipboard("obsazena") {
			@Override
			public boolean isDataFlavorAvailable(final DataFlavor flavor) {
				throw new IllegalStateException("Clipboard is currently unavailable");
			}
		};
		final Clipboard selhava = new Clipboard("selhava") {
			@Override
			public boolean isDataFlavorAvailable(final DataFlavor flavor) {
				return true;
			}

			@Override
			public Object getData(final DataFlavor flavor) throws java.io.IOException {
				throw new java.io.IOException("nelze číst");
			}
		};
		for (final Clipboard c : new Clipboard[] { obsazena, selhava }) {
			final List<String> p = new ArrayList<>();
			final SledovaniSchranky s = new SledovaniSchranky(c, () -> false, t -> p.add(t), Runnable::run);
			s.nastav(true);
			s.flavorsChanged(null);
			assertTrue(p.isEmpty());
		}
	}

	@Test
	public void praceNaEdtSePoVypnutiNespusti() {
		sledovani.nastav(true);
		vlozAOhlas("GC19ACP");
		assertEquals("práce čeká na vlákně událostí", 1, fronta.size());
		assertTrue(predane.isEmpty());
		sledovani.nastav(false);
		provedFrontu();
		assertTrue("po vypnutí už se na kešoid nepřechází", predane.isEmpty());
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
		verify(pozice, never()).setPozice(wpt);
		assertTrue(akce.prejdiNa("https://coord.info/GC19ACP_nazev"));
		verify(pozice).setPozice(wpt);
		verify(vyrez).vystredovatNaPozici();
	}
}
