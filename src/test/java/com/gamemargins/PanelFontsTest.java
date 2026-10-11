package com.gamemargins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PanelFontsTest
{
	@Test
	public void theFourGeistFilesLoadFromThePlugin()
	{
		assertTrue("every Geist file loads; otherwise the panel falls back to RuneLite's letters", PanelFonts.GEIST);
		assertTrue(PanelFonts.BODY.getFamily(), PanelFonts.BODY.getFamily().startsWith("Geist"));
		assertTrue(PanelFonts.TITLE.getFamily(), PanelFonts.TITLE.getFamily().startsWith("Geist"));
		assertTrue(PanelFonts.CODE.getFamily(), PanelFonts.CODE.getFamily().startsWith("Geist Mono"));
		assertEquals(13f, PanelFonts.BODY.getSize2D(), 0f);
	}
}
