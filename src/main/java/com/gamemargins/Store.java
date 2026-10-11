package com.gamemargins;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * What the plugin keeps on disk, in RuneLite's folder for this plugin (Filepath, so the Plugin Hub's rules
 * are met): per account the slots as last known and the purchases of the last day ("account-<hash>.json"),
 * and the queue of events not yet delivered to gamemargins.com ("queue.json"). Written as JSON through
 * the injected Gson; a file that can't be read is treated as absent, never as a reason to stop.
 */
@Slf4j
public class Store
{
	private static final Type EVENTS = new TypeToken<List<OfferEvent>>()
	{
	}.getType();

	/** The persisted form of one account. */
	@Data
	@NoArgsConstructor
	public static class AccountFile
	{
		private String name;
		private SlotState[] slots;
		private List<Purchase> purchases;
	}

	private final Gson gson;
	private final Filepath dir;

	public Store(Gson gson, Filepath dir)
	{
		this.gson = gson;
		this.dir = dir;
	}

	private Filepath accountFile(String account)
	{
		// Account hashes may be negative: the file name keeps the digits only.
		return dir.join("account-" + account.replace("-", "n") + ".json");
	}

	public AccountFile loadAccount(String account)
	{
		return read(accountFile(account), AccountFile.class);
	}

	/** Writes an account's file from JSON the plugin made on the client thread (a snapshot, so no race with the tracker). */
	public void saveAccountJson(String account, String json)
	{
		writeText(accountFile(account), json);
	}

	public List<OfferEvent> loadQueue()
	{
		List<OfferEvent> events = read(dir.join("queue.json"), EVENTS);
		return events == null ? new ArrayList<>() : events;
	}

	public void saveQueue(List<OfferEvent> events)
	{
		write(dir.join("queue.json"), events);
	}

	private <T> T read(Filepath file, Type type)
	{
		try
		{
			if (!file.exists())
			{
				return null;
			}
			try (Reader reader = file.openBufferedReader())
			{
				return gson.fromJson(reader, type);
			}
		}
		catch (IOException | JsonSyntaxException e)
		{
			log.debug("could not read {}", file, e);
			return null;
		}
	}

	private void write(Filepath file, Object value)
	{
		writeText(file, gson.toJson(value));
	}

	private void writeText(Filepath file, String json)
	{
		try
		{
			dir.createDirectories();
			// Written whole to a temporary file first, so a crash mid-write never leaves a half file behind.
			Filepath tmp = dir.join(file.getFileName() + ".tmp");
			try (Writer writer = tmp.openBufferedWriter(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))
			{
				writer.write(json);
			}
			tmp.moveTo(file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException e)
		{
			log.debug("could not write {}", file, e);
		}
	}
}
