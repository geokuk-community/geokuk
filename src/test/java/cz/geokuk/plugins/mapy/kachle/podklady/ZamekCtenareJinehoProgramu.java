package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.TimeUnit;

/**
 * Druhý program, který drží sdílený zámek SQLite (bajty SHARED za PENDING_BYTE) jako čtenář: zápis se rozběhne, ale commit nedostane výhradní zámek. Zámky souborů platí mezi procesy, proto
 * běží ve vlastní JVM: vypíše „zamceno“ a drží zámek, dokud nedostane řádek na vstupu nebo neuplyne limit.
 */
public final class ZamekCtenareJinehoProgramu {

	private static final long PENDING_BYTE = 0x40000000L;

	public static void main(final String[] args) throws Exception {
		try (RandomAccessFile soubor = new RandomAccessFile(new File(args[0]), "rw"); FileChannel kanal = soubor.getChannel();
				FileLock zamek = kanal.lock(PENDING_BYTE + 2, 510, true)) {
			System.out.println("zamceno");
			System.out.flush();
			final long konec = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
			while (System.in.available() == 0 && System.nanoTime() < konec) {
				Thread.sleep(20);
			}
		}
	}

	private ZamekCtenareJinehoProgramu() {}
}
