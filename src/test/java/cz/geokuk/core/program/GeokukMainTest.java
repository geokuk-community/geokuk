package cz.geokuk.core.program;

import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

public class GeokukMainTest {

	@Test
	public void zpravyZJavaUtilLoggingJsouVLogu() {
		final ch.qos.logback.classic.Logger cil = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("test.jul");
		final ListAppender<ILoggingEvent> zachycene = new ListAppender<>();
		zachycene.start();
		cil.addAppender(zachycene);
		try {
			GeokukMain.presmerujJulDoSlf4j();
			Logger.getLogger("test.jul").warning("zpráva z JUL");
			Assert.assertEquals(1, zachycene.list.size());
			Assert.assertEquals("zpráva z JUL", zachycene.list.get(0).getFormattedMessage());
		} finally {
			cil.detachAppender(zachycene);
		}
	}

	@Test
	public void druhaInstancePoznaZeUzBezi() throws Exception {
		final java.io.File slozka = java.nio.file.Files.createTempDirectory("geokuk-zamek").toFile();
		final java.io.File soubor = new java.io.File(slozka, cz.geokuk.start.Start.ZAMEK);
		final java.nio.channels.FileLock prvni = cz.geokuk.start.Start.zamkni(soubor);
		try {
			Assert.assertNotNull(prvni);
			Assert.assertTrue(GeokukMain.uzBezi(null, soubor));
			Assert.assertFalse(GeokukMain.uzBezi(prvni, soubor));
		} finally {
			prvni.channel().close();
			soubor.delete();
			slozka.delete();
		}
	}

	@Test
	public void nezapisovatelnaSlozkaNebraniSpusteni() throws Exception {
		final java.io.File soubor = java.io.File.createTempFile("geokuk-data", ".soubor");
		try {
			Assert.assertFalse(GeokukMain.uzBezi(null, new java.io.File(soubor, cz.geokuk.start.Start.ZAMEK)));
		} finally {
			soubor.delete();
		}
	}
}
