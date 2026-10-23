package cz.geokuk.plugins.kesoid.genetika;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.*;

public class GenTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {}

	private Genom genom;
	private Collection<Gen> genyPred;

	@Before
	public void setUp() throws Exception {
		genom = new Genom();
		genyPred = new ArrayList<>(genom.getGeny());
	}

	@Test
	public void aktivniKesVeFiltru() {
		Assert.assertEquals("Aktivní", genom.ALELA_actv.getDisplayName());
	}

	@Test
	public void test1() {
		Assert.assertEquals("ahoj", genom.gen("ahoj").getDisplayName());
	}

	@Test
	public void test2() {
		Assert.assertEquals("kukuč to je", genom.gen("ahoj").displayName("kukuč to je").getDisplayName());
	}

	@Test
	public void test3() {
		Assert.assertSame(genom.gen("ahoj"), genom.gen("ahoj"));
	}

	@Test
	public void test4() {
		Assert.assertNotSame(genom.gen("ahoj"), genom.gen("ahojx"));
	}

	@Test
	public void test7() {
		genom.gen("1");
		genom.gen("2");
		genom.gen("2");
		genom.gen("3");
		Assert.assertEquals(genyPred.size() + 3, genom.getGeny().size());
	}

	@Test
	public void test8() {
		final Gen gen = genom.gen("gujin");
		Assert.assertEquals("gujin", gen.getNazev());
	}


	/** Alely přidává import keší, zatímco GUI je prochází; seznam proto musí být snímek. */
	@Test
	public void seznamAlelJeSnimek() {
		final Gen gen = genom.gen("snimek");
		gen.alela("a");
		final Set<Alela> alely = gen.getAlely();
		final int pred = alely.size();
		final Collection<Alela> vsechny = genom.getAlely();
		final int vsechnyPred = vsechny.size();
		final List<Gen> geny = genom.getGeny();
		final int genyPred = geny.size();
		gen.alela("b");
		genom.gen("dalsi");
		Assert.assertEquals(pred, alely.size());
		Assert.assertEquals(pred + 1, gen.getAlely().size());
		Assert.assertEquals(vsechnyPred, vsechny.size());
		Assert.assertEquals(genyPred, geny.size());
	}
}
