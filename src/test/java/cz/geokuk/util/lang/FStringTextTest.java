package cz.geokuk.util.lang;

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
}
