package org.jormungandr.core.theme

/**
 * Core surface and text color tokens.
 */
data class ColorTokens(
    val background: String,
    val secondaryBackground: String,
    val surface: String,
    val foreground: String,
    val mutedForeground: String,
    val border: String,
    val accent: String,
    val accentHover: String,
    val selectionBackground: String,
    val selectionForeground: String,
    val error: String,
    val warning: String,
    val success: String,
    val info: String
)

/**
 * Tabular and dataframe grid styling tokens.
 */
data class DataGridThemeTokens(
    val headerBackground: String,
    val headerForeground: String,
    val rowEvenBackground: String,
    val rowOddBackground: String,
    val gridLineColor: String,
    val selectionBackground: String,
    val selectionForeground: String
)

/**
 * Syntax highlighting tokens for code, queries, and notebooks.
 */
data class SyntaxThemeTokens(
    val keyword: String,
    val string: String,
    val number: String,
    val comment: String,
    val function: String,
    val variable: String,
    val type: String
)

/**
 * Universal theme specification applied across all IDE windows,
 * perspectives, and modular extensions.
 */
data class JormungandrTheme(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val colors: ColorTokens,
    val dataGrid: DataGridThemeTokens,
    val syntax: SyntaxThemeTokens,
    val chartPalette: List<String>
) {
    /**
     * Serializes theme tokens into standard CSS custom properties
     * for embedding in JCEF webviews, Jupyter notebook cells, and charts.
     */
    fun toCssVariables(): String = buildString {
        appendLine(":root {")
        appendLine("  --jg-bg: ${colors.background};")
        appendLine("  --jg-bg-secondary: ${colors.secondaryBackground};")
        appendLine("  --jg-surface: ${colors.surface};")
        appendLine("  --jg-fg: ${colors.foreground};")
        appendLine("  --jg-fg-muted: ${colors.mutedForeground};")
        appendLine("  --jg-border: ${colors.border};")
        appendLine("  --jg-accent: ${colors.accent};")
        appendLine("  --jg-accent-hover: ${colors.accentHover};")
        appendLine("  --jg-selection-bg: ${colors.selectionBackground};")
        appendLine("  --jg-selection-fg: ${colors.selectionForeground};")
        appendLine("  --jg-error: ${colors.error};")
        appendLine("  --jg-warning: ${colors.warning};")
        appendLine("  --jg-success: ${colors.success};")
        appendLine("  --jg-info: ${colors.info};")
        appendLine("  --jg-grid-header-bg: ${dataGrid.headerBackground};")
        appendLine("  --jg-grid-header-fg: ${dataGrid.headerForeground};")
        appendLine("  --jg-grid-row-even: ${dataGrid.rowEvenBackground};")
        appendLine("  --jg-grid-row-odd: ${dataGrid.rowOddBackground};")
        appendLine("  --jg-grid-line: ${dataGrid.gridLineColor};")
        appendLine("  --jg-syntax-keyword: ${syntax.keyword};")
        appendLine("  --jg-syntax-string: ${syntax.string};")
        appendLine("  --jg-syntax-number: ${syntax.number};")
        appendLine("  --jg-syntax-comment: ${syntax.comment};")
        appendLine("  --jg-syntax-func: ${syntax.function};")
        appendLine("}")
    }

    companion object {
        /**
         * The official Solarized Light palette defined by Ethan Schoonover.
         */
        val SOLARIZED_LIGHT = JormungandrTheme(
            id = "solarized.light",
            name = "Solarized Light",
            isDark = false,
            colors = ColorTokens(
                background = "#FDF6E3",          // base3
                secondaryBackground = "#EEE8D5", // base2
                surface = "#FAF2DC",
                foreground = "#657B83",          // base00
                mutedForeground = "#93A1A1",     // base1
                border = "#E0D8C3",
                accent = "#268BD2",              // blue
                accentHover = "#2AA198",         // cyan
                selectionBackground = "#EEE8D5", // base2
                selectionForeground = "#586E75", // base01
                error = "#DC322F",               // red
                warning = "#CB4B16",             // orange
                success = "#859900",             // green
                info = "#268BD2"                 // blue
            ),
            dataGrid = DataGridThemeTokens(
                headerBackground = "#E8E2CF",
                headerForeground = "#586E75",
                rowEvenBackground = "#FDF6E3",
                rowOddBackground = "#F7F1DD",
                gridLineColor = "#E0D8C3",
                selectionBackground = "#E3DCBA",
                selectionForeground = "#073642"
            ),
            syntax = SyntaxThemeTokens(
                keyword = "#859900", // green
                string = "#2AA198",  // cyan
                number = "#D33682",  // magenta
                comment = "#93A1A1", // base1
                function = "#268BD2",// blue
                variable = "#B58900",// yellow
                type = "#CB4B16"     // orange
            ),
            chartPalette = listOf(
                "#268BD2", // blue
                "#2AA198", // cyan
                "#859900", // green
                "#B58900", // yellow
                "#CB4B16", // orange
                "#DC322F", // red
                "#D33682", // magenta
                "#6C71C4"  // violet
            )
        )

        /**
         * "Bubblegum Barbie" theme celebrating vibrant and pastel shades of pink,
         * with rich berry and plum accents for ultra-crisp legibility.
         */
        val BUBBLEGUM_BARBIE = JormungandrTheme(
            id = "bubblegum.barbie",
            name = "Bubblegum Barbie",
            isDark = false,
            colors = ColorTokens(
                background = "#FFF5F8",          // Soft bubblegum blush
                secondaryBackground = "#FFE4EC", // Misty rose
                surface = "#FFF0F5",             // Lavender blush
                foreground = "#4A154B",          // Deep berry plum for high contrast
                mutedForeground = "#9C27B0",     // Rich orchid purple
                border = "#F8BBD0",              // Soft carnation border
                accent = "#E0218A",              // Iconic Barbie pink
                accentHover = "#FF4081",         // Vibrant hot pink
                selectionBackground = "#FFD1DC", // Pastel pink
                selectionForeground = "#2D0A24", // Midnight plum
                error = "#D81B60",               // Deep magenta/raspberry
                warning = "#FF80AB",             // Neon flamingo
                success = "#00BFA5",             // Mint/teal accent
                info = "#E0218A"                 // Barbie pink
            ),
            dataGrid = DataGridThemeTokens(
                headerBackground = "#F8BBD0",    // Carnation pink header
                headerForeground = "#3D0C2E",    // Deep berry
                rowEvenBackground = "#FFF5F8",   // Light pink even rows
                rowOddBackground = "#FFE8F1",    // Rose odd rows
                gridLineColor = "#F48FB1",       // Soft rose grid lines
                selectionBackground = "#FF80AB", // Hot pink cell highlight
                selectionForeground = "#FFFFFF"
            ),
            syntax = SyntaxThemeTokens(
                keyword = "#D81B60",             // Hot fuchsia
                string = "#E91E63",              // Barbie rose pink
                number = "#AB47BC",              // Bubblegum purple orchid
                comment = "#CE93D8",             // Soft mauve lavender
                function = "#E0218A",            // Iconic Barbie pink
                variable = "#AD1457",            // Deep raspberry
                type = "#880E4F"                 // Burgundy rose
            ),
            chartPalette = listOf(
                "#E0218A", // Barbie pink
                "#FF4081", // Hot pink
                "#F06292", // Carnation
                "#EC407A", // Bubblegum
                "#D81B60", // Deep fuchsia
                "#BA68C8", // Pinkish orchid
                "#FF80AB", // Neon flamingo
                "#FFB6C1"  // Light pink
            )
        )

        /**
         * "Nordic Deep Ocean" theme inspired by Arctic seas:
         * Deep navy midnight backgrounds, frost cyan and seafoam green syntax accents.
         */
        val NORDIC_DEEP_OCEAN = JormungandrTheme(
            id = "nordic.deep.ocean",
            name = "Nordic Deep Ocean",
            isDark = true,
            colors = ColorTokens(
                background = "#0B1320",
                secondaryBackground = "#131E30",
                surface = "#0F1A2C",
                foreground = "#D8DEE9",
                mutedForeground = "#64748B",
                border = "#1E293B",
                accent = "#88C0D0",
                accentHover = "#8FBCBB",
                selectionBackground = "#203047",
                selectionForeground = "#ECEFF4",
                error = "#BF616A",
                warning = "#EBCB8B",
                success = "#A3BE8C",
                info = "#81A1C1"
            ),
            dataGrid = DataGridThemeTokens(
                headerBackground = "#131E30",
                headerForeground = "#94A3B8",
                rowEvenBackground = "#0B1320",
                rowOddBackground = "#0F1A2C",
                gridLineColor = "#1E293B",
                selectionBackground = "#203047",
                selectionForeground = "#ECEFF4"
            ),
            syntax = SyntaxThemeTokens(
                keyword = "#81A1C1",
                string = "#A3BE8C",
                number = "#B48EAD",
                comment = "#64748B",
                function = "#88C0D0",
                variable = "#D8DEE9",
                type = "#8FBCBB"
            ),
            chartPalette = listOf(
                "#88C0D0", // Frost cyan
                "#8FBCBB", // Seafoam
                "#81A1C1", // Glacial blue
                "#5E81AC", // Deep polar blue
                "#A3BE8C", // Arctic green
                "#EBCB8B", // Aurora yellow
                "#D08770", // Aurora orange
                "#B48EAD"  // Aurora purple
            )
        )

        /**
         * "Cyberpunk Neon" theme featuring deep obsidian surfaces,
         * electric neon cyan, synthwave fuchsia, and matrix green accents.
         */
        val CYBERPUNK_NEON = JormungandrTheme(
            id = "cyberpunk.neon",
            name = "Cyberpunk Neon",
            isDark = true,
            colors = ColorTokens(
                background = "#0D0B18",
                secondaryBackground = "#161326",
                surface = "#1E1A33",
                foreground = "#F8F8F2",
                mutedForeground = "#6272A4",
                border = "#362F58",
                accent = "#00F0FF",
                accentHover = "#FF007F",
                selectionBackground = "#3C2A59",
                selectionForeground = "#FFFFFF",
                error = "#FF5555",
                warning = "#FFB86C",
                success = "#50FA7B",
                info = "#8BE9FD"
            ),
            dataGrid = DataGridThemeTokens(
                headerBackground = "#1E1A33",
                headerForeground = "#00F0FF",
                rowEvenBackground = "#0D0B18",
                rowOddBackground = "#161326",
                gridLineColor = "#362F58",
                selectionBackground = "#FF007F",
                selectionForeground = "#FFFFFF"
            ),
            syntax = SyntaxThemeTokens(
                keyword = "#FF79C6",
                string = "#F1FA8C",
                number = "#BD93F9",
                comment = "#6272A4",
                function = "#50FA7B",
                variable = "#8BE9FD",
                type = "#00F0FF"
            ),
            chartPalette = listOf(
                "#00F0FF", // Electric cyan
                "#FF007F", // Neon fuchsia
                "#50FA7B", // Matrix green
                "#BD93F9", // Synthwave purple
                "#FFB86C", // Neon amber
                "#F1FA8C", // Laser yellow
                "#FF5555", // Laser red
                "#8BE9FD"  // Ice blue
            )
        )

        /**
         * "Gruvbox Dark" retro groove warm sepia palette with earthy tones
         * and high-contrast pastel accents.
         */
        val GRUVBOX_DARK = JormungandrTheme(
            id = "gruvbox.dark",
            name = "Gruvbox Dark",
            isDark = true,
            colors = ColorTokens(
                background = "#1D2021",
                secondaryBackground = "#282828",
                surface = "#32302F",
                foreground = "#EBDBB2",
                mutedForeground = "#928374",
                border = "#3C3836",
                accent = "#FE8019",
                accentHover = "#FABD2F",
                selectionBackground = "#504945",
                selectionForeground = "#FBF1C7",
                error = "#FB4934",
                warning = "#FABD2F",
                success = "#B8BB26",
                info = "#83A598"
            ),
            dataGrid = DataGridThemeTokens(
                headerBackground = "#282828",
                headerForeground = "#FABD2F",
                rowEvenBackground = "#1D2021",
                rowOddBackground = "#282828",
                gridLineColor = "#3C3836",
                selectionBackground = "#504945",
                selectionForeground = "#FBF1C7"
            ),
            syntax = SyntaxThemeTokens(
                keyword = "#FB4934",
                string = "#B8BB26",
                number = "#D3869B",
                comment = "#928374",
                function = "#8EC07C",
                variable = "#EBDBB2",
                type = "#FABD2F"
            ),
            chartPalette = listOf(
                "#FE8019", // Warm orange
                "#B8BB26", // Gruvbox green
                "#FABD2F", // Gruvbox yellow
                "#83A598", // Gruvbox blue
                "#D3869B", // Gruvbox purple
                "#8EC07C", // Gruvbox aqua
                "#FB4934", // Gruvbox red
                "#EBDBB2"  // Cream
            )
        )

        /**
         * "Monokai Pro" sleek modern dark theme with refined pastel accents
         * and calibrated brightness.
         */
        val MONOKAI_PRO = JormungandrTheme(
            id = "monokai.pro",
            name = "Monokai Pro",
            isDark = true,
            colors = ColorTokens(
                background = "#222222",
                secondaryBackground = "#2D2A2E",
                surface = "#363337",
                foreground = "#FCFCFA",
                mutedForeground = "#727072",
                border = "#403E41",
                accent = "#FFD866",
                accentHover = "#FF6188",
                selectionBackground = "#49464E",
                selectionForeground = "#FFFFFF",
                error = "#FF6188",
                warning = "#FC9867",
                success = "#A9DC76",
                info = "#78DCE8"
            ),
            dataGrid = DataGridThemeTokens(
                headerBackground = "#2D2A2E",
                headerForeground = "#FFD866",
                rowEvenBackground = "#222222",
                rowOddBackground = "#2A272B",
                gridLineColor = "#403E41",
                selectionBackground = "#49464E",
                selectionForeground = "#FCFCFA"
            ),
            syntax = SyntaxThemeTokens(
                keyword = "#FF6188",
                string = "#A9DC76",
                number = "#AB9DF2",
                comment = "#727072",
                function = "#78DCE8",
                variable = "#FCFCFA",
                type = "#FFD866"
            ),
            chartPalette = listOf(
                "#FFD866", // Warm gold
                "#78DCE8", // Cyan
                "#A9DC76", // Lime green
                "#FF6188", // Pink fuchsia
                "#AB9DF2", // Violet
                "#FC9867", // Orange
                "#727072", // Muted gray
                "#FCFCFA"  // Soft white
            )
        )
    }
}
