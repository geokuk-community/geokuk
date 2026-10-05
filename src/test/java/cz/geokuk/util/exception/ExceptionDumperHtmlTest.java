package cz.geokuk.util.exception;

import java.io.IOException;
import java.net.URL;

import org.junit.Assert;
import org.junit.Test;

/** Text z výjimky ani okolnost se ve výpisu chyby nestanou HTML značkami. */
public class ExceptionDumperHtmlTest {

	private String vypis;

	@Test
	public void zpravaIOkolnostJsouEscapovane() {
		vypis(new IllegalStateException("soubor <img src=x onerror=alert(1)>.gpx", new IOException("<b>vnořená</b>")), "Načítání <script>x</script>");
		Assert.assertFalse(vypis, vypis.contains("<img"));
		Assert.assertFalse(vypis, vypis.contains("<script"));
		Assert.assertFalse(vypis, vypis.contains("<b>vnořená"));
		Assert.assertTrue(vypis, vypis.contains("&lt;img"));
	}

	@Test
	public void systemoveVlastnostiJsouEscapovane() {
		System.setProperty("geokuk.test.html", "C:\\Data <i>&</i>");
		try {
			vypis(new IllegalStateException("x"), "y");
		} finally {
			System.clearProperty("geokuk.test.html");
		}
		Assert.assertFalse(vypis, vypis.contains("<i>&</i>"));
		Assert.assertTrue(vypis, vypis.contains("&lt;i&gt;&amp;&lt;/i&gt;"));
	}

	@Test
	public void sqlStavBezZnacek() {
		vypis(new java.sql.SQLException("zamčeno", "HY000", 5), "y");
		Assert.assertTrue(vypis, vypis.contains("SQLSTATE=HY000 ERRORCODE=5"));
		Assert.assertFalse(vypis, vypis.contains("&lt;b&gt; SQLSTATE"));
	}

	private void vypis(final Throwable t, final String okolnost) {
		new ExceptionDumper().dump(t, EExceptionSeverity.DISPLAY, okolnost, new ExceptionDumperRepositorySpi() {
					@Override
					public int getRunNumber() {
						return 1;
					}

					@Override
					public URL getUrl(final AExcId aCode) {
						return null;
					}

					@Override
					public boolean isReadable() {
						return true;
					}

					@Override
					public void write(final AExcId aCode, final String aExceptionData) {
						vypis = aExceptionData;
					}
				});
		Assert.assertNotNull(vypis);
	}
}
