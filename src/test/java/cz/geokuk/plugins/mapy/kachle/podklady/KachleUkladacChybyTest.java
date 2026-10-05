package cz.geokuk.plugins.mapy.kachle.podklady;

import static org.junit.Assert.assertTrue;

import java.awt.Image;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.*;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.data.*;

/** Chyby při ukládání dlaždic na disk se nesmí ztratit. */
public class KachleUkladacChybyTest {

	private static final Ka KACHLE = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 13), EKaType.TURIST_M);

	private final Logger logger = (Logger) LoggerFactory.getLogger(KachleZiskavac.class);
	/** Do seznamu zapisují vlákna ziskávače i z předchozího testu, proto seznam bezpečný pro souběh. */
	private final List<ILoggingEvent> udalosti = new CopyOnWriteArrayList<>();
	private final AppenderBase<ILoggingEvent> appender = new AppenderBase<ILoggingEvent>() {
		@Override
		protected void append(final ILoggingEvent e) {
			udalosti.add(e);
		}
	};

	@Before
	public void setUp() {
		appender.start();
		logger.addAppender(appender);
	}

	@After
	public void tearDown() {
		logger.detachAppender(appender);
	}

	@Test(timeout = 30000)
	public void chybaZapisuSeZaloguje() throws Exception {
		final KachleZiskavac ziskavac = new KachleZiskavac();
		ziskavac.inject(new KachleModel() {
			@Override
			public boolean isUkladatMapyNaDisk() {
				return true;
			}
		});
		ziskavac.setKachleManager(new KachleManager() {
			@Override
			public boolean exists(final Ka ki) {
				return false;
			}

			@Override
			public Image load(final Ka ki) {
				return null;
			}

			@Override
			public boolean save(final Collection<ItemToSave> itemsToSave) {
				throw new IllegalStateException("disk");
			}
		});
		for (int i = 0; i < 300; i++) {
			ziskavac.ukladac.zaplanujUlozeni(new Ukladanec(KACHLE, new byte[0], null));
		}
		pockejNaChybu("Ukládání dlaždic na disk selhalo");
	}

	@Test(timeout = 30000)
	public void chybaNaplanovanehoUkladaniSeZaloguje() throws Exception {
		final KachleZiskavac ziskavac = new KachleZiskavac();
		ziskavac.inject(new KachleModel() {
			@Override
			public boolean isUkladatMapyNaDisk() {
				throw new IllegalStateException("nastavení");
			}
		});
		ziskavac.ukladac.zaplanujUlozeni(new Ukladanec(KACHLE, new byte[0], null));
		pockejNaChybu("Naplánované ukládání dlaždic na disk selhalo");
	}

	private void pockejNaChybu(final String zprava) throws InterruptedException {
		final long konec = System.currentTimeMillis() + 15000;
		while (System.currentTimeMillis() < konec) {
			if (udalosti.stream().anyMatch(e -> e.getLevel() == Level.ERROR && zprava.equals(e.getFormattedMessage()))) {
				return;
			}
			Thread.sleep(50);
		}
		assertTrue("Chyba se nezalogovala: " + zprava, false);
	}
}
