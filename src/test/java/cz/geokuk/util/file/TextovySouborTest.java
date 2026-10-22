package cz.geokuk.util.file;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TextovySouborTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void utf8() {
		Assert.assertEquals("Jiří.jpg\n", TextovySoubor.dekoduj("Jiří.jpg\n".getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void cp1250ZeStarsiVerze() {
		Assert.assertEquals("Jiří.jpg\nŽluťoučký kůň\n", TextovySoubor.dekoduj("Jiří.jpg\nŽluťoučký kůň\n".getBytes(TextovySoubor.CP1250)));
	}

	@Test
	public void bomSeVynecha() {
		Assert.assertEquals("GC1", TextovySoubor.dekoduj("﻿GC1".getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void chybejiciSouborJePrazdny() throws Exception {
		Assert.assertEquals("", TextovySoubor.nacti(new File(tmp.getRoot(), "neni.ggt")));
	}

	@Test
	public void souborVCp1250() throws Exception {
		final File f = tmp.newFile("lovim.ggt");
		Files.write(f.toPath(), "GC1\r\nJiří.jpg\r\n".getBytes(TextovySoubor.CP1250));
		Assert.assertEquals("GC1\r\nJiří.jpg\r\n", TextovySoubor.nacti(f));
	}

	@Test
	public void souborVUtf16() throws Exception {
		final File le = tmp.newFile("le.ini");
		final byte[] text = "50.1 14.4 Domov Jiří\r\n".getBytes(StandardCharsets.UTF_16LE);
		final byte[] sBom = new byte[text.length + 2];
		sBom[0] = (byte) 0xFF;
		sBom[1] = (byte) 0xFE;
		System.arraycopy(text, 0, sBom, 2, text.length);
		Files.write(le.toPath(), sBom);
		Assert.assertEquals("50.1 14.4 Domov Jiří\r\n", TextovySoubor.nacti(le));
		final File be = tmp.newFile("be.ini");
		Files.write(be.toPath(), "50.1 14.4 Domov Jiří\r\n".getBytes(StandardCharsets.UTF_16));
		Assert.assertEquals("50.1 14.4 Domov Jiří\r\n", TextovySoubor.nacti(be));
	}
}
