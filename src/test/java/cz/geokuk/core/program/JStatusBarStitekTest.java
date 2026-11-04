package cz.geokuk.core.program;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;

import javax.swing.JLabel;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.DuvodNekresleni;
import cz.geokuk.plugins.kesoid.StavVrstev;
import cz.geokuk.plugins.kesoid.StavVrstevEvent;
import cz.geokuk.plugins.kesoid.VrstvaNadMapou;

/** Štítek „Kruhy skryté“ ve stavovém řádku se ukazuje jen při skrytých zapnutých vrstvách a v bublině říká proč. */
public class JStatusBarStitekTest {

	private static JLabel stitek(final JStatusBar radek) throws Exception {
		final Field f = JStatusBar.class.getDeclaredField("stitekSkrytychKruhu");
		f.setAccessible(true);
		return (JLabel) f.get(radek);
	}

	@Test
	public void stitekJeVidetJenKdyzNekterouVrstvuSkryva() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JStatusBar radek = new JStatusBar();
		final JLabel s = stitek(radek);
		Assert.assertFalse(s.isVisible());
		Assert.assertEquals("Kruhy skryté", s.getText());

		final StavVrstev skryto = StavVrstev.prazdny().s(VrstvaNadMapou.ZVYRAZNOVACI_KRUHY, DuvodNekresleni.PRILIS_HUSTE, 0).s(VrstvaNadMapou.OBSAZENOST,
				DuvodNekresleni.POLOMER_PRILIS_MALY, 0);
		radek.onEvent(new StavVrstevEvent(skryto));
		Assert.assertTrue(s.isVisible());
		Assert.assertEquals(skryto.tooltip(), s.getToolTipText());

		radek.onEvent(new StavVrstevEvent(StavVrstev.prazdny()));
		Assert.assertFalse(s.isVisible());
		Assert.assertNull("bublina zmizí se štítkem", s.getToolTipText());
	}

	@Test
	public void plovouciStitekNemeniSirkuRadku() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JStatusBar radek = new JStatusBar();
		final int bez = radek.getPreferredSize().width;
		radek.onEvent(new StavVrstevEvent(StavVrstev.prazdny().s(VrstvaNadMapou.OBSAZENOST, DuvodNekresleni.POLOMER_PRILIS_MALY, 0)));
		Assert.assertEquals("plovoucí štítek nemění šířku řádku", bez, radek.getPreferredSize().width);
	}
}
