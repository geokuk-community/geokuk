package cz.geokuk.util.file;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import lombok.extern.slf4j.Slf4j;

/**
 * Hlídá, jestli se ve složce (a v podsložkách do dané hloubky) něco změnilo, aniž by ji procházela: při nečinnosti stojí dotaz jen prázdnou frontu událostí systému.
 * Kde události nejdou zaregistrovat, nebo kdyby nedorazily (třeba na síťovém disku), ptá se na změnu {@link #zmenaOdPosledniho()} vždy kladně, resp. aspoň jednou za
 * {@link #JISTOTA}, takže se složka dál prochází jako dřív.
 * <p>
 * Nepoužívá vlastní vlákno; volá se z vlákna, které složku jinak kontroluje.
 */
@Slf4j
public final class SledovaniSlozky implements AutoCloseable {

	/** Nejdéle za jak dlouho se složka projde i bez události. */
	public static final long JISTOTA = TimeUnit.SECONDS.toNanos(60);

	private final Path koren;
	private final int hloubka;
	private final LongSupplier hodiny;
	private final long jistotaNs;
	private WatchService sluzba;
	private boolean spolehlive;
	private long posledniZmena;
	private boolean prvni = true;

	public SledovaniSlozky(final Path koren, final int hloubka) {
		this(koren, hloubka, System::nanoTime, JISTOTA);
	}

	SledovaniSlozky(final Path koren, final int hloubka, final LongSupplier hodiny, final long jistotaNs) {
		this.koren = koren;
		this.hloubka = hloubka;
		this.hodiny = hodiny;
		this.jistotaNs = jistotaNs;
		try {
			if (!Files.isDirectory(koren)) {
				throw new IOException("není složka");
			}
			sluzba = koren.getFileSystem().newWatchService();
			zaregistruj(koren, hloubka);
			spolehlive = true;
		} catch (final IOException | RuntimeException e) {
			log.debug("Složku {} nejde sledovat, bude se procházet: {}", koren, e.toString());
			zavri();
		}
	}

	public Path getKoren() {
		return koren;
	}

	/** Událost od posledního volání nebo uplynula pojistka; bez fungujícího sledování vždy true. První volání je vždy true. */
	public synchronized boolean zmenaOdPosledniho() {
		final long ted = hodiny.getAsLong();
		boolean zmena = prvni || !spolehlive;
		prvni = false;
		if (spolehlive) {
			zmena |= vyzvedniUdalosti();
		}
		if (!zmena && ted - posledniZmena >= jistotaNs) {
			zmena = true;
		}
		if (zmena) {
			posledniZmena = ted;
		}
		return zmena;
	}

	private boolean vyzvedniUdalosti() {
		boolean zmena = false;
		try {
			for (WatchKey klic = sluzba.poll(); klic != null; klic = sluzba.poll()) {
				final Path slozka = (Path) klic.watchable();
				for (final WatchEvent<?> e : klic.pollEvents()) {
					zmena = true;
					if (e.kind() == StandardWatchEventKinds.ENTRY_CREATE && e.context() instanceof Path) {
						final Path nova = slozka.resolve((Path) e.context());
						if (Files.isDirectory(nova, LinkOption.NOFOLLOW_LINKS)) {
							zaregistruj(nova, hloubka - koren.relativize(nova).getNameCount());
						}
					}
				}
				if (!klic.reset()) {
					zmena = true; // složka zanikla nebo se přestala sledovat
				}
			}
		} catch (final ClosedWatchServiceException e) {
			spolehlive = false;
			return true;
		} catch (final IOException | RuntimeException e) {
			log.debug("Sledování složky {} selhalo, bude se procházet: {}", koren, e.toString());
			zavri();
			return true;
		}
		return zmena;
	}

	private void zaregistruj(final Path slozka, final int zbyvaHloubky) throws IOException {
		if (zbyvaHloubky < 1) {
			return;
		}
		Files.walkFileTree(slozka, java.util.Collections.<FileVisitOption> emptySet(), zbyvaHloubky, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
				dir.register(sluzba, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private void zavri() {
		spolehlive = false;
		if (sluzba != null) {
			try {
				sluzba.close();
			} catch (final IOException e) {
				log.debug("Sledování složky {} nejde zavřít: {}", koren, e.toString());
			}
		}
	}

	@Override
	public synchronized void close() {
		zavri();
	}
}
