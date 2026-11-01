package cz.geokuk.core.coordinates;

/**
 * Převod mezi WGS84 a S-JTSK (EPSG:5514): Helmertova transformace na Besselův elipsoid a Krovakovo zobrazení. Hodnoty jsou ve tvaru, v jakém je používá ČÚZK:
 * záporné, {@code y} je osa na západ, {@code x} na jih. Přesnost je v řádu metrů, použitelné jen na území České republiky a okolí.
 * <p>
 * Parametry: Besselův elipsoid 1841 (a = 6377397,155 m, e² = 0,006674372230614); zobrazení φc = 49°30', λ0 = 24°50' východně od Greenwiche, azimut osy kužele 30°17'17,3031",
 * φp = 78°30', k0 = 0,9999; sedmiparametrová transformace z Besselova elipsoidu do WGS84 (pozicový vektor): posun 570,8285 / 85,6769 / 462,8374 m, otočení 4,9984" / 1,5867" / 5,2611",
 * měřítko 3,5623 ppm.
 */
public final class SJtsk {

	/** Souřadnice S-JTSK v metrech (obě záporné). */
	public static final class Bod {
		public final double y;
		public final double x;

		Bod(final double y, final double x) {
			this.y = y;
			this.x = x;
		}
	}

	private static final double A_WGS = 6378137.0;
	private static final double E2_WGS = 0.00669437999014;
	private static final double A_BESSEL = 6377397.155;
	private static final double E2_BESSEL = 0.006674372230614;
	private static final double E = Math.sqrt(E2_BESSEL);

	/** Posun, otočení (v obloukových vteřinách) a měřítko (ppm) z Besselova elipsoidu do WGS84, pozicový vektor. */
	private static final double DX = 570.8285, DY = 85.6769, DZ = 462.8374;
	private static final double RX = Math.toRadians(4.9984 / 3600), RY = Math.toRadians(1.5867 / 3600), RZ = Math.toRadians(5.2611 / 3600);
	private static final double MERITKO = 3.5623e-6;

	private static final double FI_C = Math.toRadians(49.5);
	private static final double LAMBDA_0 = Math.toRadians(24 + 50.0 / 60);
	private static final double ALFA = Math.toRadians(30 + 17.0 / 60 + 17.3031 / 3600);
	private static final double FI_P = Math.toRadians(78.5);
	private static final double K_0 = 0.9999;

	private static final double A = A_BESSEL * Math.sqrt(1 - E2_BESSEL) / (1 - E2_BESSEL * sqr(Math.sin(FI_C)));
	private static final double B = Math.sqrt(1 + E2_BESSEL * Math.pow(Math.cos(FI_C), 4) / (1 - E2_BESSEL));
	private static final double GAMA_0 = Math.asin(Math.sin(FI_C) / B);
	private static final double T_0 = Math.tan(Math.PI / 4 + GAMA_0 / 2) * Math.pow((1 + E * Math.sin(FI_C)) / (1 - E * Math.sin(FI_C)), E * B / 2) / Math.pow(Math.tan(Math.PI / 4 + FI_C / 2), B);
	private static final double N = Math.sin(FI_P);
	private static final double R_0 = K_0 * A / Math.tan(FI_P);

	private SJtsk() {}

	private static double sqr(final double x) {
		return x * x;
	}

	/** Převod z WGS84 (stupně) do S-JTSK. */
	public static Bod zWgs(final double lat, final double lon) {
		final double[] bessel = zWgsNaBessel(Math.toRadians(lat), Math.toRadians(lon));
		return krovak(bessel[0], bessel[1]);
	}

	/** Převod z S-JTSK do WGS84; pole {zeměpisná šířka, zeměpisná délka} ve stupních. */
	public static double[] naWgs(final double y, final double x) {
		final double[] bessel = zKrovaku(y, x);
		return besselNaWgs(bessel[0], bessel[1]);
	}

	private static Bod krovak(final double fi, final double lambda) {
		final double u = 2 * (Math.atan(T_0 * Math.pow(Math.tan(Math.PI / 4 + fi / 2), B) * Math.pow((1 - E * Math.sin(fi)) / (1 + E * Math.sin(fi)), E * B / 2)) - Math.PI / 4);
		final double v = B * (LAMBDA_0 - lambda);
		final double s = Math.asin(Math.cos(ALFA) * Math.sin(u) + Math.sin(ALFA) * Math.cos(u) * Math.cos(v));
		final double d = Math.asin(Math.cos(u) * Math.sin(v) / Math.cos(s));
		final double theta = N * d;
		final double r = R_0 * Math.pow(Math.tan(Math.PI / 4 + FI_P / 2), N) / Math.pow(Math.tan(s / 2 + Math.PI / 4), N);
		return new Bod(-r * Math.sin(theta), -r * Math.cos(theta));
	}

	private static double[] zKrovaku(final double y, final double x) {
		final double r = Math.hypot(x, y);
		final double theta = Math.atan2(-y, -x);
		final double d = theta / N;
		final double s = 2 * (Math.atan(Math.pow(R_0 / r, 1 / N) * Math.tan(Math.PI / 4 + FI_P / 2)) - Math.PI / 4);
		final double u = Math.asin(Math.cos(ALFA) * Math.sin(s) - Math.sin(ALFA) * Math.cos(s) * Math.cos(d));
		final double v = Math.asin(Math.cos(s) * Math.sin(d) / Math.cos(u));
		double fi = u;
		for (int i = 0; i < 20; i++) {
			fi = 2 * (Math.atan(Math.pow(T_0, -1 / B) * Math.pow(Math.tan(u / 2 + Math.PI / 4), 1 / B) * Math.pow((1 + E * Math.sin(fi)) / (1 - E * Math.sin(fi)), E / 2)) - Math.PI / 4);
		}
		return new double[] { fi, LAMBDA_0 - v / B };
	}

	private static double[] geocentricke(final double fi, final double lambda, final double a, final double e2) {
		final double n = a / Math.sqrt(1 - e2 * sqr(Math.sin(fi)));
		return new double[] { n * Math.cos(fi) * Math.cos(lambda), n * Math.cos(fi) * Math.sin(lambda), n * (1 - e2) * Math.sin(fi) };
	}

	private static double[] zGeocentrickych(final double[] p, final double a, final double e2) {
		final double lambda = Math.atan2(p[1], p[0]);
		final double rovnik = Math.hypot(p[0], p[1]);
		double fi = Math.atan2(p[2], rovnik * (1 - e2));
		for (int i = 0; i < 10; i++) {
			final double n = a / Math.sqrt(1 - e2 * sqr(Math.sin(fi)));
			fi = Math.atan2(p[2] + e2 * n * Math.sin(fi), rovnik);
		}
		return new double[] { fi, lambda };
	}

	/** Besselův elipsoid do WGS84. */
	private static double[] helmertBesselNaWgs(final double[] p) {
		final double m = 1 + MERITKO;
		return new double[] { DX + m * (p[0] + RZ * p[1] - RY * p[2]), DY + m * (-RZ * p[0] + p[1] + RX * p[2]), DZ + m * (RY * p[0] - RX * p[1] + p[2]) };
	}

	private static double[] besselNaWgs(final double fi, final double lambda) {
		final double[] w = zGeocentrickych(helmertBesselNaWgs(geocentricke(fi, lambda, A_BESSEL, E2_BESSEL)), A_WGS, E2_WGS);
		return new double[] { Math.toDegrees(w[0]), Math.toDegrees(w[1]) };
	}

	/** WGS84 na Bessela: inverze Helmertovy transformace, hledá se iterací. */
	private static double[] zWgsNaBessel(final double fi, final double lambda) {
		final double[] cil = geocentricke(fi, lambda, A_WGS, E2_WGS);
		double[] p = { cil[0] - DX, cil[1] - DY, cil[2] - DZ };
		for (int i = 0; i < 10; i++) {
			final double[] zpet = helmertBesselNaWgs(p);
			p = new double[] { p[0] + cil[0] - zpet[0], p[1] + cil[1] - zpet[1], p[2] + cil[2] - zpet[2] };
		}
		return zGeocentrickych(p, A_BESSEL, E2_BESSEL);
	}
}
