package cz.geokuk.util.file;

import java.io.File;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Složku, kterou nejde přečíst ({@link File#list()} vrátí null), prohledávání přeskočí. */
public class MultiFolderNecitelnaTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void necitelnaSlozkaSePreskoci() throws Exception {
		final File slozka = new File(tmp.getRoot().getPath()) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isDirectory() {
				return true;
			}

			@Override
			public String[] list() {
				return null;
			}
		};
		new MultiFolder().addFolderTree(slozka);
	}
}
