package com.gamemargins;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * gamemargins.com's RuneLite API, through RuneLite's OkHttp client, always asynchronous (enqueue): nothing
 * here runs on the client thread, and callbacks come back on OkHttp's pool (callers hop to the client thread
 * or Swing themselves). The site's routes: POST /api/runelite/link (start linking), POST /api/runelite/link/poll,
 * POST /api/runelite/offers (a batch of events, with the token), GET /api/runelite/status, GET /api/runelite/limits.
 */
@Slf4j
public class ApiClient
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	/** What a request came back with: the HTTP status and the JSON body (null when it wasn't JSON). */
	@Data
	public static class Reply
	{
		private final int status;
		private final JsonObject body;
		/** Set when the request never reached the server. */
		private final IOException failure;

		public boolean ok()
		{
			return failure == null && status >= 200 && status < 300;
		}
	}

	/** A batch of events as the site takes it. */
	@Data
	public static class Batch
	{
		private final int tzOffset;
		private final List<OfferEvent> events;
	}

	private final OkHttpClient http;
	private final Gson gson;
	private final String site;

	public ApiClient(OkHttpClient http, Gson gson, String site)
	{
		this.http = http;
		this.gson = gson;
		this.site = site.endsWith("/") ? site.substring(0, site.length() - 1) : site;
	}

	public String getSite()
	{
		return site;
	}

	private HttpUrl url(String path)
	{
		HttpUrl base = HttpUrl.parse(site + path);
		if (base == null)
		{
			throw new IllegalArgumentException("not a URL: " + site + path);
		}
		return base;
	}

	public void startLink(Consumer<Reply> done)
	{
		send(new Request.Builder().url(url("/api/runelite/link")).post(RequestBody.create(JSON, "{}")).build(), done);
	}

	public void pollLink(String deviceCode, Consumer<Reply> done)
	{
		JsonObject body = new JsonObject();
		body.addProperty("deviceCode", deviceCode);
		send(new Request.Builder().url(url("/api/runelite/link/poll")).post(RequestBody.create(JSON, gson.toJson(body))).build(), done);
	}

	public void sendOffers(String token, int tzOffset, List<OfferEvent> events, Consumer<Reply> done)
	{
		String json = gson.toJson(new Batch(tzOffset, events));
		send(new Request.Builder().url(url("/api/runelite/offers")).header("Authorization", "Bearer " + token).post(RequestBody.create(JSON, json)).build(), done);
	}

	public void status(String token, Consumer<Reply> done)
	{
		send(new Request.Builder().url(url("/api/runelite/status")).header("Authorization", "Bearer " + token).get().build(), done);
	}

	public void limits(Consumer<Reply> done)
	{
		send(new Request.Builder().url(url("/api/runelite/limits")).get().build(), done);
	}

	/** The buy limits in a /api/runelite/limits reply: item id → limit. */
	public Map<Integer, Integer> readLimits(JsonObject body)
	{
		Map<Integer, Integer> out = new java.util.HashMap<>();
		if (body == null || !body.has("limits"))
		{
			return out;
		}
		for (Map.Entry<String, com.google.gson.JsonElement> e : body.getAsJsonObject("limits").entrySet())
		{
			try
			{
				out.put(Integer.parseInt(e.getKey()), e.getValue().getAsInt());
			}
			catch (NumberFormatException | IllegalStateException ignored)
			{
				// Not an item id or not a number: left out.
			}
		}
		return out;
	}

	private void send(Request request, Consumer<Reply> done)
	{
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				done.accept(new Reply(0, null, e));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					JsonObject body = null;
					String text = r.body() == null ? "" : r.body().string();
					if (!text.isEmpty())
					{
						try
						{
							body = gson.fromJson(text, JsonObject.class);
						}
						catch (JsonSyntaxException e)
						{
							log.debug("not JSON from {}: {}", request.url(), text.length() > 200 ? text.substring(0, 200) : text);
						}
					}
					done.accept(new Reply(r.code(), body, null));
				}
				catch (IOException e)
				{
					done.accept(new Reply(0, null, e));
				}
			}
		});
	}
}
