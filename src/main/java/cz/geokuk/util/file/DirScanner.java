package cz.geokuk.util.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import lombok.extern.slf4j.Slf4j;

/**
 * Třída je zodpovědna projití zadaných rootu a držení informací o tom, zda nedošlo ke změně.
 */
@Slf4j
public class DirScanner {

	// case insensitive, TODO : other image formats than JPG, raw and tif

	// TODO : Use file watchers
	private volatile List<Root> roots;
	private List<KeFile> lastScaned = null;
	/** Bez zámku, aby GUI nečekalo, než doběhne sken velké složky. */
	private volatile boolean nacistZnovu = true;
	private volatile Set<File> nedostupne = Collections.emptySet();

	/**
	 * Vrátí null, pokud není co načítat, protože nedošlo ke změně. Prázdný seznam je něco jiného, to ke změně došlo takové, že zmizely všechny soubory. Když se změní byť jediný soubor, je to změna a načítá se.
	 *
	 * @return
	 */
	public synchronized List<KeFile> coMamNacist() {
		final boolean vynutit = nacistZnovu;
		nacistZnovu = false;
		final Set<KeFile> set = new HashSet<>();
		final Set<File> nedostupneTed = new HashSet<>();
		for (final Root dir : roots) {
			final List<KeFile> li = scanDir(dir, nedostupneTed);
			set.addAll(li);
		}
		nedostupne = nedostupneTed;
		final List<KeFile> list = new ArrayList<>(set);
		if (!vynutit && list.equals(lastScaned)) {
			return null; // nezměnilo se nic
		}
		lastScaned = list;
		return list;
	}

	/** Kořeny a složky, které se při posledním {@link #coMamNacist()} nepodařilo přečíst; nevíme, co v nich je. */
	public Set<File> getNedostupne() {
		return nedostupne;
	}

	public void nulujLastScaned() {
		nacistZnovu = true;
	}

	public void seRootDirs(final boolean prenacti, final Root... roots) {
		final List<Root> newRoots = Arrays.asList(roots);
		if (!prenacti && newRoots.equals(this.roots)) {
			return;
		}
		this.roots = newRoots;
		nulujLastScaned();
	}

	private boolean matches(final String fileName, final Root.Def def) {
		if (def.patternExcludes != null) {
			if (def.patternExcludes.matcher(fileName).matches()) {
				return false;
			}
		}
		if (def.patternIncludes != null) {
			return def.patternIncludes.matcher(fileName).matches();
		} else {
			return true; // není matcher tak mečuje vše
		}
	}

	public List<KeFile> scan(final Root root) {
		return scanDir(root, new HashSet<>());
	}

	private List<KeFile> scanDir(final Root root, final Set<File> nedostupneSlozky) {
		if (!root.dir.exists()) {
			nedostupneSlozky.add(root.dir);
			return Collections.emptyList();
		}
		try {
			final List<KeFile> list = new ArrayList<>();
			Files.walkFileTree(root.dir.toPath(), EnumSet.of(FileVisitOption.FOLLOW_LINKS), root.def.maxDepth, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
					final boolean vynechat = !dir.equals(root.dir.toPath()) && root.vynechane.contains(dir.toAbsolutePath().normalize().toFile());
					return vynechat ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(final Path path, final BasicFileAttributes aAttrs) throws IOException {

					if (matches(path.getFileName().toString(), root.def)) {
						list.add(new KeFile(new FileAndTime(path.toFile()), root));
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(final Path path, final IOException e) {
					// nečitelná složka ani zacyklený odkaz nesmí shodit celý sken
					log.warn("Přeskakuji {}: {}", path, e.toString());
					nedostupneSlozky.add(path.toFile());
					return FileVisitResult.CONTINUE;
				}
			});
			return list;
		} catch (final Exception e) {
			throw new RuntimeException("Skenovani od " + root, e);
		}
	}
}
