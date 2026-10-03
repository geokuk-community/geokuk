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
}
