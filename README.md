# RuneLite Inventory Hover Highlighter

A lightweight RuneLite plugin that outlines configured items when you hover them in inventory-style interfaces.

## Features

- Highlight matching items only while they are hovered
- Wildcard matching support (e.g., `rune*` matches all rune items)
- Multiple highlight styles (outline, fill, or both)
- Choose between item sprite or full slot highlighting
- Built-in default highlight list for common food and potion items
- List Notepad config field for temporarily storing item lists or notes

## Quick Start

1. Install via RuneLite Plugin Hub
2. Configure items to highlight (comma-separated)
3. Customize colors and style preferences
4. Optional: Use List Notepad to store extra item lists or notes for later copy/paste

## Examples

### Basic Items
```
Coins, rune scimitar, Lobster
```

### Using Wildcards
```
rune*, *potion, *shark*
```

## Tips

- Names are not case-sensitive
- Use commas to separate items
- Plain item names match exact item names only, so `Shark` matches `Shark` but not `Raw shark`
- Add `*` for wildcards: `angler*` starts with angler, `*potion` ends with potion, and `*shark*` contains shark
- List Notepad is only for copy/paste storage; it does not affect what gets highlighted

## Support

For issues or suggestions, please report through the GitHub repository: https://github.com/Emtec-byte/InventoryHighlighter

Created by Cheese cake