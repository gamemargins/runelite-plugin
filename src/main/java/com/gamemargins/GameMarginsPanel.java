package com.gamemargins;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.ProgressBar;

/**
 * The side panel (decision 3B/3C): link status with the code while linking, the account and today's profit
 * (from the site), the eight slots with their progress, the buy limits with their countdowns, and when the
 * site last took a batch. Rebuilt from a snapshot the plugin hands it ({@link #show}); everything here runs
 * on the Swing thread. Every line of text is in Geist, the site's letter ({@link PanelFonts}).
 */
public class GameMarginsPanel extends PluginPanel
{
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
	private static final Color GREEN = new Color(55, 240, 70);
	private static final Color AMBER = new Color(230, 150, 30);
	private static final Color DIM = new Color(143, 143, 143);
	/**
	 * Where text wraps, in CSS pixels: Swing's HTML counts a CSS pixel as 1.3 screen pixels, so this keeps the
	 * text 40 pixels inside the panel (a CSS width of PANEL_WIDTH - 40 ran past the edge and was cut off).
	 */
	private static final int WRAP_WIDTH = Math.round((PANEL_WIDTH - 40) / 1.3f);

	/** What the panel shows, taken off the plugin's state on the client thread. */
	public static class View
	{
		public boolean linked;
		public LinkFlow.State linkState = LinkFlow.State.IDLE;
		public String userCode;
		public String linkProblem;
		public String accountName;
		public boolean worldTracked = true;
		public boolean loggedIn;
		public Long todayProfit;
		public Integer todayTrades;
		public SlotState[] slots = new SlotState[OfferTracker.SLOTS];
		public List<BuyLimits.Window> windows = java.util.Collections.emptyList();
		public java.util.function.IntFunction<String> nameOf = id -> "Item " + id;
		public long lastSyncAt;
		public int queued;
		public String syncProblem;
	}

	private final Runnable onLink;
	private final Runnable onOpenPage;
	private final Runnable onUnlink;
	private final JPanel body = new JPanel();

	public GameMarginsPanel(Runnable onLink, Runnable onOpenPage, Runnable onUnlink)
	{
		// Wrapped in RuneLite's scroll pane (the default), so a long list of slots and limits scrolls.
		super();
		this.onLink = onLink;
		this.onOpenPage = onOpenPage;
		this.onUnlink = onUnlink;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		add(body, BorderLayout.NORTH);
	}

	/** Shows a view; safe to call from any thread. */
	public void show(View view)
	{
		SwingUtilities.invokeLater(() -> rebuild(view));
	}

	private void rebuild(View v)
	{
		body.removeAll();
		NumberFormat n = NumberFormat.getIntegerInstance();

		body.add(label("GameMargins", PanelFonts.TITLE, Color.WHITE));
		body.add(Box.createVerticalStrut(8));

		if (!v.linked)
		{
			body.add(text("Link this plugin to your gamemargins.com account: your Grand Exchange offers then fill your trades and buy-limit timers on the site by themselves.", DIM));
			body.add(Box.createVerticalStrut(8));
			if (v.linkState == LinkFlow.State.WAITING && v.userCode != null)
			{
				body.add(text("On gamemargins.com/link, confirm this code:", Color.WHITE));
				body.add(label(v.userCode, PanelFonts.CODE, Color.WHITE));
				body.add(text("Waiting for you to confirm…", DIM));
				body.add(Box.createVerticalStrut(6));
				body.add(button("Open the page again", onOpenPage));
			}
			else
			{
				if (v.linkState == LinkFlow.State.FAILED && v.linkProblem != null)
				{
					body.add(text(v.linkProblem, AMBER));
					body.add(Box.createVerticalStrut(6));
				}
				body.add(button(v.linkState == LinkFlow.State.STARTING ? "Asking for a code…" : "Link", onLink));
			}
			body.add(Box.createVerticalStrut(8));
			body.add(text("Nothing is sent before you link. What is sent then: your offers (item, quantity, price, what filled and when), your account's number and name.", DIM));
			body.revalidate();
			body.repaint();
			return;
		}

		if (!v.loggedIn)
		{
			body.add(text("Log in to see your slots.", DIM));
		}
		else if (!v.worldTracked)
		{
			body.add(text("This world has its own economy (Leagues, Deadman, beta…): offers here are not tracked.", AMBER));
		}
		else
		{
			JPanel head = new JPanel(new BorderLayout(6, 0));
			head.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			head.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
			head.setAlignmentX(LEFT_ALIGNMENT);
			head.add(label(v.accountName == null ? "Account" : v.accountName, PanelFonts.BODY, Color.WHITE), BorderLayout.CENTER);
			if (v.todayProfit != null)
			{
				long profit = v.todayProfit;
				JLabel today = label((profit > 0 ? "+" : "") + n.format(profit) + " today", PanelFonts.BODY, profit > 0 ? GREEN : profit < 0 ? Color.RED : DIM);
				today.setToolTipText(v.todayTrades == null ? null : v.todayTrades + " trades sold today, profit after tax");
				head.add(today, BorderLayout.EAST);
			}
			body.add(head);
			body.add(Box.createVerticalStrut(8));

			body.add(section("Offers"));
			int empty = 0;
			for (int i = 0; i < v.slots.length; i++)
			{
				SlotState s = v.slots[i];
				if (s == null || s.isEmpty())
				{
					empty++;
					continue;
				}
				body.add(slotRow(s, v.nameOf.apply(s.getItemId()), n));
				body.add(Box.createVerticalStrut(4));
			}
			if (empty > 0)
			{
				body.add(text(empty + (empty == 1 ? " empty slot" : " empty slots"), DIM));
			}
			body.add(Box.createVerticalStrut(8));

			body.add(section("Buy limits"));
			if (v.windows.isEmpty())
			{
				body.add(text("Nothing bought in the last 4 hours", DIM));
			}
			long now = System.currentTimeMillis();
			for (BuyLimits.Window w : v.windows)
			{
				JPanel row = new JPanel(new BorderLayout(6, 0));
				row.setBackground(ColorScheme.DARK_GRAY_COLOR);
				row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR));
				row.setAlignmentX(LEFT_ALIGNMENT);
				row.add(label(v.nameOf.apply(w.getItemId()), PanelFonts.BODY, Color.WHITE), BorderLayout.CENTER);
				String left = w.getLeft() < 0 ? n.format(w.getUsed()) + " bought" : w.getLeft() == 0 ? "full" : n.format(w.getLeft()) + " left";
				JLabel when = label(left + " · " + CLOCK.format(Instant.ofEpochMilli(w.getResets())), PanelFonts.BODY, w.getLeft() == 0 ? AMBER : DIM);
				when.setToolTipText("Frees up in " + BuyLimits.until(w.getResets() - now));
				row.add(when, BorderLayout.EAST);
				body.add(row);
			}
		}

		body.add(Box.createVerticalStrut(10));
		String sync;
		if (v.syncProblem != null)
		{
			sync = v.syncProblem;
		}
		else if (v.lastSyncAt == 0)
		{
			// Nothing sent since RuneLite started, and nothing waiting: the site has all there is. ("Nothing sent
			// yet" read as if the link didn't work, while today's profit above had just come from the site.)
			sync = v.queued > 0 ? v.queued + " to send" : "Up to date";
		}
		else
		{
			long ago = Math.max(0, now() - v.lastSyncAt);
			sync = "Synced " + (ago < 60_000 ? (ago / 1000) + " s ago" : BuyLimits.until(ago) + " ago") + (v.queued > 0 ? " · " + v.queued + " to send" : "");
		}
		body.add(text(sync, v.syncProblem != null ? AMBER : DIM));
		body.add(Box.createVerticalStrut(6));
		body.add(button("Unlink", onUnlink));
		body.revalidate();
		body.repaint();
	}

	private static long now()
	{
		return System.currentTimeMillis();
	}

	private JPanel slotRow(SlotState s, String name, NumberFormat n)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
		row.setAlignmentX(LEFT_ALIGNMENT);
		// Everything in the row lines up on the left: BoxLayout puts a left-aligned label (the price) at the
		// middle of panels that are centred by default.
		JPanel line = new JPanel(new BorderLayout(6, 0));
		line.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		line.setAlignmentX(LEFT_ALIGNMENT);
		// The name takes what the numbers leave, and is shortened with "…" when it doesn't fit.
		line.add(label(name, PanelFonts.BODY, Color.WHITE), BorderLayout.CENTER);
		String state = s.isCancelled() ? "Cancelled" : s.isFinished() ? "Done" : s.isBuy() ? "Buy" : "Sell";
		Color stateColor = s.isCancelled() ? Color.RED : s.isFinished() ? GREEN : DIM;
		line.add(label(state + " " + n.format(s.getFilled()) + " / " + n.format(s.getQuantity()), PanelFonts.BODY, stateColor), BorderLayout.EAST);
		row.add(line);
		ProgressBar bar = new ProgressBar();
		bar.setMaximumValue(Math.max(1, s.getQuantity()));
		bar.setValue(s.getFilled());
		bar.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		bar.setForeground(s.isFinished() && !s.isCancelled() ? GREEN : AMBER);
		bar.setPreferredSize(new Dimension(PANEL_WIDTH - 40, 6));
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
		bar.setAlignmentX(LEFT_ALIGNMENT);
		row.add(Box.createVerticalStrut(3));
		row.add(bar);
		row.add(label(n.format(s.getPrice()) + " each", PanelFonts.SMALL, DIM));
		return row;
	}

	/** A line of text in one of the panel's letters ({@link PanelFonts}), lined up on the left. */
	private static JLabel label(String s, Font font, Color color)
	{
		JLabel label = PanelFonts.use(new JLabel(s), font);
		label.setForeground(color);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JLabel section(String name)
	{
		return label(name.toUpperCase(), PanelFonts.SECTION, ColorScheme.BRAND_ORANGE);
	}

	private static JLabel text(String s, Color color)
	{
		return label("<html><div style='width:" + WRAP_WIDTH + "px'>" + escape(s) + "</div></html>", PanelFonts.SMALL, color);
	}

	private static JButton button(String label, Runnable action)
	{
		JButton b = PanelFonts.use(new JButton(label), PanelFonts.BUTTON);
		b.setAlignmentX(LEFT_ALIGNMENT);
		b.addActionListener(e -> action.run());
		return b;
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
