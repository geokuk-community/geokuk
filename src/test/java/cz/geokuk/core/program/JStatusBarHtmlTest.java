package cz.geokuk.core.program;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.reflect.Field;

import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicHTML;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import cz.geokuk.plugins.cesty.CestyChangedEvent;
import cz.geokuk.plugins.cesty.CestyModel;
import cz.geokuk.plugins.cesty.data.Doc;

/** Soubor s cestami, jehož jméno začíná {@code <html>} (na Linuxu a macOS jde), se v bublině stavového řádku ukáže jako text. */
public class JStatusBarHtmlTest {

	@Test
	public void jmenoSouboruVBubline() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final File soubor = new File("<html><img src=http:&#47;&#47;sledovac&#47;x.png>.gpx");
		final Doc doc = new Doc() {
			@Override
			public boolean isEmpty() {
				return false;
			}

			@Override
			public File getFile() {
				return soubor;
			}
		};
		final CestyModel model = new CestyModel() {
			@Override
			public Doc getDoc() {
				return doc;
			}
		};
		final CestyChangedEvent event = new CestyChangedEvent(doc, null);
		event.setModel(model);
		final JStatusBar radek = new JStatusBar();
		radek.onEvent(event);
		final Field f = JStatusBar.class.getDeclaredField("jSouborSVyletem");
		f.setAccessible(true);
		final String bublina = ((JComponent) f.get(radek)).getToolTipText();
		Assert.assertFalse(bublina, BasicHTML.isHTMLString(bublina) && bublina.contains("<img"));
	}
}
