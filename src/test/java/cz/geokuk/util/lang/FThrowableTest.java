package cz.geokuk.util.lang;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.Assert;
import org.junit.Test;

public class FThrowableTest {

	@Test
	public void vyjimkaBezZasobnikuSeVypise() {
		final Throwable bezZasobniku = new RuntimeException("bez zásobníku") {
			private static final long serialVersionUID = 1L;

			@Override
			public StackTraceElement[] getStackTrace() {
				return null;
			}
		};
		final StringWriter sw = new StringWriter();
		FThrowable.printStackTrace(bezZasobniku, new PrintWriter(sw), "");
		Assert.assertTrue(sw.toString(), sw.toString().contains("bez zásobníku"));
	}
}
