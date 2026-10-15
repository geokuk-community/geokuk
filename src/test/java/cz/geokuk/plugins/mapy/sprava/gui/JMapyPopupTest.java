package cz.geokuk.plugins.mapy.sprava.gui;

import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;

import javax.swing.*;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.program.PrehledovaMapa;

/** Popup Mapy: řádky podle stavu, akce, odkazy na weby a čtení složky offline map. */
public class JMapyPopupTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final long MB = 1_000_000L;

	private static RadekMapy.Builder katalog(final String id, final RadekMapy.Stav stav) {
		return RadekMapy.katalogova(id, id, stav).velikost(600 * MB).zdroj("download.mapsforge.org").licence("ODbL 1.0");
	}

	private static List<JButton> tlacitka(final Container c) {
		final List<JButton> v = new ArrayList<>();
		for (final Component k : c.getComponents()) {
			if (k instanceof JButton) {
				v.add((JButton) k);
			}
			if (k instanceof Container) {
				v.addAll(tlacitka((Container) k));
			}
		}
		return v;
	}

	private static JButton tlacitko(final Container c, final String text) {
		return tlacitka(c).stream().filter(b -> text.equals(b.getText())).findFirst().orElse(null);
	}

	private static final class Zaznam implements OvladaniMap {
		final List<String> volani = new ArrayList<>();

		@Override
		public void stahni(final String id) {
			volani.add("stahni " + id);
		}

		@Override
		public void zrusStahovani() {
			volani.add("zrus");
		}

		@Override
		public void zkontroluj(final String id) {
			volani.add("zkontroluj " + id);
		}

		@Override
		public void otevriSlozku() {
			volani.add("slozka");
		}
	}

	@Test
	public void akceRadkuPodleStavu() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			final Zaznam z = new Zaznam();
			p.setOvladani(z);
			p.obnov(Arrays.asList(katalog("cz", RadekMapy.Stav.AKTUALNI).datumDat(Instant.EPOCH).build(), katalog("sk", RadekMapy.Stav.NOVEJSI).datumDat(Instant.EPOCH).build(),
					katalog("at", RadekMapy.Stav.NEMAME).proZobrazeneMisto(true).build(), katalog("pl", RadekMapy.Stav.NEMAME).rozpracovano(true).build(),
					RadekMapy.vlastni("paws.map").build()));
			tlacitko(p, JMapyPopup.AKTUALIZOVAT).doClick();
			tlacitko(p, JMapyPopup.STAHNOUT).doClick();
			tlacitko(p, JMapyPopup.POKRACOVAT).doClick();
			tlacitko(p, JMapyPopup.ZKONTROLOVAT).doClick();
			tlacitko(p, JMapyPopup.OTEVRIT_SLOZKU).doClick();
			Assert.assertEquals(Arrays.asList("stahni sk", "stahni at", "stahni pl", "zkontroluj cz", "zkontroluj sk", "slozka"), z.volani);
		});
	}

	@Test
	public void behemStahovaniNejdeStahovatJinou() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			final Zaznam z = new Zaznam();
			p.setOvladani(z);
			p.obnov(Arrays.asList(katalog("at", RadekMapy.Stav.STAHUJE).stazeno(300 * MB).build(), katalog("pl", RadekMapy.Stav.NEMAME).build()));
			Assert.assertFalse(tlacitko(p, JMapyPopup.STAHNOUT).isEnabled());
			tlacitko(p, JMapyPopup.ZRUSIT).doClick();
			Assert.assertEquals(Collections.singletonList("zrus"), z.volani);
		});
	}

	/** Při stahování chodí změny často: popup se nepřestaví (posuvník seznamu zůstane), jen se přepíše popis. */
	@Test
	public void prubehStahovaniJenPrepisePopis() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			final int[] prestaveni = new int[1];
			p.setPoZmeneObsahu(() -> prestaveni[0]++);
			p.obnov(Arrays.asList(katalog("at", RadekMapy.Stav.STAHUJE).stazeno(60 * MB).build()));
			final JButton zrusit = tlacitko(p, JMapyPopup.ZRUSIT);
			p.obnov(Arrays.asList(katalog("at", RadekMapy.Stav.STAHUJE).stazeno(300 * MB).build()));
			Assert.assertSame(zrusit, tlacitko(p, JMapyPopup.ZRUSIT));
			Assert.assertEquals(1, prestaveni[0]);
			Assert.assertTrue(texty(p).contains("50 % z 600 MB"));
			p.obnov(Arrays.asList(katalog("at", RadekMapy.Stav.AKTUALNI).datumDat(Instant.EPOCH).build()));
			Assert.assertEquals(2, prestaveni[0]);
			Assert.assertNull(tlacitko(p, JMapyPopup.ZRUSIT));
		});
	}

	private static List<String> texty(final Container c) {
		final List<String> v = new ArrayList<>();
		for (final Component k : c.getComponents()) {
			if (k instanceof JLabel) {
				v.add(((JLabel) k).getText());
			}
			if (k instanceof Container) {
				v.addAll(texty((Container) k));
			}
		}
		return v;
	}

	/** Weby s mapami jen úvodní stránkou: OpenAndroMaps nedovoluje přímé odkazy na stažení. */
	@Test
	public void odkazyVedouNaUvodniStranky() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			int odkazu = 0;
			for (final JButton b : tlacitka(p)) {
				if (b.getToolTipText() != null && b.getToolTipText().startsWith("http")) {
					final URI u = URI.create(b.getToolTipText());
					Assert.assertTrue(b.getToolTipText(), u.getPath() == null || u.getPath().isEmpty() || "/".equals(u.getPath()));
					odkazu++;
				}
			}
			Assert.assertEquals(2, odkazu);
		});
	}

	@Test
	public void souhrnBlokuBereNejdulezitejsiStav() {
		final RadekMapy ok = katalog("cz", RadekMapy.Stav.AKTUALNI).build();
		final RadekMapy nova = katalog("sk", RadekMapy.Stav.NOVEJSI).build();
		final RadekMapy chyba = RadekMapy.vlastni("x.map").chyba("rozbitá").build();
		final RadekMapy stahuje = katalog("at", RadekMapy.Stav.STAHUJE).build();
		final RadekMapy nemame = katalog("pl", RadekMapy.Stav.NEMAME).build();
		Assert.assertEquals(RadekMapy.Stav.NEMAME, JBlokMap.souhrn(Collections.singletonList(nemame)));
		Assert.assertEquals(RadekMapy.Stav.AKTUALNI, JBlokMap.souhrn(Arrays.asList(nemame, ok)));
		Assert.assertEquals(RadekMapy.Stav.NOVEJSI, JBlokMap.souhrn(Arrays.asList(ok, nova)));
		Assert.assertEquals(RadekMapy.Stav.CHYBA, JBlokMap.souhrn(Arrays.asList(ok, nova, chyba)));
		Assert.assertEquals(RadekMapy.Stav.STAHUJE, JBlokMap.souhrn(Arrays.asList(ok, chyba, stahuje)));
	}

	@Test
	public void souboryVeSlozce() throws Exception {
		final File slozka = tmp.newFolder("offline-mapy");
		Files.copy(new File("src/test/resources/offline-mapy/kukov.map").toPath(), new File(slozka, "kukov.map").toPath());
		Files.write(new File(slozka, "rozbita.map").toPath(), new byte[5000]);
		Files.write(new File(slozka, PrehledovaMapa.SOUBOR).toPath(), new byte[10]);
		Files.write(new File(slozka, "tema.zip").toPath(), new byte[10]);
		final List<RadekMapy> radky = SouboryMap.nacti(slozka);
		Assert.assertEquals(2, radky.size());
		final RadekMapy kukov = radky.get(0);
		Assert.assertEquals("kukov.map", kukov.getNazev());
		Assert.assertEquals(RadekMapy.Stav.AKTUALNI, kukov.getStav());
		Assert.assertNotNull(kukov.datumDat);
		Assert.assertTrue(kukov.isVlastni());
		Assert.assertEquals(RadekMapy.Stav.CHYBA, radky.get(1).getStav());
		Assert.assertTrue(radky.get(1).isVeSlozce());
		Assert.assertTrue(SouboryMap.nacti(new File(slozka, "neni")).isEmpty());
	}
}
