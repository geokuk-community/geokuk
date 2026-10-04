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
		new ExceptionDumper().dump(new IllegalStateException("soubor <img src=x onerror=alert(1)>.gpx", new IOException("<b>vnořená</b>")), EExceptionSeverity.DISPLAY,
				"Načítání <script>x</script>", new ExceptionDumperRepositorySpi() {
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
		Assert.assertFalse(vypis, vypis.contains("<img"));
		Assert.assertFalse(vypis, vypis.contains("<script"));
		Assert.assertFalse(vypis, vypis.contains("<b>vnořená"));
		Assert.assertTrue(vypis, vypis.contains("&lt;img"));
	}
}
