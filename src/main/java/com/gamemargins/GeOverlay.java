package com.gamemargins;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The buy limit in the Grand Exchange's offer setup screen (decision 3C): while a buy offer is being set up,
 * a small panel under the offer says how much of the item's limit is left and when it resets, from this
 * plugin's own purchases (BuyLimits) and the limits the site gave it. Drawn only on that screen, only for buys.
 */
public class GeOverlay extends Overlay
{
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
	private static final Color ORANGE = new Color(220, 138, 0);
	private static final Color GREEN = new Color(55, 240, 70);

	private final Client client;
	private final GameMarginsPlugin plugin;
	private final PanelComponent panel = new PanelComponent();

	@Inject
	GeOverlay(Client client, GameMarginsPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isOverlayOn())
		{
			return null;
		}
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isHidden())
		{
			return null;
		}
		// 0 is a buy offer, 1 a sell; the item picked for it is in the search varp.
		if (client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) != 0)
		{
			return null;
		}
		int itemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
		if (itemId <= 0)
		{
			return null;
		}
		BuyLimits.Window window = plugin.windowFor(itemId);
		Integer limit = plugin.limitOf(itemId);
		panel.getChildren().clear();
		panel.getChildren().add(TitleComponent.builder().text("GameMargins").color(ORANGE).build());
		NumberFormat n = NumberFormat.getIntegerInstance();
		if (window == null)
		{
			panel.getChildren().add(LineComponent.builder()
				.left("Buy limit")
				.right(limit == null ? "unknown" : n.format(limit) + " free")
				.rightColor(GREEN)
				.build());
			panel.getChildren().add(LineComponent.builder().left("Nothing bought in the last 4 hours").build());
		}
		else
		{
			String left = window.getLeft() < 0 ? n.format(window.getUsed()) + " bought" : n.format(window.getLeft()) + " of " + n.format(window.getLimit()) + " left";
			panel.getChildren().add(LineComponent.builder()
				.left("Buy limit")
				.right(left)
				.rightColor(window.getLeft() == 0 ? Color.RED : Color.WHITE)
				.build());
			long remaining = window.getResets() - System.currentTimeMillis();
			panel.getChildren().add(LineComponent.builder()
				.left("Resets at " + CLOCK.format(Instant.ofEpochMilli(window.getResets())))
				.right("in " + BuyLimits.until(remaining))
				.build());
		}
		// Under the setup window, at its left edge.
		Rectangle bounds = setup.getBounds();
		if (bounds != null)
		{
			panel.setPreferredLocation(new Point(bounds.x, bounds.y + bounds.height + 4));
		}
		return panel.render(graphics);
	}
}
