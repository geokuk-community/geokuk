package cz.geokuk.plugins.mapy.stahovac;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.*;

import cz.geokuk.plugins.mapy.kachle.podklady.*;

/** Hromadné stahování počítá výsledky a při omezení ze strany serveru přestane. */
public class DavkaStahovaniTest {

	@Test
	public void hotovoAzPoVsechVysledcich() {
		final DavkaStahovani davka = new DavkaStahovani(2, () -> {});
		davka.zarazeno(Kanceler.EMPTY);
		davka.zarazeno(Kanceler.EMPTY);
		davka.zarazovaniSkonceno();
		davka.prijemce().send(new KachloStav(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)));
		Assert.assertFalse(davka.jeHotova());
		davka.prijemce().send(new KachloStav(new IOException("výpadek")));
		Assert.assertTrue(davka.jeHotova());
		Assert.assertTrue(davka.popis(), davka.popis().startsWith("Hotovo: v cache je 1 z 2 dlaždic, chyb 1."));
	}

	@Test
	public void omezeniServeruZastaviDavku() {
		final AtomicInteger zruseno = new AtomicInteger();
		final DavkaStahovani davka = new DavkaStahovani(3, () -> {});
		davka.zarazeno(zruseno::incrementAndGet);
		davka.zarazeno(zruseno::incrementAndGet);
		davka.prijemce().send(new KachloStav(new KachloDownloader.ChybaServeru(429, "Too Many Requests")));
		Assert.assertTrue(davka.jeZastavena());
		Assert.assertEquals("zařazené dlaždice se zruší", 2, zruseno.get());
		Assert.assertFalse("další se už nezařadí", davka.zarazeno(zruseno::incrementAndGet));
		Assert.assertTrue(davka.popis(), davka.popis().contains("HTTP 429"));
	}
}
