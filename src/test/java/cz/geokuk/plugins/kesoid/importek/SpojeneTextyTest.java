package cz.geokuk.plugins.kesoid.importek;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.Arrays;

import org.junit.*;

/** Texty řádku jedním čtením sloupce; po sloupcích jen řádek, kde text obsahuje oddělovač. */
public class SpojeneTextyTest {

	private Connection c;
	private int volani;

	@Before
	public void setUp() throws SQLException {
		c = DriverManager.getConnection("jdbc:sqlite::memory:");
		try (Statement s = c.createStatement()) {
			s.execute("CREATE TABLE t (a TEXT, b TEXT, c TEXT)");
		}
	}

	@After
	public void tearDown() throws SQLException {
		c.close();
	}

	@Test
	public void bezOddelovaceJedinoCteni() throws SQLException {
		final String[] r = radek("Keš", null, "");
		Assert.assertArrayEquals(new String[] { "Keš", null, "" }, r);
		Assert.assertEquals(1, volani);
	}

	@Test
	public void sOddelovacemPoSloupcich() throws SQLException {
		Assert.assertArrayEquals(new String[] { "a\u001eb", null, "x" }, radek("a\u001eb", null, "x"));
		Assert.assertEquals(4, volani);
		Assert.assertArrayEquals(new String[] { null, "\u001f", "" }, radek(null, "\u001f", ""));
		Assert.assertEquals(4, volani);
	}

	@Test
	public void nullAPrazdnyRozlisene() throws SQLException {
		Assert.assertArrayEquals(new String[] { null, "", null }, radek(null, "", null));
		Assert.assertArrayEquals(new String[] { "", "", "" }, radek("", "", ""));
		Assert.assertEquals(1, volani);
	}

	/** Vloží řádek, přečte ho přes {@link SpojeneTexty} a spočítá volání getString. */
	private String[] radek(final String a, final String b, final String c3) throws SQLException {
		try (Statement s = c.createStatement()) {
			s.execute("DELETE FROM t");
		}
		try (PreparedStatement ps = c.prepareStatement("INSERT INTO t VALUES (?, ?, ?)")) {
			ps.setString(1, a);
			ps.setString(2, b);
			ps.setString(3, c3);
			ps.executeUpdate();
		}
		volani = 0;
		try (Statement s = c.createStatement(); ResultSet rs = pocitej(s.executeQuery("SELECT a, b, c, " + SpojeneTexty.vyraz(Arrays.asList("a", "b", "c")) + " FROM t"))) {
			final SpojeneTexty texty = new SpojeneTexty(4, 1, 2, 3);
			Assert.assertTrue(rs.next());
			texty.nacti(rs);
			return new String[] { texty.get(1), texty.get(2), texty.get(3) };
		}
	}

	private ResultSet pocitej(final ResultSet rs) {
		return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ResultSet.class }, (proxy, m, args) -> {
			if (m.getName().equals("getString")) {
				volani++;
			}
			return m.invoke(rs, args);
		});
	}
}
