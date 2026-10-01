package cz.geokuk.plugins.kesoid.mapicon;

import java.util.*;

import org.junit.*;

import cz.geokuk.plugins.kesoid.genetika.*;

/** Otevření filtru nad daty bez některých alel nesmí ty alely z filtru vyhodit. */
public class JVyberIkon0Test {

	@Test
	public void nevykreslenaVybranaAlelaZustane() {
		final List<QualAlelaNames> zmeny = new ArrayList<>();
		final JVyberIkon0 vyber = new JVyberIkon0(false, true) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean shouldEnable(final Alela alela) {
				return true;
			}

			@Override
			protected boolean shouldRender(final Alela alela) {
				return false;
			}

			@Override
			protected boolean shouldRender(final Gen gen) {
				return false;
			}

			@Override
			protected void zmenaVyberu(final Set<Alela> aVybraneAlely) {
				zmeny.add(jmenaVcetneNevykreslenych(aVybraneAlely));
			}
		};
		final QualAlelaNames nechtene = new QualAlelaNames("arch:stav", "dsbl:stav", "fnd:vztah");
		vyber.refresh(new IkonBag(), nechtene, null);
		Assert.assertEquals(Collections.singletonList(nechtene), zmeny);
	}
}
