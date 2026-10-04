package cz.geokuk.util.lang;

import java.awt.Component;
import java.awt.Container;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.plaf.basic.BasicHTML;

import org.junit.Assert;
import org.junit.Test;

/** Text z GPX, který začíná {@code <html>}, se v popisku Swingu zobrazí jako text, ne jako HTML. */
public class FStringTextTest {

	@Test
	public void htmlZDatSeUkazeJakoText() {
		final String zGpx = "<html><img src=http://sledovac/x.png>";
		final String text = FString.text(zGpx);
		Assert.assertFalse(text, text.contains("<img"));
		Assert.assertEquals("<html>&lt;html&gt;&lt;img src=http://sledovac/x.png&gt;", text);
	}

	@Test
	public void velikostPismenNehraje() {
		final String text = FString.text("<HTML><img src=x>");
		Assert.assertFalse(text, text.contains("<img"));
	}

	@Test
	public void obycejnyTextBezeZmeny() {
		Assert.assertEquals("Keš <b> u lesa", FString.text("Keš <b> u lesa"));
		Assert.assertEquals(" <html>", FString.text(" <html>"));
		Assert.assertFalse(BasicHTML.isHTMLString(FString.text(" <html>")));
		Assert.assertNull(FString.text(null));
	}

	@Test
	public void radkyZustanou() {
		Assert.assertEquals("<html>&lt;html&gt;a<br>b", FString.text("<html>a\nb"));
	}

	/** Hint s {@code <html>} až na dalším řádku: JOptionPane dělí zprávu po řádcích, každý řádek je vlastní popisek. */
	@Test
	public void htmlNaDalsimRadku() {
		for (final String zGpx : new String[] { "Pod kamenem\n<html><img src=http://sledovac/x.png>", "Pod kamenem\r\n<html><img src=x>", "a\r<html><img src=x>" }) {
			final String text = FString.text(zGpx);
			Assert.assertFalse(text, text.contains("<img"));
			Assert.assertFalse(text, text.contains("\n") || text.contains("\r"));
			Assert.assertEquals(0, obrazkyVPopiscich(new JOptionPane(text)));
		}
		Assert.assertEquals("<html>Pod kamenem<br>&lt;html&gt;x", FString.text("Pod kamenem\n<html>x"));
		Assert.assertEquals("a\nb <html>", FString.text("a\nb <html>"));
	}

	/** Bez opravy JOptionPane vykreslí druhý řádek jako HTML. */
	@Test
	public void joptionPaneVykresliHtmlRadek() {
		Assert.assertEquals(1, obrazkyVPopiscich(new JOptionPane("Pod kamenem\n<html><img src=x>")));
	}

	/** Popisky vykreslené jako HTML se značkou obrázku, tedy s požadavkem na cizí server. */
	private static int obrazkyVPopiscich(final Container c) {
		int pocet = 0;
		for (final Component k : c.getComponents()) {
			if (k instanceof JLabel && ((JLabel) k).getClientProperty(BasicHTML.propertyKey) != null && ((JLabel) k).getText().contains("<img")) {
				pocet++;
			}
			if (k instanceof Container) {
				pocet += obrazkyVPopiscich((Container) k);
			}
		}
		return pocet;
	}
}
