package com.gamemargins;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import okhttp3.OkHttpClient;

/**
 * GameMargins: Grand Exchange buy-limit timers and flip tracking, synced with gamemargins.com.
 *
 * <p>The plugin watches the eight Grand Exchange slots (GrandExchangeOfferChanged) and turns every change
 * into an event for the site (OfferTracker), queued on disk (EventQueue, Store) and sent in batches with a
 * bearer token the site handed out when the player linked the plugin (LinkFlow: a code shown here,
 * confirmed on gamemargins.com/link). Nothing is sent before that. The side panel (GameMarginsPanel) shows
 * the slots, the buy-limit windows (BuyLimits, from this plugin's own purchases) and today's profit from the
 * site; the overlay (GeOverlay) shows the limit left in the offer setup screen; the Notifier says when an
 * offer finishes or a limit frees up.
 *
 * <p>Threads: game events and the trackers live on the client thread; disk and network run on RuneLite's
 * executor and OkHttp's pool; the panel is rebuilt on Swing from a snapshot.
 */
@Slf4j
@PluginDescriptor(
	name = "GameMargins",
	description = "Grand Exchange buy-limit timers and flip tracking, synced with gamemargins.com",
	tags = {"flipping", "grand exchange", "ge", "buy limit", "timer", "merching", "profit"},
	internalName = "gamemargins"
)
public class GameMarginsPlugin extends Plugin
{
	/** Game ticks without an offer event after login before the slots are taken as settled. */
	private static final int REPLAY_QUIET_TICKS = 3;
	/** Ticks to wait for the account's file at most before settling without it. */
	private static final int REPLAY_MAX_TICKS = 50;
	private static final long FLUSH_EVERY_MS = 2_000;
	private static final long LIMITS_EVERY_HOURS = 24;
	private static final long STATUS_EVERY_MINUTES = 5;

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private OkHttpClient http;
	@Inject
	private Gson gson;
	@Inject
	private ConfigManager configManager;
	@Inject
	private GameMarginsConfig config;
	@Inject
	private Notifier notifier;
	@Inject
	private ClientToolbar toolbar;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private ItemManager itemManager;
	@Inject
	private ScheduledExecutorService executor;
	@Inject
	private GeOverlay overlay;

	private Store store;
	private ApiClient api;
	private LinkFlow link;
	private GameMarginsPanel panel;
	private NavigationButton navButton;
	private final EventQueue queue = new EventQueue();
	private final Map<String, OfferTracker> trackers = new HashMap<>();
	private final Map<Integer, String> itemNames = new HashMap<>();
	private final Map<String, Long> todayProfit = new HashMap<>();
	private final Map<String, Integer> todayTrades = new HashMap<>();
	private final Set<String> announcedResets = new HashSet<>();
	private volatile Map<Integer, Integer> limits = new HashMap<>();
	private OfferTracker current;
	private boolean worldTracked = true;
	private int quietTicks = -1;
	private int tick;
	private volatile long lastSyncAt;
	private volatile String syncProblem;
	private ScheduledFuture<?> flusher;
	private ScheduledFuture<?> limitsRefresh;
	private ScheduledFuture<?> statusRefresh;

	@Override
	protected void startUp() throws Exception
	{
		store = new Store(gson, getPluginDirectory());
		api = new ApiClient(http, gson, config.siteUrl());
		link = new LinkFlow(api, executor, this::linked, this::refreshPanel);
		panel = new GameMarginsPanel(() -> link.start(), () -> link.openPage(), this::unlink);
		navButton = NavigationButton.builder().tooltip("GameMargins").icon(icon()).priority(6).panel(panel).build();
		toolbar.addNavigation(navButton);
		overlayManager.add(overlay);
		// The queue from the last session, read off the executor so the client thread never waits on disk.
		executor.execute(() ->
		{
			List<OfferEvent> saved = store.loadQueue();
			if (!saved.isEmpty())
			{
				queue.restore(saved);
			}
			refreshPanel();
		});
		flusher = executor.scheduleWithFixedDelay(this::flush, FLUSH_EVERY_MS, FLUSH_EVERY_MS, TimeUnit.MILLISECONDS);
		if (isLinked())
		{
			limitsRefresh = executor.scheduleWithFixedDelay(this::fetchLimits, 0, LIMITS_EVERY_HOURS, TimeUnit.HOURS);
			statusRefresh = executor.scheduleWithFixedDelay(this::fetchStatus, 1, STATUS_EVERY_MINUTES * 60, TimeUnit.SECONDS);
		}
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::loggedIn);
		}
		refreshPanel();
		log.debug("GameMargins started");
	}

	@Override
	protected void shutDown()
	{
		if (flusher != null)
		{
			flusher.cancel(false);
		}
		if (limitsRefresh != null)
		{
			limitsRefresh.cancel(false);
		}
		if (statusRefresh != null)
		{
			statusRefresh.cancel(false);
		}
		link.cancel();
		toolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);
		saveCurrent();
		trackers.clear();
		current = null;
		log.debug("GameMargins stopped");
	}

	@Provides
	GameMarginsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GameMarginsConfig.class);
	}

	// --- game events (client thread) ---------------------------------------------------------------

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				loggedIn();
				break;
			case LOGIN_SCREEN:
			case HOPPING:
			case CONNECTION_LOST:
				saveCurrent();
				if (event.getGameState() != GameState.HOPPING)
				{
					current = null;
				}
				refreshPanel();
				break;
			default:
				break;
		}
	}

	/** A login (or the plugin turned on while logged in): find the account's tracker and wait out the slot replay. */
	private void loggedIn()
	{
		long hash = client.getAccountHash();
		if (hash == -1)
		{
			return;
		}
		worldTracked = WorldFilter.tracked(client.getWorldType());
		String account = Long.toString(hash);
		current = trackers.computeIfAbsent(account, this::loadTracker);
		current.beginReplay();
		quietTicks = 0;
		refreshPanel();
	}

	private OfferTracker loadTracker(String account)
	{
		OfferTracker tracker = new OfferTracker(account, System::currentTimeMillis);
		// The account's file is read off the client thread; the login reconciliation waits until it is in.
		executor.execute(() ->
		{
			Store.AccountFile saved = store.loadAccount(account);
			clientThread.invoke(() ->
			{
				tracker.restore(saved == null ? null : saved.getSlots(), saved == null ? null : saved.getPurchases());
				if (saved != null && saved.getName() != null && tracker.getName() == null)
				{
					tracker.setName(saved.getName());
				}
			});
		});
		return tracker;
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		if (current == null)
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loggedIn();
			}
			if (current == null)
			{
				return;
			}
		}
		if (current.isReplaying())
		{
			// The login burst: the slots settle over a few ticks, the reconciliation reads them then.
			quietTicks = 0;
			return;
		}
		if (!worldTracked)
		{
			return;
		}
		noteName();
		List<OfferEvent> events = current.onChange(event.getSlot(), event.getOffer());
		if (events.isEmpty())
		{
			return;
		}
		GrandExchangeOffer offer = event.getOffer();
		if (offer.getState() == GrandExchangeOfferState.BOUGHT || offer.getState() == GrandExchangeOfferState.SOLD)
		{
			String what = offer.getState() == GrandExchangeOfferState.BOUGHT ? "Bought" : "Sold";
			notifier.notify(config.notifyOfferDone(), "GameMargins: " + what + " " + NumberFormat.getIntegerInstance().format(offer.getTotalQuantity()) + " " + nameOf(offer.getItemId()));
		}
		enqueue(events);
		saveCurrent();
		refreshPanel();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tick++;
		if (current != null && current.isReplaying())
		{
			quietTicks++;
			if (quietTicks >= REPLAY_QUIET_TICKS && (current.isRestored() || quietTicks >= REPLAY_MAX_TICKS))
			{
				settleReplay();
			}
			return;
		}
		if (current != null && tick % 5 == 0)
		{
			checkResets();
		}
		if (tick % 10 == 0)
		{
			refreshPanel();
		}
	}

	/** The client has settled after login: what changed while the plugin wasn't watching goes out, marked offline. */
	private void settleReplay()
	{
		noteName();
		List<OfferEvent> events = worldTracked ? current.endReplay(client.getGrandExchangeOffers()) : new ArrayList<>();
		if (!worldTracked)
		{
			current.endReplay(null);
		}
		if (!events.isEmpty())
		{
			enqueue(events);
			saveCurrent();
		}
		refreshPanel();
	}

	private void noteName()
	{
		Player me = client.getLocalPlayer();
		if (me != null && me.getName() != null && current != null)
		{
			current.setName(me.getName());
		}
	}

	/** A window that just ended: the whole limit is free again. Once per window. */
	private void checkResets()
	{
		long now = System.currentTimeMillis();
		List<Purchase> purchases = current.getPurchases();
		Set<Integer> items = new HashSet<>();
		for (Purchase p : purchases)
		{
			items.add(p.getItemId());
		}
		for (int itemId : items)
		{
			BuyLimits.Window open = BuyLimits.window(itemId, purchases, now, limitOf(itemId) == null ? -1 : limitOf(itemId));
			if (open != null)
			{
				continue;
			}
			// The last window ended: find when, and announce it if that was in the last minute.
			long lastAt = 0;
			for (Purchase p : purchases)
			{
				if (p.getItemId() == itemId)
				{
					lastAt = Math.max(lastAt, p.getAt());
				}
			}
			BuyLimits.Window last = BuyLimits.window(itemId, purchases, lastAt, -1);
			if (last == null || now - last.getResets() > 60_000)
			{
				continue;
			}
			String key = current.getAccount() + ":" + itemId + ":" + last.getResets();
			if (announcedResets.add(key))
			{
				Integer limit = limitOf(itemId);
				String again = limit == null ? "" : " You can buy " + NumberFormat.getIntegerInstance().format(limit) + " again.";
				notifier.notify(config.notifyLimitReset(), "GameMargins: buy limit reset for " + nameOf(itemId) + "." + again);
			}
		}
	}

	// --- the site -------------------------------------------------------------------------------------

	private boolean isLinked()
	{
		String token = config.token();
		return token != null && !token.isEmpty();
	}

	/** The device flow finished: keep the token, fetch the limits and the status, and start sending. */
	private void linked(String token)
	{
		configManager.setConfiguration(GameMarginsConfig.GROUP, GameMarginsConfig.TOKEN_KEY, token);
		syncProblem = null;
		if (limitsRefresh == null)
		{
			limitsRefresh = executor.scheduleWithFixedDelay(this::fetchLimits, 0, LIMITS_EVERY_HOURS, TimeUnit.HOURS);
		}
		if (statusRefresh == null)
		{
			statusRefresh = executor.scheduleWithFixedDelay(this::fetchStatus, 0, STATUS_EVERY_MINUTES * 60, TimeUnit.SECONDS);
		}
		refreshPanel();
	}

	/** The player unlinks here (or the site revoked the token): forget it, and the events waiting for it. */
	private void unlink()
	{
		configManager.unsetConfiguration(GameMarginsConfig.GROUP, GameMarginsConfig.TOKEN_KEY);
		link.cancel();
		queue.clear();
		executor.execute(() -> store.saveQueue(new ArrayList<>()));
		todayProfit.clear();
		todayTrades.clear();
		syncProblem = null;
		refreshPanel();
	}

	private void enqueue(List<OfferEvent> events)
	{
		queue.addAll(events);
		List<OfferEvent> snapshot = queue.snapshot();
		executor.execute(() -> store.saveQueue(snapshot));
	}

	/** Sends the next batch when one is due (every FLUSH_EVERY_MS on the executor). */
	private void flush()
	{
		if (!isLinked())
		{
			return;
		}
		long now = System.currentTimeMillis();
		List<OfferEvent> batch = queue.take(now);
		if (batch == null)
		{
			return;
		}
		int tzOffset = TimeZone.getDefault().getOffset(now) / 60_000;
		api.sendOffers(config.token(), tzOffset, batch, reply ->
		{
			if (reply.ok())
			{
				queue.sent(batch);
				lastSyncAt = System.currentTimeMillis();
				syncProblem = null;
				readStatus(accountsIn(reply.getBody()));
				List<OfferEvent> snapshot = queue.snapshot();
				store.saveQueue(snapshot);
			}
			else if (reply.getStatus() == 401)
			{
				// The site no longer knows this token (unlinked on the account page): stop sending until linked again.
				syncProblem = "Not linked any more: press Link";
				queue.failed(System.currentTimeMillis());
				configManager.unsetConfiguration(GameMarginsConfig.GROUP, GameMarginsConfig.TOKEN_KEY);
			}
			else if (reply.getStatus() == 400)
			{
				// Refused as malformed: it would never go through, and holds the rest up.
				log.debug("batch refused: {}", reply.getBody());
				queue.dropped(batch);
			}
			else
			{
				syncProblem = reply.getFailure() != null ? "gamemargins.com unreachable, retrying" : "gamemargins.com answered " + reply.getStatus() + ", retrying";
				queue.failed(System.currentTimeMillis());
			}
			refreshPanel();
		});
	}

	private void fetchLimits()
	{
		api.limits(reply ->
		{
			if (reply.ok() && reply.getBody() != null)
			{
				limits = api.readLimits(reply.getBody());
			}
		});
	}

	private void fetchStatus()
	{
		if (!isLinked())
		{
			return;
		}
		api.status(config.token(), reply ->
		{
			if (reply.ok() && reply.getBody() != null)
			{
				readStatus(accountsIn(reply.getBody()));
				refreshPanel();
			}
			else if (reply.getStatus() == 401)
			{
				syncProblem = "Not linked any more: press Link";
				configManager.unsetConfiguration(GameMarginsConfig.GROUP, GameMarginsConfig.TOKEN_KEY);
				refreshPanel();
			}
		});
	}

	private static JsonArray accountsIn(JsonObject body)
	{
		return body != null && body.has("accounts") && body.get("accounts").isJsonArray() ? body.getAsJsonArray("accounts") : null;
	}

	/** Today's profit per account, as the site works it out (the site pairs the trades; the plugin only sees offers). */
	private void readStatus(JsonArray accounts)
	{
		if (accounts == null)
		{
			return;
		}
		Map<String, Long> profit = new HashMap<>();
		Map<String, Integer> trades = new HashMap<>();
		for (JsonElement e : accounts)
		{
			if (!e.isJsonObject())
			{
				continue;
			}
			JsonObject a = e.getAsJsonObject();
			if (!a.has("hash") || !a.has("today"))
			{
				continue;
			}
			JsonObject today = a.getAsJsonObject("today");
			profit.put(a.get("hash").getAsString(), today.get("profit").getAsLong());
			trades.put(a.get("hash").getAsString(), today.get("trades").getAsInt());
		}
		synchronized (todayProfit)
		{
			todayProfit.putAll(profit);
			todayTrades.putAll(trades);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!GameMarginsConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("siteUrl".equals(event.getKey()))
		{
			link.cancel();
			api = new ApiClient(http, gson, config.siteUrl());
			link = new LinkFlow(api, executor, this::linked, this::refreshPanel);
		}
		refreshPanel();
	}

	// --- what the overlay and the panel read -------------------------------------------------------

	public boolean isOverlayOn()
	{
		return config.overlay() && current != null && worldTracked;
	}

	/** The item's Grand Exchange buy limit per 4 hours, from the site's mapping; null when unknown. */
	public Integer limitOf(int itemId)
	{
		return limits.get(itemId);
	}

	/** The open buy-limit window of an item for the logged-in account, from this plugin's purchases; null when the limit is free. */
	public BuyLimits.Window windowFor(int itemId)
	{
		if (current == null)
		{
			return null;
		}
		Integer limit = limitOf(itemId);
		return BuyLimits.window(itemId, current.getPurchases(), System.currentTimeMillis(), limit == null ? -1 : limit);
	}

	private String nameOf(int itemId)
	{
		String name = itemNames.get(itemId);
		if (name == null)
		{
			// Only on the client thread; the panel's view is built there too.
			name = itemManager.getItemComposition(itemId).getName();
			itemNames.put(itemId, name);
		}
		return name;
	}

	private void saveCurrent()
	{
		if (current == null)
		{
			return;
		}
		// Serialised here, on the client thread, so the executor writes a snapshot and never races the tracker.
		OfferTracker tracker = current;
		Store.AccountFile file = new Store.AccountFile();
		file.setName(tracker.getName());
		file.setSlots(tracker.getSlots());
		file.setPurchases(new ArrayList<>(tracker.getPurchases()));
		String json = gson.toJson(file);
		String account = tracker.getAccount();
		executor.execute(() -> store.saveAccountJson(account, json));
	}

	/** Rebuilds the panel from the plugin's state; called from any thread, built on the client thread. */
	private void refreshPanel()
	{
		if (panel == null)
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			GameMarginsPanel.View v = new GameMarginsPanel.View();
			v.linked = isLinked();
			v.linkState = link.getState();
			v.userCode = link.getUserCode();
			v.linkProblem = link.getProblem();
			v.loggedIn = current != null && client.getGameState() == GameState.LOGGED_IN;
			v.worldTracked = worldTracked;
			v.lastSyncAt = lastSyncAt;
			v.queued = queue.size();
			v.syncProblem = syncProblem;
			if (current != null)
			{
				v.accountName = current.getName();
				v.slots = current.getSlots().clone();
				long now = System.currentTimeMillis();
				v.windows = BuyLimits.windows(current.getPurchases(), now, id -> limitOf(id));
				synchronized (todayProfit)
				{
					v.todayProfit = todayProfit.get(current.getAccount());
					v.todayTrades = todayTrades.get(current.getAccount());
				}
				Map<Integer, String> names = new HashMap<>();
				for (SlotState s : v.slots)
				{
					if (s != null && !s.isEmpty())
					{
						names.put(s.getItemId(), nameOf(s.getItemId()));
					}
				}
				for (BuyLimits.Window w : v.windows)
				{
					names.put(w.getItemId(), nameOf(w.getItemId()));
				}
				v.nameOf = id -> names.getOrDefault(id, "Item " + id);
			}
			panel.show(v);
		});
	}

	/** The toolbar icon: the brand's gold tile with a G, drawn here rather than shipped as a file. */
	private static BufferedImage icon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(242, 181, 68));
		g.fillRoundRect(0, 0, 16, 16, 5, 5);
		g.setColor(new Color(27, 20, 6));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		g.drawString("G", 4, 12);
		g.dispose();
		return image;
	}
}
