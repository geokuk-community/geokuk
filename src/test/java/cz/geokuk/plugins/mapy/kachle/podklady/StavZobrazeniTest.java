package cz.geokuk.plugins.mapy.kachle.podklady;

import static org.junit.Assert.*;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Soubor stavu posledního zobrazení: co se zapíše, to se přečte; cokoli jiného neplatí celé. */
public class StavZobrazeniTest {

	@Rule
	public final TemporaryFolder tmp = new TemporaryFolder();

	private static final StavZobrazeni.Hlavicka H = new StavZobrazeni.Hlavicka(123L, "TURIST_M", 14, 1000, -2000);

	private static StavZobrazeni.Zaznam zaznam(final int i, final int delka) {
		final byte[] data = new byte[delka];
		Arrays.fill(data, (byte) i);
		return new StavZobrazeni.Zaznam("TURIST_M", 14, 9000 + i, 5600, "TURIST_M", data);
	}

	@Test
	public void zapsaneSePrecte() throws Exception {
		final File s = tmp.newFolder();
		assertEquals(2, StavZobrazeni.zapis(s, H, Arrays.asList(zaznam(1, 100), zaznam(2, 200))));
		final StavZobrazeni.Stav st = StavZobrazeni.nacti(s);
		assertNotNull(st);
		assertEquals(123L, st.hlavicka.cas);
		assertEquals("TURIST_M", st.hlavicka.podklad);
		assertEquals(14, st.hlavicka.moumer);
		assertEquals(1000, st.hlavicka.mouX);
		assertEquals(-2000, st.hlavicka.mouY);
		assertEquals(2, st.zaznamy.size());
		assertEquals(9002, st.zaznamy.get(1).x);
		assertArrayEquals(zaznam(2, 200).data, st.zaznamy.get(1).data);
		assertFalse("dočasný soubor nezůstane", new File(s, StavZobrazeni.SOUBOR + ".tmp").exists());
	}

	@Test
	public void bezSouboruNicNeplati() throws Exception {
		assertNull(StavZobrazeni.nacti(tmp.newFolder()));
	}

	@Test
	public void zaznamyNadStropSeNezapisou() throws Exception {
		final File s = tmp.newFolder();
		final int tretina = StavZobrazeni.MAX_DATA / 3;
		final List<StavZobrazeni.Zaznam> z = Arrays.asList(zaznam(1, tretina), zaznam(2, tretina), zaznam(3, tretina), zaznam(4, tretina), zaznam(5, 10));
		assertEquals("v pořadí, dokud se vejdou", 3, StavZobrazeni.zapis(s, H, z));
		final StavZobrazeni.Stav st = StavZobrazeni.nacti(s);
		assertEquals(3, st.zaznamy.size());
		assertEquals(9003, st.zaznamy.get(2).x);
	}

	@Test
	public void poskozenyBajtZneplatniCelySoubor() throws Exception {
		final File s = tmp.newFolder();
		StavZobrazeni.zapis(s, H, Arrays.asList(zaznam(1, 100), zaznam(2, 100)));
		final File f = new File(s, StavZobrazeni.SOUBOR);
		final long delka = f.length();
		for (final long pozice : new long[] { 9, 30, delka / 2, delka - 12, delka - 1 }) {
			final byte[] puvodni = Files.readAllBytes(f.toPath());
			try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
				raf.seek(pozice);
				final int b = raf.read();
				raf.seek(pozice);
				raf.write(b ^ 0x5A);
			}
			assertNull("poškozený bajt na " + pozice, StavZobrazeni.nacti(s));
			Files.write(f.toPath(), puvodni);
			assertNotNull(StavZobrazeni.nacti(s));
		}
	}

	@Test
	public void useknutyNeboProdlouzenySouborNeplati() throws Exception {
		final File s = tmp.newFolder();
		StavZobrazeni.zapis(s, H, Arrays.asList(zaznam(1, 100), zaznam(2, 100)));
		final File f = new File(s, StavZobrazeni.SOUBOR);
		final byte[] cele = Files.readAllBytes(f.toPath());
		Files.write(f.toPath(), Arrays.copyOf(cele, cele.length - 1));
		assertNull("useknutý", StavZobrazeni.nacti(s));
		Files.write(f.toPath(), Arrays.copyOf(cele, cele.length + 1));
		assertNull("s bajtem navíc", StavZobrazeni.nacti(s));
	}

	@Test
	public void jinaVerzeNeboCiziSouborNeplati() throws Exception {
		final File s = tmp.newFolder();
		StavZobrazeni.zapis(s, H, Arrays.asList(zaznam(1, 100)));
		final File f = new File(s, StavZobrazeni.SOUBOR);
		final byte[] cele = Files.readAllBytes(f.toPath());
		final byte[] verze = cele.clone();
		verze[7] = (byte) (StavZobrazeni.VERZE + 1);
		Files.write(f.toPath(), verze);
		assertNull("jiná verze", StavZobrazeni.nacti(s));
		final byte[] magic = cele.clone();
		magic[0] = 'X';
		Files.write(f.toPath(), magic);
		assertNull("cizí soubor", StavZobrazeni.nacti(s));
	}

	/** Soubor nad limit se nečte, i když má jinak platný formát (sestavený tu ručně, zápis by tolik dat nevzal). */
	@Test
	public void prilisVelkySouborSeNecte() throws Exception {
		final File s = tmp.newFolder();
		final int pul = (int) (StavZobrazeni.MAX_SOUBOR / 2) + 1;
		try (java.io.DataOutputStream out = new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(new File(s, StavZobrazeni.SOUBOR))))) {
			out.writeInt(StavZobrazeni.MAGIC);
			out.writeInt(StavZobrazeni.VERZE);
			final java.io.ByteArrayOutputStream hb = new java.io.ByteArrayOutputStream();
			final java.io.DataOutputStream hd = new java.io.DataOutputStream(hb);
			hd.writeLong(1);
			hd.writeUTF("TURIST_M");
			hd.writeInt(14);
			hd.writeInt(0);
			hd.writeInt(0);
			hd.writeInt(2);
			cast(out, hb.toByteArray());
			for (int i = 0; i < 2; i++) {
				final java.io.ByteArrayOutputStream zb = new java.io.ByteArrayOutputStream();
				final java.io.DataOutputStream zd = new java.io.DataOutputStream(zb);
				zd.writeUTF("TURIST_M");
				zd.writeInt(14);
				zd.writeInt(i);
				zd.writeInt(0);
				zd.writeUTF("TURIST_M");
				zd.writeInt(pul);
				zd.write(new byte[pul]);
				cast(out, zb.toByteArray());
			}
		}
		assertTrue(new File(s, StavZobrazeni.SOUBOR).length() > StavZobrazeni.MAX_SOUBOR);
		assertNull(StavZobrazeni.nacti(s));
	}

	private static void cast(final java.io.DataOutputStream out, final byte[] b) throws java.io.IOException {
		out.writeInt(b.length);
		out.write(b);
		final java.util.zip.CRC32 crc = new java.util.zip.CRC32();
		crc.update(b);
		out.writeLong(crc.getValue());
	}

	/** Nepovedený zápis nechá dosavadní soubor beze změny. */
	@Test
	public void chybaZapisuNechaDosavadniSoubor() throws Exception {
		final File s = tmp.newFolder();
		StavZobrazeni.zapis(s, H, Arrays.asList(zaznam(1, 100)));
		final byte[] puvodni = Files.readAllBytes(new File(s, StavZobrazeni.SOUBOR).toPath());
		assertTrue("dočasný soubor nejde vytvořit", new File(s, StavZobrazeni.SOUBOR + ".tmp").mkdir());
		try {
			StavZobrazeni.zapis(s, H, Arrays.asList(zaznam(2, 300)));
			fail("zápis měl selhat");
		} catch (final java.io.IOException e) {
			// očekáváno
		}
		assertArrayEquals(puvodni, Files.readAllBytes(new File(s, StavZobrazeni.SOUBOR).toPath()));
		assertEquals(1, StavZobrazeni.nacti(s).zaznamy.get(0).data[0]);
	}

	@Test
	public void prazdnyVyrezSeZapise() throws Exception {
		final File s = tmp.newFolder();
		assertEquals(0, StavZobrazeni.zapis(s, H, new ArrayList<>()));
		assertEquals(0, StavZobrazeni.nacti(s).zaznamy.size());
	}
}
