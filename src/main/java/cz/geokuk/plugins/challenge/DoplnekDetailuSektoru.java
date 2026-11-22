package cz.geokuk.plugins.challenge;

import cz.geokuk.plugins.kesoid.Wpt;
import cz.geokuk.plugins.kesoid.detail.DoplnekDetailuKese;
import cz.geokuk.plugins.kesoid.detail.DoplnekDetailuModel;

/** Při zapnutém plánovači se zobrazenou challenge 360° doplní do detailu keše její sektor; jinak se z detailu odregistruje. */
public class DoplnekDetailuSektoru implements DoplnekDetailuKese {

	private ChallengeModel model;
	private DoplnekDetailuModel doplnky;
	/** Přehled, se kterým je doplněk naposledy zaregistrovaný; null = není zaregistrovaný. */
	private PrehledChallenge zaregistrovanyPrehled;

	public void inject(final ChallengeModel model) {
		this.model = model;
	}

	public void inject(final DoplnekDetailuModel doplnky) {
		this.doplnky = doplnky;
	}

	public void onEvent(final ChallengeZmenenyEvent event) {
		obnov();
	}

	/** Zaregistruje nebo odregistruje doplněk podle stavu plánovače; detail se obnoví jen při změně přehledu. */
	void obnov() {
		final PrehledChallenge p = prehled360();
		if (p == zaregistrovanyPrehled) {
			return;
		}
		zaregistrovanyPrehled = p;
		doplnky.nastav(p == null ? null : this);
	}

	private PrehledChallenge prehled360() {
		if (model == null || !model.jeZapnuto()) {
			return null;
		}
		final PrehledChallenge p = model.prehled();
		return p != null && p.challenge().typ() == Challenge.Typ.SEKTORY_360 && p.maStred() ? p : null;
	}

	@Override
	public String radek(final Wpt wpt) {
		final VybranaKes kes = SledovaniVybraneKese.zWpt(wpt);
		final PrehledChallenge p = prehled360();
		if (kes == null || p == null) {
			return null;
		}
		final int sektor = p.sektorBodu(kes.lat(), kes.lon());
		return sektor < 0 ? null : TextyKresleni.sektorDetailu(sektor, p.jeNejistyBod(kes.lat(), kes.lon()));
	}
}
