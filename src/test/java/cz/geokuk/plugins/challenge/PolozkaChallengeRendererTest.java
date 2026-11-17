package cz.geokuk.plugins.challenge;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.MetalTheme;
import javax.swing.plaf.metal.OceanTheme;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.framework.Event0;
import cz.geokuk.framework.EventFirer;
import cz.geokuk.plugins.challenge.NastaveniChallengeTest.MapoveUloziste;

/** Dlouhé názvy šablon (GC kód a anglický název, ~58 znaků) v seznamu dialogu: zkrácené „…“ podle šířky seznamu, přípona zůstane, plný název je v tooltipu. */
public class PolozkaChallengeRendererTest {

	private static ChallengeModel model() {
		final ChallengeModel m = new ChallengeModel();
		m.inject(new EventFirer() {
			@Override
			public void fire(final Event0<?> event) {}
		});
		final MapoveUloziste u = new MapoveUloziste();
		m.setUloziste(() -> u);
		m.initAfterInject();
		return m;
	}

	private static JLabel polozka(final ChallengeModel m, final Challenge c, final int sirkaSeznamu) {
		return polozka(m, c, sirkaSeznamu, false);
	}

	private static JLabel polozka(final ChallengeModel m, final Challenge c, final int sirkaSeznamu, final boolean vybrana) {
		final DefaultListModel<Challenge> lm = new DefaultListModel<>();
		lm.addElement(c);
		final JList<Challenge> l = new JList<>(lm);
		l.setSize(sirkaSeznamu, 300);
		return (JLabel) new PolozkaChallengeRenderer(() -> m).getListCellRendererComponent(l, c, 0, vybrana, false);
	}

	@Test
	public void dlouhyNazevSablonySeVSirceSeznamuZkratiPriponaZustane() {
		final LookAndFeel puvodni = UIManager.getLookAndFeel();
		final MetalTheme puvodniTema = MetalLookAndFeel.getCurrentTheme();
		try {
			for (final MetalTheme tema : new MetalTheme[] { new DefaultMetalTheme(), new OceanTheme(), new cz.geokuk.core.lookandfeel.CharcoalTheme() }) {
				MetalLookAndFeel.setCurrentTheme(tema);
				UIManager.setLookAndFeel(new MetalLookAndFeel());
				final ChallengeModel m = model();
				for (final Challenge c : Sablony.vse()) {
					for (final int sirka : new int[] { 200, 290, 400 }) {
						final JLabel s = polozka(m, c, sirka);
						final BufferedImage obraz = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
						final Graphics2D g = obraz.createGraphics();
						final FontMetrics fm = g.getFontMetrics(s.getFont());
						g.dispose();
						Assert.assertTrue(tema.getName() + " " + sirka + ": " + s.getText(), fm.stringWidth(PolozkaChallengeRenderer.plnyText(s)) <= sirka - s.getInsets().left - s.getInsets().right || s.getText().length() < 8);
						Assert.assertTrue(PolozkaChallengeRenderer.plnyText(s), PolozkaChallengeRenderer.plnyText(s).endsWith("(šablona)"));
						Assert.assertEquals("šířku seznamu určuje okno, ne název", 1, s.getPreferredSize().width);
						Assert.assertTrue("tooltip má plný název: " + s.getToolTipText(), s.getToolTipText().contains(c.nazev().replace("&", "&amp;")));
						if (fm.stringWidth(PolozkaChallengeRenderer.ZAPNUTA + "  " + c.nazev() + "  (šablona)") > sirka - s.getInsets().left - s.getInsets().right) {
							Assert.assertTrue(s.getText(), s.getText().contains("…"));
						} else {
							Assert.assertTrue("vejde se: " + s.getText(), s.getText().contains(c.nazev()));
						}
					}
				}
			}
		} catch (final Exception e) {
			throw new AssertionError(e);
		} finally {
			MetalLookAndFeel.setCurrentTheme(puvodniTema);
			try {
				UIManager.setLookAndFeel(puvodni);
			} catch (final Exception e) {
				// beze změny
			}
		}
	}

	@Test
	public void nazvySablonJsouDlouheAleVRozumnemRozsahADelsiNezSeznam() {
		int nejdelsi = 0;
		for (final Challenge c : Sablony.vse()) {
			nejdelsi = Math.max(nejdelsi, c.nazev().length());
		}
		Assert.assertTrue("nejdelší název šablony: " + nejdelsi, nejdelsi >= 40 && nejdelsi <= 80);
	}

	@Test
	public void aktivniAVlastniPolozkaMaPopisekAKratkyNazevBezZkraceni() {
		final ChallengeModel m = model();
		final Challenge c = m.ulozVlastni(new Challenge("Moje", Challenge.Typ.SEKTORY_360, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC));
		m.aktivuj(c);
		final JLabel s = polozka(m, c, 290);
		Assert.assertEquals("●  Moje", PolozkaChallengeRenderer.plnyText(s));
		Assert.assertFalse(s.getText().contains("…"));
		final Challenge jina = m.ulozVlastni(new Challenge("Jiná", Challenge.Typ.SEKTORY_360, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC));
		Assert.assertEquals("neaktivní má prázdné kolečko", "○  Jiná", PolozkaChallengeRenderer.plnyText(polozka(m, jina, 290)));
	}

	/** Šablona má příponu „(šablona)“ na konci a jen přípona je šedá, značka a název mají barvu písma. */
	@Test
	public void priponaJeSedaZnackaANazevNe() {
		final Object puvodni = UIManager.get("Label.disabledForeground");
		try {
			UIManager.put("Label.disabledForeground", java.awt.Color.RED);
			final ChallengeModel m = model();
			final Challenge sablona = Sablony.cesko360();
			final JLabel s = polozka(m, sablona, 400);
			s.setSize(400, 24);
			final BufferedImage obraz = new BufferedImage(400, 24, BufferedImage.TYPE_INT_ARGB);
			final Graphics2D g = obraz.createGraphics();
			s.paint(g);
			final FontMetrics fm = g.getFontMetrics(s.getFont());
			g.dispose();
			final int hranice = s.getInsets().left + fm.stringWidth(s.getText());
			int cervenaVNazvu = 0;
			int cervenaVPripone = 0;
			for (int y = 0; y < 24; y++) {
				for (int x = 0; x < 400; x++) {
					final int rgb = obraz.getRGB(x, y);
					final boolean cervena = ((rgb >> 16) & 0xff) > 150 && ((rgb >> 8) & 0xff) < 100 && (rgb & 0xff) < 100;
					if (cervena) {
						if (x < hranice - 1) {
							cervenaVNazvu++;
						} else {
							cervenaVPripone++;
						}
					}
				}
			}
			Assert.assertEquals("značka a název nejsou šedé", 0, cervenaVNazvu);
			Assert.assertTrue("přípona je šedá", cervenaVPripone > 10);
			Assert.assertEquals("přípona není v textu popisku", PolozkaChallengeRenderer.plnyText(s), s.getText() + "  (šablona)");
		} finally {
			UIManager.put("Label.disabledForeground", puvodni);
		}
	}

	/** Nedostupná challenge má příponu „(nedostupná)“ jako ostatní challenge ženského rodu. */
	@Test
	public void nedostupnaChallengeMaPriponuVZenskemRode() {
		final ChallengeModel m = model();
		final Challenge kal = m.ulozVlastni(new Challenge("Kal", Challenge.Typ.KALENDAR, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC));
		Assert.assertTrue(PolozkaChallengeRenderer.plnyText(polozka(m, kal, 400)), PolozkaChallengeRenderer.plnyText(polozka(m, kal, 400)).endsWith("(nedostupná)"));
	}

	/** Na vybrané řádce má přípona barvu výběru, ne šedou, která by na pozadí výběru splynula. */
	@Test
	public void priponaVybraneRadkyMaBarvuVyberu() {
		final Object puvodni = UIManager.get("Label.disabledForeground");
		try {
			UIManager.put("Label.disabledForeground", java.awt.Color.RED);
			final ChallengeModel m = model();
			final JLabel s = polozka(m, Sablony.cesko360(), 400, true);
			s.setSize(400, 24);
			final BufferedImage obraz = new BufferedImage(400, 24, BufferedImage.TYPE_INT_ARGB);
			final Graphics2D g = obraz.createGraphics();
			s.paint(g);
			final FontMetrics fm = g.getFontMetrics(s.getFont());
			g.dispose();
			final int hranice = s.getInsets().left + fm.stringWidth(s.getText());
			final int pozadi = s.getBackground().getRGB();
			int cervena = 0;
			int jina = 0;
			for (int y = 0; y < 24; y++) {
				for (int x = hranice; x < 400; x++) {
					final int rgb = obraz.getRGB(x, y);
					if (((rgb >> 16) & 0xff) > 150 && ((rgb >> 8) & 0xff) < 100 && (rgb & 0xff) < 100) {
						cervena++;
					} else if (rgb != pozadi) {
						jina++;
					}
				}
			}
			Assert.assertEquals("přípona vybrané řádky není šedá", 0, cervena);
			Assert.assertTrue("přípona je vykreslená barvou výběru", jina > 10);
		} finally {
			UIManager.put("Label.disabledForeground", puvodni);
		}
	}

	/** Když je šablona zároveň nedostupná, je „(šablona)“ před „(nedostupná)“. */
	@Test
	public void priponySablonaPredNedostupna() {
		final ChallengeModel m = model();
		final Challenge nedostupnaSablona = new Challenge(Sablony.maticeDT().nazev(), Challenge.Typ.KALENDAR, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC);
		Assert.assertTrue(m.jeSablona(nedostupnaSablona));
		Assert.assertFalse(m.jeVUi(nedostupnaSablona));
		final String text = PolozkaChallengeRenderer.plnyText(polozka(m, nedostupnaSablona, 600));
		Assert.assertTrue(text, text.endsWith("  (šablona)  (nedostupná)"));
	}
}
