package cz.geokuk.plugins.kesoid.kind.waymark;

import javax.swing.Icon;

import cz.geokuk.img.ImageLoader;
import cz.geokuk.plugins.kesoid.*;
import cz.geokuk.plugins.kesoid.data.EKesoidKind;
import cz.geokuk.plugins.kesoid.genetika.Genotyp;
import cz.geokuk.util.lang.FString;

public class Waymark extends Kesoid {

	@Override
	public Genotyp buildGenotyp(final Genotyp g) {
		final GenotypBuilderWaymark genotypBuilder = new GenotypBuilderWaymark(g.getGenom());
		return genotypBuilder.build(this, g);
	}

	@Override
	public EKesoidKind getKesoidKind() {
		return EKesoidKind.WAYMARK;
	}

	@Override
	public Icon getUrlIcon() {
		return ImageLoader.seekResIcon("waymarking.png");
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
