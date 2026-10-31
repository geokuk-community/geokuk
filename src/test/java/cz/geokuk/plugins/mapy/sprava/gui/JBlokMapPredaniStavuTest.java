package cz.geokuk.plugins.mapy.sprava.gui;

import java.awt.KeyboardFocusManager;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

import javax.swing.*;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.PokrytiMap;
import cz.geokuk.util.gui.PopupNadBlokem;

/** Blok Mapy při vytvoření popupu předá stav, který mezitím nasbíral, a zapojí zpětná volání do popupu i do okna nad blokem. */
public class JBlokMapPredaniStavuTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static Object pole(final Object o, final String jmeno) throws Exception {
		Class<?> c = o.getClass();
		while (c != null) {
			try {
				final Field f = c.getDeclaredField(jmeno);
				f.setAccessible(true);
				return f.get(o);
			} catch (final NoSuchFieldException e) {
				c = c.getSuperclass();
			}
		}
		throw new NoSuchFieldException(jmeno);
	}

	private static void nastav(final Object o, final String jmeno, final Object hodnota) throws Exception {
		final Field f = o.getClass().getDeclaredField(jmeno);
		f.setAccessible(true);
		f.set(o, hodnota);
	}

	private List<PokrytiMap.DvojitaMapa> dvojiteMapy() throws Exception {
		final File slozka = tmp.newFolder("dvojite");
		final KatalogMap katalog = KatalogMap.vestaveny();
		for (final String id : new String[] { "europe/germany", "europe/germany/bayern", "europe/germany/sachsen" }) {
			Assert.assertTrue(new File(slozka, katalog.podleId(id).get().getNazevSouboru()).createNewFile());
		}
		final List<PokrytiMap.DvojitaMapa> dvojite = PokrytiMap.dvojiteMapy(slozka, katalog);
		Assert.assertEquals(1, dvojite.size());
		return dvojite;
	}

	/** Ovládání, volné místo, dvojité mapy a dostupnost stínování, které blok znal před vznikem popupu, popup dostane. */
	@Test
	public void popupDostaneStavBloku() throws Exception {
		final List<PokrytiMap.DvojitaMapa> dvojite = dvojiteMapy();
		final OvladaniMap ovladani = new OvladaniMap() {
			@Override
			public void otevriSlozku() {}

			@Override
			public boolean isObrysPokryti() {
				return true;
			}

			@Override
			public boolean isStinovani() {
				return false;
			}
		};
		SwingUtilities.invokeAndWait(() -> {
			try {
				final JBlokMap blok = new JBlokMap();
				blok.setOvladani(ovladani);
				nastav(blok, "stinovaniDostupne", true);
				nastav(blok, "volne", 123_456_789L);
				nastav(blok, "dvojite", dvojite);
				Assert.assertFalse(blok.isObsahVytvoren());
				final JMapyPopup obsah = blok.getObsah();
				Assert.assertSame("ovládání", ovladani, pole(obsah, "ovladani"));
				Assert.assertEquals("volné místo", 123_456_789L, pole(obsah, "volneMisto"));
				Assert.assertSame("dvojité mapy", dvojite, pole(obsah, "dvojite"));
				Assert.assertEquals("stínování dostupné", true, pole(obsah, "stinovaniDostupne"));
				Assert.assertFalse("přepínač stínování podle ovládání", ((JCheckBox) pole(obsah, "stinovani")).isSelected());
				Assert.assertTrue("přepínač obrysu podle ovládání", ((JCheckBox) pole(obsah, "obrys")).isSelected());
			} catch (final Exception e) {
				throw new AssertionError(e);
			}
		});
	}

	/** Ovládání nastavené až po vzniku popupu se do popupu dostane také. */
	@Test
	public void ovladaniPoVzniku() throws Exception {
		final OvladaniMap ovladani = new OvladaniMap() {
			@Override
			public void otevriSlozku() {}
		};
		SwingUtilities.invokeAndWait(() -> {
			try {
				final JBlokMap blok = new JBlokMap();
				final JMapyPopup obsah = blok.getObsah();
				Assert.assertNull(pole(obsah, "ovladani"));
				blok.setOvladani(ovladani);
				Assert.assertSame(ovladani, pole(obsah, "ovladani"));
			} catch (final Exception e) {
				throw new AssertionError(e);
			}
		});
	}

	/** Stav oddílu Ve složce a rozbalené země popup čte z nastavení a ukládá do něj. */
	@Test
	public void popupCteAUkladaNastaveni() throws Exception {
		final Optional<Boolean> puvodniSlozka = JBlokMap.nactiStavSlozky();
		final Set<String> puvodniVsechny = JBlokMap.nactiRozbaleneZeme(JMapyPopup.ROZBALENE_VSECHNY);
		final Set<String> puvodniVeSlozce = JBlokMap.nactiRozbaleneZeme(JMapyPopup.ROZBALENE_SLOZKA);
		try {
			JBlokMap.ulozStavSlozky(false);
			JBlokMap.ulozRozbaleneZeme(JMapyPopup.ROZBALENE_VSECHNY, new HashSet<>(Arrays.asList("Německo")));
			JBlokMap.ulozRozbaleneZeme(JMapyPopup.ROZBALENE_SLOZKA, new HashSet<>(Arrays.asList("Rakousko")));
			SwingUtilities.invokeAndWait(() -> {
				try {
					final JMapyPopup obsah = new JBlokMap().getObsah();
					Assert.assertEquals("čte stav složky", Optional.of(false), obsah.nactiStavSlozky.get());
					obsah.ulozStavSlozky.accept(true);
					Assert.assertEquals("ukládá stav složky", Optional.of(true), JBlokMap.nactiStavSlozky());
					Assert.assertEquals("načtené rozbalené země", Collections.singleton("Německo"), pole(obsah, "rozbalene"));
					Assert.assertEquals("načtené rozbalené země ve složce", Collections.singleton("Rakousko"), pole(obsah, "rozbaleneSlozka"));
					@SuppressWarnings("unchecked")
					final java.util.function.BiConsumer<String, Set<String>> uloz = (java.util.function.BiConsumer<String, Set<String>>) pole(obsah, "ulozRozbalene");
					uloz.accept(JMapyPopup.ROZBALENE_VSECHNY, Collections.singleton("Polsko"));
					Assert.assertEquals("ukládá rozbalené země", Collections.singleton("Polsko"), JBlokMap.nactiRozbaleneZeme(JMapyPopup.ROZBALENE_VSECHNY));
				} catch (final Exception e) {
					throw new AssertionError(e);
				}
			});
		} finally {
			JBlokMap.ulozRozbaleneZeme(JMapyPopup.ROZBALENE_VSECHNY, puvodniVsechny);
			JBlokMap.ulozRozbaleneZeme(JMapyPopup.ROZBALENE_SLOZKA, puvodniVeSlozce);
			if (puvodniSlozka.isPresent()) {
				JBlokMap.ulozStavSlozky(puvodniSlozka.get());
			} else {
				cz.geokuk.framework.MyPreferences.current().node(JBlokMap.PREFE_NODE).remove(JBlokMap.PREFE_SLOZKA_VOLBA);
			}
		}
	}

	private static KeyboardFocusManager spravceSFokusem(final java.awt.Component vlastnik) {
		return new java.awt.DefaultKeyboardFocusManager() {
			@Override
			public java.awt.Component getFocusOwner() {
				return vlastnik;
			}
		};
	}

	/** Okno nad blokem drží popup otevřený jen při hledání a po zavření popupu hledání vymaže; popup ví, jestli je zobrazený. */
	@Test
	public void oknoNadBlokemJeZapojeno() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			try {
				final JBlokMap blok = new JBlokMap();
				final JMapyPopup obsah = blok.getObsah();
				final PopupNadBlokem popup = (PopupNadBlokem) pole(blok, "popup");
				final java.util.function.BooleanSupplier drzOtevreny = (java.util.function.BooleanSupplier) pole(popup, "drzOtevreny");
				final Runnable poZavreni = (Runnable) pole(popup, "poZavreni");
				final java.util.function.BooleanSupplier zobrazeny = (java.util.function.BooleanSupplier) pole(obsah, "zobrazeny");

				final java.awt.Component hledat = (java.awt.Component) pole(obsah, "hledat");
				final java.util.function.Supplier<KeyboardFocusManager> puvodni = obsah.spravceFokusu;
				try {
					obsah.spravceFokusu = () -> spravceSFokusem(obsah);
					Assert.assertEquals("držení podle hledání", obsah.isHledaniAktivni(), drzOtevreny.getAsBoolean());
					Assert.assertTrue(drzOtevreny.getAsBoolean());
					obsah.spravceFokusu = () -> spravceSFokusem(null);
					Assert.assertFalse(drzOtevreny.getAsBoolean());
				} finally {
					obsah.spravceFokusu = puvodni;
				}

				final JTextField text = (JTextField) hledat;
				text.setText("Německo");
				poZavreni.run();
				Assert.assertEquals("po zavření se hledání vymaže", "", text.getText());

				Assert.assertFalse("popup není zobrazený", zobrazeny.getAsBoolean());
			} catch (final Exception e) {
				throw new AssertionError(e);
			}
		});
	}

	/** Popup je podle bloku zobrazený od ukaz() do zavri() a dostupná výška je místo nad blokem po horní lištu okna. */
	@Test(timeout = 30000)
	public void zobrazenyADostupnaVyska() throws Exception {
		Assume.assumeFalse("bez displeje", java.awt.GraphicsEnvironment.isHeadless());
		final File slozka = tmp.newFolder("zobrazeny");
		final JFrame okno = new JFrame();
		final JBlokMap[] blok = new JBlokMap[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				blok[0] = new JBlokMap();
				blok[0].setZdrojSlozky(() -> slozka);
				blok[0].cteniSlozky = f -> Collections.emptyList();
				final JPanel lista = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
				lista.add(blok[0]);
				okno.add(lista, java.awt.BorderLayout.SOUTH);
				okno.setBounds(0, 60, 1300, 850);
				okno.setVisible(true);
			});
			final long konec = System.currentTimeMillis() + 10_000;
			final boolean[] ukazuje = new boolean[1];
			while (System.currentTimeMillis() < konec && !ukazuje[0]) {
				SwingUtilities.invokeAndWait(() -> ukazuje[0] = blok[0].isShowing());
				if (!ukazuje[0]) {
					Thread.sleep(50);
				}
			}
			Assert.assertTrue("blok je na obrazovce", ukazuje[0]);
			SwingUtilities.invokeAndWait(() -> {
				try {
					final JMapyPopup obsah = blok[0].getObsah();
					final PopupNadBlokem popup = (PopupNadBlokem) pole(blok[0], "popup");
					final java.util.function.IntSupplier vyska = (java.util.function.IntSupplier) pole(obsah, "dostupnaVyska");
					final java.util.function.BooleanSupplier zobrazeny = (java.util.function.BooleanSupplier) pole(obsah, "zobrazeny");
					final int ocekavana = blok[0].getLocationOnScreen().y - popup.horniHranice();
					Assert.assertTrue("nad blokem je místo", ocekavana > 0);
					Assert.assertEquals(ocekavana, vyska.getAsInt());
					Assert.assertFalse(zobrazeny.getAsBoolean());
					blok[0].ukaz();
					Assert.assertSame(obsah, popup.viditelny());
					Assert.assertTrue("zobrazený od ukaz()", zobrazeny.getAsBoolean());
					blok[0].zavri();
					Assert.assertFalse("po zavri() ne", zobrazeny.getAsBoolean());
				} catch (final Exception e) {
					throw new AssertionError(e);
				}
			});
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				blok[0].zavri();
				okno.dispose();
			});
		}
	}
}
