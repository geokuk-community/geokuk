package cz.geokuk.plugins.kesoid.mvc;

import java.util.EnumSet;

import javax.swing.SwingUtilities;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.importek.TypZdroje;

/** Tabulky popupů bloku Zdroje se skládají až při prvním zobrazení a dostanou stav, který blok mezitím dostal. */
public class JPrepinaceZdrojuLazyTest {

	private final StavyZdrojuProTesty data = StavyZdrojuProTesty.vzorek();

	@Test
	public void tabulkySeNeskladajiPriStartuAniPriZmenachStavu() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JPrepinaceZdroju blok = new JPrepinaceZdroju();
			blok.setOvladani(new StavyZdrojuProTesty.Zaznam());
			blok.setPovoleneTypy(EnumSet.of(TypZdroje.GPX));
			blok.setCasDat("2026-10-25 10:00");
			blok.obnov(data.snimek());
			Assert.assertFalse(blok.jsouTabulkyVytvoreny());
		});
	}

	@Test
	public void souhrnDostaneStavBloku() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JPrepinaceZdroju blok = new JPrepinaceZdroju();
			final StavyZdrojuProTesty.Zaznam ovladani = new StavyZdrojuProTesty.Zaznam();
			blok.setOvladani(ovladani);
			blok.setPovoleneTypy(EnumSet.of(TypZdroje.GPX));
			blok.setCasDat("2026-10-25 10:00");
			blok.obnov(data.snimek());
			blok.getUplna();
			Assert.assertTrue(blok.jsouTabulkyVytvoreny());
			Assert.assertTrue("tabulka zná povolené typy", blok.getUplna().getRadky().stream().allMatch(r -> r.typ == TypZdroje.GPX));
			Assert.assertFalse(blok.getUplna().getRadky().isEmpty());
			Assert.assertTrue(blok.getDetail(TypZdroje.GPX).getRadky().stream().allMatch(r -> r.typ == TypZdroje.GPX));
			Assert.assertFalse(blok.getDetail(TypZdroje.GPX).getRadky().isEmpty());
		});
	}

	@Test
	public void najetiMysiNaPopisekSloziSouhrn() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JPrepinaceZdroju blok = new JPrepinaceZdroju();
			for (final java.awt.event.MouseListener l : blok.getPopisek().getMouseListeners()) {
				l.mouseEntered(new java.awt.event.MouseEvent(blok.getPopisek(), java.awt.event.MouseEvent.MOUSE_ENTERED, 0, 0, 1, 1, 0, false));
			}
			Assert.assertTrue(blok.jsouTabulkyVytvoreny());
			blok.removeNotify();
		});
	}
}
