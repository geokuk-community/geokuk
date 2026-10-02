package cz.geokuk.util.file;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/** Seznam resourců (souborů, ne složek) pod danou cestou ve všech místech classpath, v jaru i ve složce. */
public final class Resourcy {

	private Resourcy() {}

	public static Set<String> vypis(final ClassLoader classLoader, final String cesta) {
		final String predpona = cesta.endsWith("/") ? cesta : cesta + "/";
		final Set<String> vysledek = new TreeSet<>();
		try {
			for (final URL url : Collections.list(classLoader.getResources(cesta))) {
				if ("jar".equals(url.getProtocol())) {
					final JarURLConnection spojeni = (JarURLConnection) url.openConnection();
					spojeni.setUseCaches(false);
					try (JarFile jar = spojeni.getJarFile()) {
						for (final JarEntry polozka : Collections.list(jar.entries())) {
							if (!polozka.isDirectory() && polozka.getName().startsWith(predpona)) {
								vysledek.add(polozka.getName());
							}
						}
					}
				} else if ("file".equals(url.getProtocol())) {
					final Path koren = Paths.get(url.toURI());
					try (Stream<Path> soubory = Files.walk(koren)) {
						soubory.filter(Files::isRegularFile).forEach(f -> vysledek.add(predpona + koren.relativize(f).toString().replace(File.separatorChar, '/')));
					}
				}
			}
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		} catch (final URISyntaxException e) {
			throw new IllegalStateException(e);
		}
		return vysledek;
	}
}
