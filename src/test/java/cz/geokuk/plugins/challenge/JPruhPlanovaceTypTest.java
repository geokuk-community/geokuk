package cz.geokuk.plugins.challenge;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;

import javax.swing.JLabel;

import org.junit.Assert;
import org.junit.Test;

/** Popisek typu v pruhu se vejde do svého místa celý (Kalendář menším písmem), kratší typy mají výchozí písmo. */
public class JPruhPlanovaceTypTest {

	private static FontMetrics metriky(final Font f) {
		return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).getGraphics().getFontMetrics(f);
	}

	@Test
	public void kazdyTypSeVejdeCely() {
		final Font zaklad = new JLabel().getFont().deriveFont(Font.BOLD, 12f);
		final int sirka = JPruhPlanovace.rozloz(cz.geokuk.framework.RozmisteniPrekryvu.PRUH_PLNY)[JPruhPlanovace.VYBER].width - 8;
		for (final Challenge.Typ t : Challenge.Typ.values()) {
			final String text = StitkyPruhu.typ(t) + " ▾";
			final Font f = JPruhPlanovace.pismoNaSirku(zaklad, text, sirka, JPruhPlanovaceTypTest::metriky);
			Assert.assertTrue(text + ": " + metriky(f).stringWidth(text) + " > " + sirka, metriky(f).stringWidth(text) <= sirka);
			Assert.assertTrue(text + ": písmo aspoň 9 bodů", f.getSize2D() >= 9f);
		}
	}

	/** Metriky s pevnou šířkou znaku (půl velikosti písma), aby výsledek nezávisel na písmu platformy. */
	private static FontMetrics pevneMetriky(final Font f) {
		return new FontMetrics(f) {
			private static final long serialVersionUID = 1L;

			@Override
			public int stringWidth(final String text) {
				return (int) Math.ceil(text.length() * getFont().getSize2D() * 0.5);
			}
		};
	}

	/** Zmenšování písma podle šířky: výchozí 12 b, když se vejde; jinak menší, nejméně 9 b. Šířky písmen jsou umělé, takže platí na každém systému. */
	@Test
	public void pismoSeZmensujeJenKdyzSeNevejdeAMaNejmeneDevetBodu() {
		final Font zaklad = new Font(Font.DIALOG, Font.BOLD, 12);
		final int sirka = JPruhPlanovace.rozloz(cz.geokuk.framework.RozmisteniPrekryvu.PRUH_PLNY)[JPruhPlanovace.VYBER].width - 8;
		Assert.assertEquals("krátký text výchozím písmem", 12f, JPruhPlanovace.pismoNaSirku(zaklad, "360° ▾", sirka, JPruhPlanovaceTypTest::pevneMetriky).getSize2D(), 0.01);
		final Font mensi = JPruhPlanovace.pismoNaSirku(zaklad, "Kalendář ▾", sirka, JPruhPlanovaceTypTest::pevneMetriky);
		Assert.assertTrue("delší text menším písmem: " + mensi.getSize2D(), mensi.getSize2D() < 12f && mensi.getSize2D() >= 9f);
		Assert.assertTrue("a vejde se", pevneMetriky(mensi).stringWidth("Kalendář ▾") <= sirka);
		Assert.assertEquals("nejmenší 9 bodů", 9f, JPruhPlanovace.pismoNaSirku(zaklad, "Velmi dlouhý typ challenge", sirka, JPruhPlanovaceTypTest::pevneMetriky).getSize2D(), 0.01);
	}

	/** Skutečná lišta s Kalendářem: typ v tlačítku výběru se po rozložení vejde celý, název challenge je jen v tooltipu. */
	@Test
	public void pruhSKalendaremUkazePopisekCely() throws Exception {
		final ChallengeModel model = new ChallengeModel();
		final JPruhPlanovace pruh = new JPruhPlanovace();
		model.inject(new cz.geokuk.framework.EventFirer() {
			@Override
			public void fire(final cz.geokuk.framework.Event0<?> event) {
				pruh.onEvent((ChallengeZmenenyEvent) event);
			}
		});
		final NastaveniChallengeTest.MapoveUloziste u = new NastaveniChallengeTest.MapoveUloziste();
		model.setUloziste(() -> u);
		model.initAfterInject();
		model.setKalendarVUi(true);
		pruh.inject(model);
		pruh.setSize(1920, 1000);
		final Challenge c = model.ulozVlastni(new Challenge("Kalendář test", Challenge.Typ.KALENDAR, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC));
		model.aktivuj(c);
		model.setZobrazena(c);
		model.setZapnuto(true);
		javax.swing.SwingUtilities.invokeAndWait(() -> {});
		pruh.doLayout();
		final javax.swing.AbstractButton vyber = (javax.swing.AbstractButton) pruh.getComponent(JPruhPlanovace.VYBER);
		Assert.assertEquals("Kalendář ▾", vyber.getText());
		final int text = vyber.getFontMetrics(vyber.getFont()).stringWidth(vyber.getText());
		Assert.assertTrue(vyber.getFont() + ": " + text + " > " + vyber.getWidth(), text <= vyber.getWidth() - 8);
		Assert.assertEquals("Challenge: Kalendář test – vybrat jinou", vyber.getToolTipText());
	}
}
