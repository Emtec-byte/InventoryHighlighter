package com.inventoryhighlighter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Alpha;
import java.awt.Color;

@ConfigGroup(InventoryHighlighterConfig.GROUP)
// RuneLite stores user edits by config key; these defaults are used only until a key is changed or reset.
public interface InventoryHighlighterConfig extends Config
{
    String GROUP = "inventoryhighlighter";
    String DEFAULT_ITEM_LIST = "Saradomin brew*, *moonlight antelope, Marlin, Manta ray, Anglerfish, Shark, *karambw*, Prayer potion*, Super restore*, *combat potion*, *Ranging potion*";
    String DEFAULT_NOTEPAD_TEXT = "Use this as a quick copy/paste text storage area for item lists or notes. It does not affect highlighting.";
    String HELP_TEXT = "Enter item names in Item List, separated by commas. Highlight mode decides whether the list is a whitelist (highlight only these items) or a blacklist (highlight everything except these items). Plain names match exact items only, so Shark matches Shark but not Raw shark. Use * as a wildcard: Sha* matches names starting with Sha, *ar matches names ending in ar, and *ar* matches names containing ar.";

    @ConfigSection(
        name = "How to use",
        description = "Instructions and wildcard examples",
        position = 100,
        closedByDefault = true
    )
    String helpSection = "help";

    // The item list is shared by both modes; Highlight mode decides whether it acts as a whitelist or a blacklist.
    // Highlighting is intentionally hover-only; drawing every matching item was only useful during performance tuning.

    @ConfigItem(
        keyName = "itemList",
        name = "Item List",
        description = "Comma-separated item names. Shared by both modes: highlighted in Whitelist mode, ignored in Blacklist mode.",
        position = 1
    )
    default String itemList()
    {
        return DEFAULT_ITEM_LIST;
    }

    @ConfigItem(
        keyName = "highlightMode",
        name = "Highlight mode",
        description = "Whitelist highlights only the items in the list. Blacklist highlights every item except those in the list (an empty list highlights everything).",
        position = 2
    )
    default HighlightMode highlightMode()
    {
        return HighlightMode.WHITELIST;
    }

    @Alpha
    @ConfigItem(
        keyName = "outlineColor",
        name = "Outline Color",
        description = "The color of the outline",
        position = 3
    )
    default Color outlineColor()
    {
        return Color.RED;
    }

    @Alpha
    @ConfigItem(
        keyName = "fillColor",
        name = "Fill Color",
        description = "The color of the fill",
        position = 4
    )
    default Color fillColor()
    {
        return new Color(255, 0, 0, 50);
    }

    @ConfigItem(
        keyName = "outlineOnly",
        name = "Outline Only",
        description = "Only show outline instead of filled highlight",
        position = 5
    )
    default boolean outlineOnly()
    {
        return false;
    }

    @ConfigItem(
        keyName = "outlineThickness",
        name = "Outline Thickness",
        description = "The thickness of the outline in pixels (doesn't work with sprite outlines)",
        position = 6
    )
    default int outlineThickness()
    {
        return 2;
    }

    @ConfigItem(
        keyName = "spriteOnly",
        name = "Sprite Only",
        description = "Highlight only the item sprite instead of the full clickbox",
        position = 7
    )
    default boolean spriteOnly()
    {
        return true;
    }

    @ConfigItem(
        keyName = "presets",
        name = "List Notepad",
        description = "Optional text storage area for quick copy/paste of item lists or notes. This field does not affect highlighting directly.",
        position = 99
    )
    default String presets()
    {
        return DEFAULT_NOTEPAD_TEXT;
    }

    @ConfigItem(
        keyName = "helpText",
        name = "Guide",
        description = "Quick reference for item names and wildcard matching",
        position = 101,
        section = helpSection
    )
    default String helpText()
    {
        return HELP_TEXT;
    }

    // Shared by both modes; the mode decides whether a list match means "highlight" (whitelist) or "suppress" (blacklist).
    enum HighlightMode
    {
        WHITELIST,
        BLACKLIST;

        @Override
        public String toString()
        {
            // Render as "Whitelist" / "Blacklist" in the config dropdown instead of the all-caps enum name.
            return name().charAt(0) + name().substring(1).toLowerCase();
        }
    }
}
