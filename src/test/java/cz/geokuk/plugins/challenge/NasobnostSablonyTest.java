package cz.geokuk.plugins.challenge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import cz.geokuk.framework.Event0;
import cz.geokuk.framework.EventFirer;
import cz.geokuk.plugins.challenge.NastaveniChallengeTest.MapoveUloziste;

/** Násobnost matice D/T jako nastavení uživatele: i u šablony, uložení, načtení, výsledek po změně. */
public class NasobnostSablonyTest {

	private static final String MATICE = Sablony.maticeDT().nazev();

	private ChallengeModel model;
	private MapoveUloziste uloziste;
	private final List<ChallengeZmenenyEvent> udalosti = new ArrayList<>();

	@Before
	public void setUp() {
		model = new ChallengeModel();
		model.inject(new EventFirer() {
			@Override
			public void fire(final Event0<?> event) {
				udalosti.add((ChallengeZmenenyEvent) event);
			}
		});
		uloziste = new MapoveUloziste();
		model.setUloziste(() -> uloziste);
		model.initAfterInject();
	}

	private static void pockejNaEdt() throws Exception {
		SwingUtilities.invokeAndWait(() -> {});
	}

	@Test
	public void sablonaMaNasobnostUzivateleAleSablonaVKoduZustaneJednou() {
		final ChallengeSeznam s = new ChallengeSeznam();
		Assert.assertEquals(1, s.najdi(MATICE).opakovani());
		final Challenge nova = s.nastavNasobnostSablony(MATICE, 3);
		Assert.assertEquals(3, nova.opakovani());
		Assert.assertEquals(3, s.najdi(MATICE).opakovani());
		Assert.assertEquals(3, s.najdi(MATICE.toUpperCase(java.util.Locale.ROOT)).opakovani());
		Assert.assertTrue("je stále šablona", s.jeSablona(MATICE));
		Assert.assertEquals("šablona v kódu beze změny", 1, Sablony.maticeDT().opakovani());
		for (final List<Challenge> seznam : Arrays.asList(s.sablony(), s.vsechny())) {
			for (final Challenge c : seznam) {
				Assert.assertEquals(c.nazev(), c.nazev().equals(MATICE) ? 3 : 1, c.opakovani());
			}
		}
		s.aktivuj(MATICE);
		Assert.assertEquals("aktivní challenge je s násobností", 3, s.aktivni().get(0).opakovani());
		s.nastavNasobnostSablony(MATICE, 1);
		Assert.assertEquals(1, s.najdi(MATICE).opakovani());
		Assert.assertTrue(s.nasobnostiSablon().isEmpty());
	}

	@Test
	public void kopieSablonyPrevezmeAktualniNasobnost() {
		final ChallengeSeznam s = new ChallengeSeznam();
		s.nastavNasobnostSablony(MATICE, 4);
		final Challenge kopie = s.kopieSablony(MATICE);
		Assert.assertEquals(4, kopie.opakovani());
		Assert.assertFalse(s.jeSablona(kopie.nazev()));
		s.nastavNasobnostSablony(MATICE, 1);
		Assert.assertEquals("kopie je samostatná", 4, s.najdi(kopie.nazev()).opakovani());
	}

	@Test
	public void neplatnaNasobnostASablonaBezMaticeSeOdmitnou() {
		final ChallengeSeznam s = new ChallengeSeznam();
		for (final int n : new int[] { 0, -1, 10 }) {
			try {
				s.nastavNasobnostSablony(MATICE, n);
				Assert.fail("násobnost " + n);
			} catch (final ChallengeChyba e) {
				// očekáváno
			}
		}
		try {
			s.nastavNasobnostSablony(Sablony.od360Domova().nazev(), 2);
			Assert.fail("360° nemá násobnost");
		} catch (final ChallengeChyba e) {
			// očekáváno
		}
		try {
			s.nastavNasobnostSablony("Neexistuje", 2);
			Assert.fail("neexistující šablona");
		} catch (final ChallengeChyba e) {
			// očekáváno
		}
		Assert.assertTrue(s.nasobnostiSablon().isEmpty());
	}

	@Test
	public void nasobnostSablonyPrezijeUlozeniANacteniAJednickaKlicSmaze() {
		final ChallengeSeznam s = new ChallengeSeznam();
		s.nastavNasobnostSablony(MATICE, 3);
		NastaveniChallenge.uloz(s, false, uloziste);
		Assert.assertEquals("3", uloziste.get("opakovani." + MATICE.toLowerCase(java.util.Locale.ROOT)));
		Assert.assertEquals("šablona se neukládá", 3, NastaveniChallenge.nacti(uloziste).seznam.najdi(MATICE).opakovani());
		s.nastavNasobnostSablony(MATICE, 1);
		NastaveniChallenge.uloz(s, false, uloziste);
		Assert.assertFalse(uloziste.m.keySet().toString(), uloziste.m.keySet().stream().anyMatch(k -> k.startsWith("opakovani.")));
		Assert.assertEquals(1, NastaveniChallenge.nacti(uloziste).seznam.najdi(MATICE).opakovani());
	}

	@Test
	public void neplatnaUlozenaHodnotaDaJednickuACizieKlicezustanou() {
		final String klic = "opakovani." + MATICE.toLowerCase(java.util.Locale.ROOT);
		for (final String h : new String[] { "x", "0", "1", "10", "-2", "", "2,5", "99999999999" }) {
			uloziste.put(klic, h);
			Assert.assertEquals("hodnota „" + h + "“", 1, NastaveniChallenge.nacti(uloziste).seznam.najdi(MATICE).opakovani());
		}
		uloziste.put(klic, " 5 ");
		Assert.assertEquals("okolní mezery se tolerují", 5, NastaveniChallenge.nacti(uloziste).seznam.najdi(MATICE).opakovani());
		uloziste.put("opakovani.neexistuje", "3");
		uloziste.put("jineNastaveni", "zůstane");
		NastaveniChallenge.uloz(NastaveniChallenge.nacti(uloziste).seznam, false, uloziste);
		Assert.assertEquals("zůstane", uloziste.get("jineNastaveni"));
		Assert.assertEquals("5", uloziste.get(klic));
		Assert.assertEquals("klíč šablony, kterou tato verze nezná, zůstane", "3", uloziste.get("opakovani.neexistuje"));
	}

	/** Starší verze čte jen definice.* a format; klíč opakovani.* nezná a jejich zápis ho nesmaže ani nezmění definice. */
	@Test
	public void definiceVlastniSNasobnostiSeUkladaDoDefiniceASablonaSeTimNeovlivni() {
		final ChallengeSeznam s = new ChallengeSeznam();
		s.nastavNasobnostSablony(MATICE, 2);
		s.ulozVlastni(Sablony.maticeDT().sNazvem("Moje D/T").sOpakovanim(5));
		NastaveniChallenge.uloz(s, false, uloziste);
		Assert.assertTrue(uloziste.m.keySet().toString(), uloziste.m.entrySet().stream().anyMatch(e -> e.getKey().endsWith(".opakovani") && "5".equals(e.getValue())));
		final ChallengeSeznam zpet = NastaveniChallenge.nacti(uloziste).seznam;
		Assert.assertEquals(2, zpet.najdi(MATICE).opakovani());
		Assert.assertEquals(5, zpet.najdi("Moje D/T").opakovani());
	}

	@Test
	public void zmenaNasobnostiNemazeVysledekAStaryVysledekSeZahodi() throws Exception {
		final Challenge puvodni = model.ulozVlastni(Sablony.maticeDT().sNazvem("Moje D/T"));
		model.aktivuj(puvodni);
		pockejNaEdt();
		final VysledekPolicek stary = new VysledekPolicek(81, Arrays.asList("D3/T4"), Arrays.asList(new KandidatPolicka("GC1", 50, 14, "D3/T4", "", null)), 50, 1, new int[81]);
		model.nastavVysledekPolicek(puvodni, stary);
		pockejNaEdt();
		model.setVybranePolicko(puvodni, "D3/T4");
		pockejNaEdt();
		udalosti.clear();
		final Challenge nova = model.nastavOpakovani(puvodni, 3);
		pockejNaEdt();
		Assert.assertEquals(3, nova.opakovani());
		Assert.assertEquals(3, model.aktivni().get(0).opakovani());
		Assert.assertSame("výsledek se nemaže", stary, model.vysledekPolicek(nova));
		Assert.assertEquals("výběr políčka zůstává", "D3/T4", model.vybranePolicko(nova));
		Assert.assertEquals(Arrays.asList(ChallengeZmenenyEvent.Druh.DEFINICE), druhy());
		final VysledekPolicek opozdeny = new VysledekPolicek(81, Arrays.asList("D1/T1"), new ArrayList<KandidatPolicka>(), 50, 1, new int[81]);
		model.nastavVysledekPolicek(puvodni, opozdeny);
		pockejNaEdt();
		Assert.assertSame("výsledek spočítaný pro jinou násobnost se zahodí", stary, model.vysledekPolicek(nova));
		final VysledekPolicek aktualni = new VysledekPolicek(81, Arrays.asList("D3/T4"), Arrays.asList(new KandidatPolicka("GC1", 50, 14, "D3/T4", "", null)), 50, 3, new int[81]);
		model.nastavVysledekPolicek(nova, aktualni);
		pockejNaEdt();
		Assert.assertSame(aktualni, model.vysledekPolicek(nova));
	}

	@Test
	public void nasobnostSablonyPlatiRovnoUAktivniAUkladaSe() throws Exception {
		final Challenge sablona = model.vsechny().stream().filter(c -> c.nazev().equals(MATICE)).findFirst().get();
		model.aktivuj(sablona);
		pockejNaEdt();
		final Challenge nova = model.nastavOpakovani(sablona, 2);
		pockejNaEdt();
		Assert.assertEquals(2, nova.opakovani());
		Assert.assertTrue(model.jeSablona(nova));
		Assert.assertEquals(2, model.aktivni().get(0).opakovani());
		Assert.assertEquals("2", uloziste.get("opakovani." + MATICE.toLowerCase(java.util.Locale.ROOT)));
		udalosti.clear();
		Assert.assertEquals(nova, model.nastavOpakovani(nova, 2));
		Assert.assertTrue("stejná hodnota nic neohlásí", udalosti.isEmpty());
	}

	/** Klíč známé šablony se po návratu na jedničku smaže, klíč neznámé šablony (třeba z novější verze) ne. */
	@Test
	public void ulozeniNesmazeNasobnostNeznameSablony() {
		uloziste.put("opakovani.budouci sablona", "4");
		final ChallengeSeznam s = new ChallengeSeznam();
		s.nastavNasobnostSablony(MATICE, 3);
		NastaveniChallenge.uloz(s, false, uloziste);
		s.nastavNasobnostSablony(MATICE, 1);
		NastaveniChallenge.uloz(s, false, uloziste);
		Assert.assertEquals("4", uloziste.get("opakovani.budouci sablona"));
		Assert.assertNull("známá šablona s jedničkou klíč nemá", uloziste.get("opakovani." + MATICE.toLowerCase(java.util.Locale.ROOT)));
	}

	@Test
	public void smazanouVlastniChallengeNasobnostNevrati() throws Exception {
		final Challenge vlastni = model.ulozVlastni(Sablony.maticeDT().sNazvem("Moje D/T"));
		model.smazVlastni(vlastni);
		try {
			model.nastavOpakovani(vlastni, 2);
			Assert.fail("smazaná challenge se nevrací");
		} catch (final ChallengeChyba e) {
			// očekáváno
		}
		Assert.assertTrue(model.vsechny().stream().noneMatch(c -> c.nazev().equals("Moje D/T")));
	}

	@Test
	public void nasobnostJenUMaticeDT() {
		try {
			model.nastavOpakovani(Sablony.od360Domova(), 2);
			Assert.fail("360° nemá násobnost");
		} catch (final ChallengeChyba e) {
			// očekáváno
		}
	}

	private List<ChallengeZmenenyEvent.Druh> druhy() {
		final List<ChallengeZmenenyEvent.Druh> v = new ArrayList<>();
		for (final ChallengeZmenenyEvent e : udalosti) {
			v.add(e.druh());
		}
		return v;
	}
}
