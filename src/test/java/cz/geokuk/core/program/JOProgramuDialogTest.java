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

/** Dialog O programu: texty a odkazy nejsou prázdné, odkazy jsou platné webové adresy a zastaralé texty tam nejsou. */
public class JOProgramuDialogTest {

	private static void texty(final Component c, final List<String> vysledek) {
		if (c instanceof JLabel) {
			vysledek.add(((JLabel) c).getText());
		} else if (c instanceof AbstractButton) {
			vysledek.add(((AbstractButton) c).getText());
		}
		if (c instanceof Container) {
			for (final Component k : ((Container) c).getComponents()) {
				texty(k, vysledek);
			}
		}
	}

	@Test
	public void textyAOdkazyNejsouPrazdne() throws MalformedURLException {
		for (final String s : new String[] { JOProgramuDialog.PODTITUL, JOProgramuDialog.AUTORSTVI_PUVODNI, JOProgramuDialog.AUTORSTVI_UPRAVENA, JOProgramuDialog.LICENCE_VETA,
				JOProgramuDialog.PODEKOVANI_VETA, JOProgramuDialog.TEXT_GITHUB, JOProgramuDialog.TEXT_WIKI, JOProgramuDialog.TEXT_PUVODNI_PROJEKT, JOProgramuDialog.TEXT_PROFIL_AUTORA,
				JOProgramuDialog.TEXT_README }) {
			Assert.assertFalse(s.trim().isEmpty());
		}
		for (final String url : new String[] { FConst.WEB_PAGE_URL, FConst.WEB_PAGE_WIKI, JOProgramuDialog.URL_PUVODNI_PROJEKT, JOProgramuDialog.URL_PROFIL_AUTORA,
				JOProgramuDialog.URL_README }) {
			final URL u = new URL(url);
			Assert.assertTrue(url, "http".equals(u.getProtocol()) || "https".equals(u.getProtocol()));
			Assert.assertFalse(url, u.getHost().isEmpty());
		}
	}

	@Test
	public void dialogSeVytvoriAObsahujeNoveTexty() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final List<String> texty = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> {
			final JOProgramuDialog d = new JOProgramuDialog();
			try {
				texty(d.getContentPane(), texty);
			} finally {
				d.dispose();
			}
		});
		Assert.assertTrue(texty.contains(JOProgramuDialog.AUTORSTVI_PUVODNI));
		Assert.assertTrue(texty.contains(JOProgramuDialog.AUTORSTVI_UPRAVENA));
		Assert.assertTrue(texty.contains(JOProgramuDialog.TEXT_PROFIL_AUTORA));
		for (final String t : texty) {
			Assert.assertFalse(t, t.contains("GC.CZ") || t.contains("(c) 2009"));
		}
	}
}
