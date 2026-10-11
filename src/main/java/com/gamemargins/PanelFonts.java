package com.gamemargins;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import javax.swing.JComponent;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.FontManager;

/**
 * The side panel's letters (the owner's choice, 12 October 2026): Geist, the letter of gamemargins.com, bundled in
 * {@code fonts/} under the SIL Open Font License ({@code fonts/OFL.txt}). RuneScape's pixel letters only come out even
 * at 100% and 200% display scaling; Geist stays sharp at any. Should a file fail to load, the whole panel keeps
 * RuneLite's own letters instead, so it never mixes the two.
 */
@Slf4j
final class PanelFonts
{
	/** Whether Geist loaded; when not, every role below is one of RuneLite's letters. */
	static final boolean GEIST;
	/** Names and values: the account, the slots, the buy limits. */
	static final Font BODY;
	/** Sentences, prices and status lines. */
	static final Font SMALL;
	/** The small capitals above a list ("OFFERS"). */
	static final Font SECTION;
	static final Font TITLE;
	static final Font BUTTON;
	/** The code to confirm on the site while linking. */
	static final Font CODE;

	static
	{
		Font regular = load("Geist-Regular.ttf");
		Font medium = load("Geist-Medium.ttf");
		Font semiBold = load("Geist-SemiBold.ttf");
		Font mono = load("GeistMono-Medium.ttf");
		GEIST = regular != null && medium != null && semiBold != null && mono != null;
		if (GEIST)
		{
			BODY = regular.deriveFont(13f);
			SMALL = regular.deriveFont(12f);
			SECTION = semiBold.deriveFont(11f).deriveFont(Collections.singletonMap(TextAttribute.TRACKING, 0.07f));
			TITLE = semiBold.deriveFont(16f);
			BUTTON = medium.deriveFont(13f);
			CODE = mono.deriveFont(24f);
		}
		else
		{
			BODY = FontManager.getRunescapeFont();
			SMALL = FontManager.getRunescapeSmallFont();
			SECTION = FontManager.getRunescapeSmallFont();
			TITLE = FontManager.getRunescapeBoldFont();
			BUTTON = FontManager.getRunescapeFont();
			CODE = FontManager.getRunescapeBoldFont().deriveFont(32f);
		}
	}

	private PanelFonts()
	{
	}

	/**
	 * Gives a component its letter. Geist is drawn smoothed; RuneLite's look and feel leaves smoothing to each font's
	 * own table, which keeps its pixel letters sharp.
	 */
	static <T extends JComponent> T use(T component, Font font)
	{
		component.setFont(font);
		if (GEIST)
		{
			component.putClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		}
		return component;
	}

	private static Font load(String file)
	{
		try (InputStream in = PanelFonts.class.getResourceAsStream("fonts/" + file))
		{
			if (in == null)
			{
				log.warn("Font {} is missing from the plugin", file);
				return null;
			}
			return Font.createFont(Font.TRUETYPE_FONT, in);
		}
		catch (IOException | FontFormatException e)
		{
			log.warn("Could not load font {}", file, e);
			return null;
		}
	}
}
