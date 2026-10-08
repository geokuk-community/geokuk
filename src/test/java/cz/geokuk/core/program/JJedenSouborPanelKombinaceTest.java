package cz.geokuk.core.program;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import cz.geokuk.util.file.Filex;

/**
 * Uložit v Umístění souborů pro každou kombinaci: složka jiného programu (GeoGet, GSAK, OpenSAK) nebo vlastní složka GeoKuku, aktivní nebo vypnutá, a stav
 * složky na disku.
 */
@RunWith(Parameterized.class)
public class JJedenSouborPanelKombinaceTest {

	enum Stav {
		NEEXISTUJE, PRAZDNA, S_DATY, SOUBOR, RODIC_JE_SOUBOR, NECITELNA, NEODPOVIDA, VYJIMKA, DIAKRITIKA_NEEXISTUJE, DIAKRITIKA_EXISTUJE
	}

	enum Druh {
		JINY_PROGRAM, VLASTNI
	}

	@Parameters(name = "{0} {1} aktivní={2}")
	public static Collection<Object[]> kombinace() {
		final List<Object[]> k = new ArrayList<>();
		for (final Stav s : Stav.values()) {
			k.add(new Object[] { Druh.JINY_PROGRAM, s, true });
			k.add(new Object[] { Druh.JINY_PROGRAM, s, false });
			// Vlastní složky GeoKuku vypnout nejde.
			k.add(new Object[] { Druh.VLASTNI, s, true });
		}
		return k;
	}

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final Druh druh;
	private final Stav stav;
	private final boolean aktivni;

	private Function<File, JJedenSouborPanel.StavSlozky> puvodniKontrola;
	private long puvodniLimit;

	public JJedenSouborPanelKombinaceTest(final Druh druh, final Stav stav, final boolean aktivni) {
		this.druh = druh;
		this.stav = stav;
		this.aktivni = aktivni;
	}

	@Before
	public void zapamatuj() {
		puvodniKontrola = JJedenSouborPanel.kontrola;
		puvodniLimit = JJedenSouborPanel.limitMs;
	}

	@After
	public void vrat() {
		JJedenSouborPanel.kontrola = puvodniKontrola;
		JJedenSouborPanel.limitMs = puvodniLimit;
	}

	@Test(timeout = 10_000)
	public void ulozit() throws Exception {
		final File slozka = priprav();
		final boolean existovala = slozka.exists();
		final JJedenSouborPanel panel = druh == Druh.JINY_PROGRAM ? new JJedenSouborPanel(null, "Datová složka GeoGetu.", true, true, true).nezakladat()
				: new JJedenSouborPanel(null, "Složka pro rendrování obrázků map", true, true, false);
		panel.setFilex(new Filex(slozka, false, aktivni));

		final String ocekavanaChyba = ocekavanaChyba();
		final long zacatek = System.nanoTime();
		try {
			final Filex f = panel.vezmiSouborAProver();
			Assert.assertNull("uložení mělo skončit chybou: " + ocekavanaChyba, ocekavanaChyba);
			Assert.assertEquals(slozka.getCanonicalFile(), f.getEffectiveFile().getCanonicalFile());
		} catch (final JPrehledSouboru.YNejdeTo e) {
			Assert.assertNotNull("nečekaná chyba: " + e.getMessage(), ocekavanaChyba);
			Assert.assertTrue(e.getMessage(), e.getMessage().contains(ocekavanaChyba));
			Assert.assertTrue("v hlášce má být cesta, jak ji systém zná: " + e.getMessage(), e.getMessage().contains(slozka.getName()));
			Assert.assertFalse("hláška bez jména třídy: " + e.getMessage(), e.getMessage().matches(".*\\b(java|javax)\\.[a-z]+\\..*"));
		}
		Assert.assertTrue("uložení čekalo příliš dlouho", System.nanoTime() - zacatek < 2_000_000_000L);

		final boolean zalozi = aktivni && druh == Druh.VLASTNI && (stav == Stav.NEEXISTUJE || stav == Stav.DIAKRITIKA_NEEXISTUJE);
		if (zalozi) {
			Assert.assertTrue("vlastní složka se má založit", slozka.isDirectory());
		} else if (stav != Stav.NEODPOVIDA && stav != Stav.VYJIMKA) {
			Assert.assertEquals("složka se nemá zakládat ani mazat", existovala, slozka.exists());
		}
	}

	/** Kus textu, který musí hláška obsahovat, nebo {@code null}, když uložení projde. */
	private String ocekavanaChyba() {
		if (!aktivni) {
			return null;
		}
		switch (stav) {
		case PRAZDNA:
		case S_DATY:
		case DIAKRITIKA_EXISTUJE:
			return null;
		case NEODPOVIDA:
			return "neodpovídá";
		case VYJIMKA:
			return "nejde prověřit: Přístup odepřen";
		case NEEXISTUJE:
		case DIAKRITIKA_NEEXISTUJE:
			return druh == Druh.VLASTNI ? null : "neexistuje nebo není dostupná";
		case RODIC_JE_SOUBOR:
			return druh == Druh.VLASTNI ? "nepodařilo vytvořit" : "neexistuje nebo není dostupná";
		case SOUBOR:
		case NECITELNA:
			return druh == Druh.VLASTNI ? "nepodařilo vytvořit" : "není čitelná složka";
		default:
			throw new IllegalStateException(stav.name());
		}
	}

	private File priprav() throws IOException {
		final File koren = tmp.getRoot();
		switch (stav) {
		case NEEXISTUJE:
			return new File(koren, "neni/geoget");
		case PRAZDNA:
			return tmp.newFolder("prazdna");
		case S_DATY: {
			final File d = tmp.newFolder("geoget");
			Files.write(new File(d, "geoget.db3").toPath(), new byte[] { 1 });
			return d;
		}
		case SOUBOR:
			return tmp.newFile("soubor");
		case RODIC_JE_SOUBOR:
			return new File(tmp.newFile("rodic"), "geoget");
		case NECITELNA: {
			final File d = tmp.newFolder("necitelna");
			Assume.assumeTrue("nelze odebrat právo číst", d.setReadable(false, false) && d.setExecutable(false, false));
			Assume.assumeFalse("správce čte i bez práva", d.canRead());
			return d;
		}
		case NEODPOVIDA:
			JJedenSouborPanel.limitMs = 200;
			JJedenSouborPanel.kontrola = d -> {
				try {
					Thread.sleep(30_000);
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return JJedenSouborPanel.StavSlozky.CITELNA;
			};
			return new File(koren, "nas/geoget");
		case VYJIMKA:
			JJedenSouborPanel.kontrola = d -> {
				throw new SecurityException("Přístup odepřen");
			};
			return new File(koren, "zakazana");
		case DIAKRITIKA_NEEXISTUJE:
			return new File(koren, "Příliš žluťoučký kůň/Nová ěščřž geoget");
		case DIAKRITIKA_EXISTUJE:
			return tmp.newFolder("Příliš žluťoučký kůň", "Kešky č. 1");
		default:
			throw new IllegalStateException(stav.name());
		}
	}

	@After
	public void vratPrava() {
		final File d = new File(tmp.getRoot(), "necitelna");
		d.setReadable(true, false);
		d.setExecutable(true, false);
	}
}
