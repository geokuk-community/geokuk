package cz.geokuk.plugins.kesoid.kind.munzee;

import javax.swing.Icon;

import cz.geokuk.img.ImageLoader;
import cz.geokuk.plugins.kesoid.Kesoid;
import cz.geokuk.plugins.kesoid.Wpt;
import cz.geokuk.plugins.kesoid.data.EKesoidKind;
import cz.geokuk.plugins.kesoid.genetika.Genotyp;
import cz.geokuk.util.lang.FString;

public class Munzee extends Kesoid {

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.kes.Kesoid#buildGenotyp(cz.geokuk.mapicon.Genom, cz.geokuk.mapicon.Genotyp)
	 */
	@Override
	public Genotyp buildGenotyp(final Genotyp g) {
		final GenotypBuilderMunzee genotypBuilder = new GenotypBuilderMunzee(g.getGenom());
		return genotypBuilder.build(this, g);
	}

	@Override
	public EKesoidKind getKesoidKind() {
		return EKesoidKind.MUNZEE;
	}

	@Override
	public Icon getUrlIcon() {
		return ImageLoader.seekResIcon("munzee.png");
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.kes.Kesoid#prispejDoTooltipu(java.lang.StringBuilder)
	 */
	@Override
	public void prispejDoTooltipu(final StringBuilder sb, final Wpt wpt) {
		sb.append("<b>");
		sb.append(FString.html(getNazev()));
		sb.append("</b>");
		sb.append("<small>");
		sb.append(" - ");
		sb.append(FString.html(getFirstWpt().getSym()));
		sb.append("  (").append(FString.html(getIdentifier())).append(")");
		sb.append("</small>");
		sb.append("<br>");
		if (wpt != getFirstWpt()) {
			if (!getNazev().contains(wpt.getNazev())) {
				sb.append(wpt.isRucnePridany() ? "+ " : "");
				sb.append("<i>");
				sb.append(FString.html(wpt.getName().substring(0, 2)));
				sb.append(": ");
				sb.append(FString.html(wpt.getNazev()));
				sb.append("</i>");
			}
			// if (! getSym().equals(wpt.getSym())) {
			sb.append("<small>");
			sb.append(" - ");
			sb.append(FString.html(wpt.getSym()));
			sb.append("  (").append(FString.html(wpt.getName())).append(")");
			sb.append("</small>");
		}
	}

}
