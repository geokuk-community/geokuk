package cz.geokuk.start;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CdsTest {

	private static final long TED = 1_000_000_000_000L;
	private static final long MB = 1L << 20;

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File data;
	private File javaHome;
	private File jar;
	private File zamek;
	private String id;
	private File slozka;

	@Before
	public void setUp() throws IOException {
		data = tmp.newFolder("data");
		javaHome = tmp.newFolder("java");
		new File(javaHome, "lib/server").mkdirs();
		velky(new File(javaHome, "lib/server/classes.jsa"), 13 * MB, 0);
		jar = tmp.newFile("geokuk.jar");
		velky(jar, 25 * MB, TED - 86_400_000);
		zamek = new File(data, Start.ZAMEK);
		id = Cds.id("21.0.5+11", javaHome, jar);
		slozka = new File(data, Cds.SLOZKA);
	}

	private static File velky(final File f, final long velikost, final long cas) throws IOException {
		try (RandomAccessFile r = new RandomAccessFile(f, "rw")) {
			r.setLength(velikost);
		}
		if (cas > 0) {
			assertTrue(f.setLastModified(cas));
		}
		return f;
	}

	private File hotovy() {
		slozka.mkdirs();
		return new File(slozka, Cds.PREDPONA_HOTOVEHO + id + Cds.PRIPONA);
	}

	/** Hotový archiv tak, jak ho zanechá povýšení: soubor a jeho zapsaná délka. */
	private File hotovyArchiv(final long velikost) throws IOException {
		final File f = velky(hotovy(), velikost, 0);
		java.nio.file.Files.write(Cds.souborDelky(f).toPath(), Long.toString(velikost).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		return f;
	}

	private File nove(final String koncovka, final long velikost, final long stari) throws IOException {
		slozka.mkdirs();
		return velky(new File(slozka, Cds.PREDPONA_NOVEHO + id + "-" + koncovka + Cds.PRIPONA), velikost, TED - stari);
	}

	private List<String> prepinace(final String verze, final Cds.Overeni overeni) {
		return Cds.prepinace(verze, javaHome, "21.0.5+11", data, jar, zamek, overeni, TED);
	}

	private List<String> prepinace() {
		return prepinace("21", a -> true);
	}

	@Test
	public void odJavy25SeVytvariAotCache() {
		final List<String> p = prepinace("25", a -> true);
		assertEquals(Cds.KONTROLA, p.get(0));
		assertTrue(p.get(1), p.get(1).startsWith("-XX:AOTCacheOutput=" + slozka.getPath()));
	}

	@Test
	public void hotovouAotCacheJavy25SePouzije() throws IOException {
		hotovyArchiv(38 * MB);
		assertEquals(Arrays.asList(Cds.KONTROLA, "-XX:AOTCache=" + hotovy().getPath()), prepinace("25", a -> true));
	}

	@Test
	public void priPlnemDiskuSeArchivNevytvari() {
		assertEquals(Collections.emptyList(), Cds.prepinace("21", javaHome, "21.0.5+11", data, jar, zamek, a -> true, TED, () -> Cds.MIN_VOLNE_MISTO_B - 1));
		assertEquals(2, Cds.prepinace("21", javaHome, "21.0.5+11", data, jar, zamek, a -> true, TED, () -> Cds.MIN_VOLNE_MISTO_B).size());
	}

	@Test
	public void priPlnemDiskuSeHotovyArchivPresto() throws IOException {
		hotovyArchiv(38 * MB);
		assertEquals(Arrays.asList(Cds.KONTROLA, "-XX:SharedArchiveFile=" + hotovy().getPath()), Cds.prepinace("21", javaHome, "21.0.5+11", data, jar, zamek, a -> true, TED, () -> 0));
	}

	@Test
	public void archivJenProCteniSeSmaze() throws IOException {
		slozka.mkdirs();
		final File stary = velky(new File(slozka, Cds.PREDPONA_HOTOVEHO + "21.0.1-1-2-3" + Cds.PRIPONA), 30 * MB, 0);
		assertTrue(stary.setWritable(false));
		prepinace();
		assertFalse("Windows nesmažou soubor jen pro čtení", stary.exists());
	}

	@Test
	public void starsiJavaBezPrepinacu() {
		assertEquals(Collections.emptyList(), prepinace("17", a -> true));
		assertEquals(Collections.emptyList(), prepinace("1.8", a -> true));
		assertEquals(Collections.emptyList(), prepinace("neznama", a -> true));
		assertFalse("složka se kvůli staré Javě nezakládá", slozka.exists());
	}

	@Test
	public void javaOdVerze19() {
		assertTrue(Cds.jeJavaDostatecneNova("19"));
		assertTrue(Cds.jeJavaDostatecneNova("21"));
		assertTrue(Cds.jeJavaDostatecneNova("25"));
		assertFalse(Cds.jeJavaDostatecneNova("18"));
		assertFalse(Cds.jeJavaDostatecneNova("1.8"));
	}

	@Test
	public void bezArchivuSeVytvoriVRozepsanemSouboru() {
		final List<String> p = prepinace();
		assertEquals(2, p.size());
		assertEquals(Cds.KONTROLA, p.get(0));
		assertTrue(p.get(1), p.get(1).startsWith("-XX:ArchiveClassesAtExit=" + slozka.getPath() + File.separator + Cds.PREDPONA_NOVEHO + id + "-"));
		assertFalse("hotový archiv se nezakládá předem", hotovy().exists());
	}

	@Test
	public void kazdyBehMaVlastniRozepsanySoubor() {
		assertNotEquals(prepinace(), prepinace());
	}

	@Test
	public void bezZakladnihoArchivuJavyNeniCoVytvaret() {
		assertTrue(new File(javaHome, "lib/server/classes.jsa").delete());
		assertEquals(Collections.emptyList(), prepinace());
	}

	@Test
	public void zakladniArchivNaWindowsJeVBinServer() throws IOException {
		assertTrue(new File(javaHome, "lib/server/classes.jsa").delete());
		assertFalse(Cds.maZakladniArchiv(javaHome));
		new File(javaHome, "bin/server").mkdirs();
		velky(new File(javaHome, "bin/server/classes.jsa"), 13 * MB, 0);
		assertTrue(Cds.maZakladniArchiv(javaHome));
		assertEquals(2, prepinace().size());
	}

	@Test
	public void zakladniArchivBezCoopsStaci() throws IOException {
		assertTrue(new File(javaHome, "lib/server/classes.jsa").delete());
		velky(new File(javaHome, "lib/server/classes_nocoops.jsa"), 13 * MB, 0);
		assertTrue(Cds.maZakladniArchiv(javaHome));
	}

	@Test
	public void hotovyArchivSePouzije() throws IOException {
		hotovyArchiv(38 * MB);
		assertEquals(Arrays.asList(Cds.KONTROLA, "-XX:SharedArchiveFile=" + hotovy().getPath()), prepinace());
		assertTrue("hotový archiv se nemaže", hotovy().isFile());
	}

	@Test
	public void bezBeznehoZakladnihoArchivuSePouzijeHotovy() throws IOException {
		hotovyArchiv(38 * MB);
		assertTrue(new File(javaHome, "lib/server/classes.jsa").delete());
		assertEquals(Arrays.asList(Cds.KONTROLA, "-XX:SharedArchiveFile=" + hotovy().getPath()), prepinace());
	}

	@Test
	public void kdyzUzJinaInstanceBeziArchivSeNevytvari() throws IOException {
		final FileLock lock = Start.zamkni(zamek);
		assertNotNull(lock);
		try {
			assertEquals(Collections.emptyList(), prepinace());
		} finally {
			lock.release();
			lock.channel().close();
		}
	}

	@Test
	public void bezZapisuDoDataBezPrepinacu() throws IOException {
		assertTrue(new File(data, Cds.SLOZKA).createNewFile());
		assertEquals(Collections.emptyList(), prepinace());
	}

	@Test
	public void maleNebPoskozeneArchivSeSmazeAVytvoriSeZnovu() throws IOException {
		slozka.mkdirs();
		hotovyArchiv(8);
		final List<String> p = prepinace();
		assertFalse(hotovy().exists());
		assertEquals(2, p.size());
		assertTrue(p.get(1).startsWith("-XX:ArchiveClassesAtExit="));
	}

	@Test
	public void oriznutyNeboZmenenyArchivSeSmaze() throws IOException {
		hotovyArchiv(38 * MB);
		velky(hotovy(), 20 * MB, 0);
		final List<String> p = prepinace();
		assertFalse(hotovy().exists());
		assertFalse(Cds.souborDelky(hotovy()).exists());
		assertTrue(p.get(1).startsWith("-XX:ArchiveClassesAtExit="));
	}

	@Test
	public void archivBezZapsaneDelkySeSmaze() throws IOException {
		velky(hotovy(), 38 * MB, 0);
		assertTrue(prepinace().get(1).startsWith("-XX:ArchiveClassesAtExit="));
		assertFalse(hotovy().exists());
	}

	@Test
	public void povyseniZapiseDelkuAUklidiOsirele() throws IOException {
		nove("a", 30 * MB, 120_000);
		final File osirela = new File(slozka, Cds.PREDPONA_HOTOVEHO + "stary" + Cds.PRIPONA + Cds.PRIPONA_DELKY);
		assertTrue(osirela.createNewFile());
		prepinace();
		assertEquals(String.valueOf(30 * MB), new String(java.nio.file.Files.readAllBytes(Cds.souborDelky(hotovy()).toPath()), java.nio.charset.StandardCharsets.UTF_8));
		assertFalse(osirela.exists());
		assertEquals(Arrays.asList(Cds.KONTROLA, "-XX:SharedArchiveFile=" + hotovy().getPath()), prepinace());
	}

	@Test
	public void archivyStareVerzeSeMazouCiziSouboryNe() throws IOException {
		slozka.mkdirs();
		final File stary = velky(new File(slozka, Cds.PREDPONA_HOTOVEHO + "21.0.1-1-2-3" + Cds.PRIPONA), 30 * MB, 0);
		final File cizi = new File(slozka, "poznamka.txt");
		assertTrue(cizi.createNewFile());
		final File ciziJsa = velky(new File(slozka, "moje.jsa"), 6 * MB, TED - 100_000);
		hotovyArchiv(38 * MB);
		prepinace();
		assertFalse(stary.exists());
		assertTrue(cizi.exists());
		assertTrue("cizí archiv se nemaže", ciziJsa.exists());
		assertTrue(hotovy().exists());
	}

	@Test
	public void jarNeboJavaNeboMistoMeniJmenoArchivu() throws IOException {
		final String puvodni = id;
		velky(jar, 26 * MB, TED - 1000);
		assertNotEquals(puvodni, Cds.id("21.0.5+11", javaHome, jar));
		assertNotEquals(Cds.id("21.0.5+11", javaHome, jar), Cds.id("21.0.6+7", javaHome, jar));
		assertNotEquals(Cds.id("21.0.5+11", javaHome, jar), Cds.id("21.0.5+11", new File(javaHome, "jina"), jar));
		assertNotEquals(Cds.id("21.0.5+11", javaHome, jar), Cds.id("21.0.5+11", javaHome, new File(tmp.getRoot(), "jinde/geokuk.jar")));
		assertTrue(Cds.id("21 0/5:x", javaHome, jar).matches("[A-Za-z0-9.+_\\-]+"));
	}

	@Test
	public void novyJarSmazeStarouVerziAVytvoriNovou() throws IOException {
		hotovyArchiv(38 * MB);
		final File puvodni = hotovy();
		velky(jar, 26 * MB, TED - 1000);
		id = Cds.id("21.0.5+11", javaHome, jar);
		final List<String> p = prepinace();
		assertFalse("archiv původního jaru se smaže", puvodni.exists());
		assertTrue(p.get(1).startsWith("-XX:ArchiveClassesAtExit="));
	}

	@Test
	public void nejvetsiRozepsanyArchivSePovysi() throws IOException {
		final File mensi = nove("a", 6 * MB, 120_000);
		final File vetsi = nove("b", 30 * MB, 60_000);
		final List<File> overovane = new ArrayList<>();
		final List<String> p = prepinace("21", a -> overovane.add(a));
		assertEquals(Collections.singletonList(vetsi), overovane);
		assertEquals(Arrays.asList(Cds.KONTROLA, "-XX:SharedArchiveFile=" + hotovy().getPath()), p);
		assertEquals(30 * MB, hotovy().length());
		assertFalse(vetsi.exists());
		assertFalse("ostatní rozepsané se uklidí", mensi.exists());
	}

	@Test
	public void neoverenyArchivSeNepovysiASmaze() throws IOException {
		final File kandidat = nove("a", 30 * MB, 120_000);
		final List<String> p = prepinace("21", a -> false);
		assertFalse(hotovy().exists());
		assertFalse(kandidat.exists());
		assertTrue("vytvoří se znovu", p.get(1).startsWith("-XX:ArchiveClassesAtExit="));
	}

	@Test
	public void zapisovanyArchivSeNecha() throws IOException {
		final File mlady = nove("a", 30 * MB, 5_000);
		final AtomicInteger overeno = new AtomicInteger();
		final List<String> p = prepinace("21", a -> overeno.incrementAndGet() > 0);
		assertEquals(0, overeno.get());
		assertTrue("program do něj ještě může zapisovat", mlady.exists());
		assertEquals("archiv čeká na povýšení, nezapisuje se znovu", Collections.emptyList(), p);
	}

	@Test
	public void rozepsanyArchivJinehoJaruSePovysiNeniAleSmaze() throws IOException {
		slozka.mkdirs();
		final File cizi = velky(new File(slozka, Cds.PREDPONA_NOVEHO + "21.0.1-1-2-3-abc" + Cds.PRIPONA), 30 * MB, TED - 120_000);
		final List<File> overovane = new ArrayList<>();
		prepinace("21", a -> overovane.add(a));
		assertTrue(overovane.isEmpty());
		assertFalse(hotovy().exists());
		assertFalse(cizi.exists());
	}

	@Test
	public void maleRozepsaneArchivySeNepovysuji() throws IOException {
		final File maly = nove("a", 100, 120_000);
		prepinace("21", a -> true);
		assertFalse(hotovy().exists());
		assertFalse(maly.exists());
	}

	@Test
	public void nezapisovatelnaSlozkaNebeziSDlouhymVyjimkami() {
		// Neexistující a nevytvořitelná datová složka (rodič je soubor) nesmí start rozbít.
		final List<String> p = Cds.prepinace("21", javaHome, "21", new File(jar, "data"), jar, zamek, a -> true, TED);
		assertEquals(Collections.emptyList(), p);
	}
}
