package com.gamemargins;

import com.google.gson.JsonObject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.LinkBrowser;

/**
 * Linking the plugin to a gamemargins.com account (an OAuth-style device flow, the site's /link): ask the
 * site for a code, show the short one to the player and open the site's page with it, then poll with the
 * long one until the player has confirmed and the site hands over the token. Nothing of the player's
 * account ever goes through RuneLite: no password, no key to paste.
 */
@Slf4j
public class LinkFlow
{
	public enum State
	{
		IDLE, STARTING, WAITING, FAILED
	}

	private final ApiClient api;
	private final ScheduledExecutorService executor;
	private final Consumer<String> onToken;
	private final Runnable onChange;

	@Getter
	private volatile State state = State.IDLE;
	@Getter
	private volatile String userCode;
	@Getter
	private volatile String verifyUrl;
	@Getter
	private volatile String problem;
	private volatile String deviceCode;
	private volatile long expiresAt;
	private ScheduledFuture<?> poll;

	public LinkFlow(ApiClient api, ScheduledExecutorService executor, Consumer<String> onToken, Runnable onChange)
	{
		this.api = api;
		this.executor = executor;
		this.onToken = onToken;
		this.onChange = onChange;
	}

	/** Starts linking: asks the site for a code and opens the page to confirm it on. */
	public synchronized void start()
	{
		if (state == State.STARTING || state == State.WAITING)
		{
			return;
		}
		state = State.STARTING;
		problem = null;
		onChange.run();
		api.startLink(reply ->
		{
			if (!reply.ok() || reply.getBody() == null || !reply.getBody().has("deviceCode"))
			{
				fail(reply.getFailure() != null ? "Could not reach gamemargins.com" : "gamemargins.com refused (" + reply.getStatus() + ")");
				return;
			}
			JsonObject body = reply.getBody();
			deviceCode = body.get("deviceCode").getAsString();
			userCode = body.get("userCode").getAsString();
			verifyUrl = body.has("verifyUrl") ? body.get("verifyUrl").getAsString() : api.getSite() + "/link?code=" + userCode;
			int interval = body.has("interval") ? body.get("interval").getAsInt() : 5;
			int expiresIn = body.has("expiresIn") ? body.get("expiresIn").getAsInt() : 600;
			expiresAt = System.currentTimeMillis() + expiresIn * 1000L;
			state = State.WAITING;
			onChange.run();
			LinkBrowser.browse(verifyUrl);
			synchronized (this)
			{
				poll = executor.scheduleWithFixedDelay(this::pollOnce, interval, interval, TimeUnit.SECONDS);
			}
		});
	}

	/** Opens the site's page again (the player closed it). */
	public void openPage()
	{
		if (verifyUrl != null)
		{
			LinkBrowser.browse(verifyUrl);
		}
	}

	private void pollOnce()
	{
		if (state != State.WAITING)
		{
			return;
		}
		if (System.currentTimeMillis() > expiresAt)
		{
			fail("The code expired; press Link for a new one");
			return;
		}
		api.pollLink(deviceCode, reply ->
		{
			if (reply.getFailure() != null)
			{
				// The site is unreachable for the moment: keep polling until the code expires.
				return;
			}
			JsonObject body = reply.getBody();
			String status = body != null && body.has("status") ? body.get("status").getAsString() : "";
			if ("ok".equals(status) && body.has("token"))
			{
				stopPolling();
				state = State.IDLE;
				userCode = null;
				onChange.run();
				onToken.accept(body.get("token").getAsString());
			}
			else if ("expired".equals(status) || reply.getStatus() == 404 || reply.getStatus() == 410)
			{
				fail("The code expired; press Link for a new one");
			}
		});
	}

	private void fail(String why)
	{
		stopPolling();
		state = State.FAILED;
		problem = why;
		userCode = null;
		onChange.run();
	}

	public synchronized void cancel()
	{
		stopPolling();
		state = State.IDLE;
		userCode = null;
		problem = null;
	}

	private synchronized void stopPolling()
	{
		if (poll != null)
		{
			poll.cancel(false);
			poll = null;
		}
	}
}
