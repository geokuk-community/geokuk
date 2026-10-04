package cz.geokuk.util.process;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.*;

/** Odkaz z GPX nesmí otevřít soubor ani jiný než webový protokol, uživatel se to dozví. */
public class BrowserOpenerTest {

	private final List<URL> otevreno = new ArrayList<>();
	private final List<URL> ohlaseno = new ArrayList<>();
	private Consumer<URL> puvodni;
	private Consumer<URL> puvodniHlaska;

	@Before
	public void setUp() {
		puvodni = BrowserOpener.prohlizec;
		BrowserOpener.prohlizec = otevreno::add;
		puvodniHlaska = BrowserOpener.neniWebova;
		BrowserOpener.neniWebova = ohlaseno::add;
	}

	@After
	public void tearDown() {
		BrowserOpener.prohlizec = puvodni;
		BrowserOpener.neniWebova = puvodniHlaska;
	}

	@Test
	public void webovyOdkazSeOtevre() throws Exception {
		BrowserOpener.displayURL(new URL("https://coord.info/GC12345"));
		BrowserOpener.displayURL(new URL("http://www.geocaching.com/seek/cache_details.aspx?wp=GC12345"));
		Assert.assertEquals(2, otevreno.size());
	}

	@Test
	public void souborZOdkazuSeNeotevre() throws Exception {
		BrowserOpener.displayURL(new URL("file:///C:/Windows/System32/calc.exe"));
		BrowserOpener.displayURL(new URL("file://server/sdileni/x.html"));
		BrowserOpener.displayURL(new URL("jar:file:/x.jar!/a.html"));
		Assert.assertTrue(otevreno.isEmpty());
		Assert.assertEquals(3, ohlaseno.size());
	}

	@Test
	public void webovyOdkazSeNehlasi() throws Exception {
		BrowserOpener.displayURL(new URL("https://coord.info/GC12345"));
		BrowserOpener.displayURL(null);
		Assert.assertTrue(ohlaseno.isEmpty());
	}

	@Test
	public void vypisChybySeOtevre() throws Exception {
		BrowserOpener.displayFile(new URL("file:///tmp/geokuk/vyjimka.html"));
		Assert.assertEquals(1, otevreno.size());
	}
}
