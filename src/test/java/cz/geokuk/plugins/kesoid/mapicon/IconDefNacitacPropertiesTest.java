package cz.geokuk.plugins.kesoid.mapicon;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;

/** Soubor .properties ikony se po načtení zavře, ať ho jde na Windows smazat nebo přepsat. */
public class IconDefNacitacPropertiesTest {

	@Test
	public void streamSeZavre() throws Exception {
		final AtomicBoolean zavreno = new AtomicBoolean();
		final URL url = new URL(null, "test:ikona.properties", new URLStreamHandler() {
			@Override
			protected URLConnection openConnection(final URL u) {
				return new URLConnection(u) {
					@Override
					public void connect() {}

					@Override
					public InputStream getInputStream() {
						return new ByteArrayInputStream("class=x.Y\n".getBytes(StandardCharsets.ISO_8859_1)) {
							@Override
							public void close() throws IOException {
								zavreno.set(true);
								super.close();
							}
						};
					}
				};
			}
		});
		Assert.assertEquals("x.Y", IconDefNacitac.nactiProperties(url).getProperty("class"));
		Assert.assertTrue(zavreno.get());
	}
}
