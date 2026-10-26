package cz.geokuk.core.program;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/** Dialog O programu: původní texty beze změny, texty a odkazy nejsou prázdné a každé tlačítko míří na svou adresu. */
public class JOProgramuDialogTest {

	private static void komponenty(final Component c, final List<Component> vysledek) {
		vysledek.add(c);
		if (c instanceof Container) {
			for (final Component k : ((Container) c).getComponents()) {
				komponenty(k, vysledek);
			}
		}
	}

	@Test
	public void textyAOdkazyNejsouPrazdne() throws MalformedURLException {
		for (final String s : new String[] { JOProgramuDialog.NADPIS_PUVODNI, JOProgramuDialog.NADPIS_UPRAVENA, JOProgramuDialog.AUTORSTVI_PUVODNI, JOProgramuDialog.PROFIL_PUVODNI,
				JOProgramuDialog.LICENCE_PUVODNI_1, JOProgramuDialog.LICENCE_PUVODNI_2, JOProgramuDialog.PODTITUL, JOProgramuDialog.AUTORSTVI_UPRAVENA, JOProgramuDialog.LICENCE_VETA,
				JOProgramuDialog.PODEKOVANI_VETA }) {
			Assert.assertFalse(s.trim().isEmpty());
		}
		Assert.assertFalse(JOProgramuDialog.ODKAZY.length == 0);
		for (final String[] odkaz : JOProgramuDialog.ODKAZY) {
			Assert.assertFalse(odkaz[0].trim().isEmpty());
			final URL u = new URL(odkaz[1]);
			Assert.assertTrue(odkaz[1], "http".equals(u.getProtocol()) || "https".equals(u.getProtocol()));
			Assert.assertFalse(odkaz[1], u.getHost().isEmpty());
		}
	}

	@Test
	public void puvodniTextyJsouBezZmeny() {
		Assert.assertEquals("(c) 2009 Martin Veverka", JOProgramuDialog.AUTORSTVI_PUVODNI);
		Assert.assertEquals("Profil na GC.COM a GC.CZ: \"rodinka veverek\"", JOProgramuDialog.PROFIL_PUVODNI);
		Assert.assertEquals("GeoKuk je svobodný software", JOProgramuDialog.LICENCE_PUVODNI_1);
		Assert.assertEquals("pod licencí GNU GPL v3.", JOProgramuDialog.LICENCE_PUVODNI_2);
		Assert.assertEquals("http://www.geocaching.com/profile/?guid=22cad0c7-59a3-417c-99d1-7c9079e9ae27", JOProgramuDialog.adresa(JOProgramuDialog.TEXT_PROFIL_AUTORA));
		Assert.assertEquals("https://github.com/marvertin/geokuk", JOProgramuDialog.adresa(JOProgramuDialog.TEXT_PUVODNI_PROJEKT));
	}

	@Test
	public void dialogMaDveSekceAKazdeTlacitkoMiriNaSvouAdresu() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final List<String> texty = new ArrayList<>();
		final List<String[]> tlacitka = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> {
			final JOProgramuDialog d = new JOProgramuDialog();
			try {
				final List<Component> vse = new ArrayList<>();
				komponenty(d.getContentPane(), vse);
				for (final Component c : vse) {
					if (c instanceof JLabel) {
						texty.add(((JLabel) c).getText());
					}
					if (c instanceof AbstractButton) {
						final Object url = ((AbstractButton) c).getClientProperty(JOProgramuDialog.KLIC_URL);
						if (url != null) {
							tlacitka.add(new String[] { c.getAccessibleContext().getAccessibleName(), (String) url });
						}
					}
				}
			} finally {
				d.dispose();
			}
		});
		Assert.assertTrue(texty.contains(JOProgramuDialog.AUTORSTVI_PUVODNI));
		Assert.assertTrue(texty.contains(JOProgramuDialog.PROFIL_PUVODNI));
		Assert.assertTrue(texty.contains(JOProgramuDialog.AUTORSTVI_UPRAVENA));
		Assert.assertEquals("každé tlačítko-odkaz je v seznamu právě jednou", JOProgramuDialog.ODKAZY.length, tlacitka.size());
		for (final String[] odkaz : JOProgramuDialog.ODKAZY) {
			Assert.assertTrue(odkaz[0], tlacitka.stream().anyMatch(t -> odkaz[0].equals(t[0]) && odkaz[1].equals(t[1])));
		}
	}
}
