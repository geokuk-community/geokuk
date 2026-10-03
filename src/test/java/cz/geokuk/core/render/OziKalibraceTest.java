package cz.geokuk.core.render;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;

public class OziKalibraceTest {

	@Test
	public void severovychod() {
		assertEquals("Point01,xy,10,20,in, deg,50,     7.500,N,14,    22.500,E, grid,,,,N", OziExplorerRenderSwingWorker.oziKalibracniBod(1, 10, 20, new Wgs(50.125, 14.375)));
	}

	@Test
	public void zapadAJihMajiKladneStupne() {
		assertEquals("Point02,xy,0,0,in, deg,51,    30.000,N,0,     7.800,W, grid,,,,N", OziExplorerRenderSwingWorker.oziKalibracniBod(2, 0, 0, new Wgs(51.5, -0.13)));
		assertEquals("Point03,xy,0,0,in, deg,33,    51.900,S,151,    12.600,E, grid,,,,N", OziExplorerRenderSwingWorker.oziKalibracniBod(3, 0, 0, new Wgs(-33.865, 151.21)));
	}

	@Test
	public void minutySeZaokrouhliNa60() {
		assertEquals("Point04,xy,0,0,in, deg,50,     0.000,N,15,     0.000,E, grid,,,,N", OziExplorerRenderSwingWorker.oziKalibracniBod(4, 0, 0, new Wgs(49.9999999999, 14.99999999999)));
	}
}
