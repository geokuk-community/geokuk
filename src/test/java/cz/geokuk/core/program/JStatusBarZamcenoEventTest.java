package cz.geokuk.core.program;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.JLabel;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.mvc.ZamceneDatabazeEvent;

/** Stavový řádek ukáže zamčené databáze a po uvolnění údaj skryje. */
public class JStatusBarZamcenoEventTest {

	@Test
	public void ukazeAPotomSkryje() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JStatusBar radek = new JStatusBar();
		final Field f = JStatusBar.class.getDeclaredField("jZamceno");
		f.setAccessible(true);
		final JLabel stitek = (JLabel) f.get(radek);
		Assert.assertFalse("na začátku skrytý", stitek.isVisible());

		radek.onEvent(udalost(Arrays.asList("a.db3", "b.db3")));
		Assert.assertTrue(stitek.isVisible());
		Assert.assertEquals("Zamčeno: 2", stitek.getText());
		Assert.assertTrue(stitek.getToolTipText(), stitek.getToolTipText().contains("a.db3"));

		radek.onEvent(udalost(Collections.emptyList()));
		Assert.assertFalse(stitek.isVisible());
	}

	private static ZamceneDatabazeEvent udalost(final List<String> jmena) throws Exception {
		final Constructor<ZamceneDatabazeEvent> k = ZamceneDatabazeEvent.class.getDeclaredConstructor(List.class);
		k.setAccessible(true);
		return k.newInstance(jmena);
	}
}
