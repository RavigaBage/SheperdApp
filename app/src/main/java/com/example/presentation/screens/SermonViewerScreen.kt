package com.example.presentation.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.domain.model.ShepherdFile
import com.example.presentation.viewmodel.ShepherdViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.ui.theme.ShepherdGold
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SermonViewerScreen(
    viewModel: ShepherdViewModel,
    sermonId: String,
    filePath: String,
    sermonTitle: String,
    onBack: () -> Unit,
    onNavigateToPreach: (String, String, String, Int, Int, Float) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val paragraphs by viewModel.paragraphs.collectAsState()
    val isDocumentLoading by viewModel.isDocumentLoading.collectAsState()
    val documentLoadingStatus by viewModel.documentLoadingStatus.collectAsState()
    val warning by viewModel.alreadyPreachedWarning.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val allFiles by viewModel.files.collectAsState()

    val currentFile = remember(allFiles, sermonId) {
        allFiles.find { it.id == sermonId }
    }
    val isBookmarked = remember(bookmarks, sermonId) {
        bookmarks.any { it.fileId == sermonId }
    }

    // SharedPreferences for e-reader preferences
    val prefs = remember { context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE) }
    
    // Live persisted states
    var fontSizeSp by remember { mutableStateOf(prefs.getInt("font_size", 16)) }
    var brightness by remember { mutableStateOf(prefs.getFloat("brightness", 1.0f)) }
    var isNightMode by remember { mutableStateOf(prefs.getBoolean("night_mode", false)) }

    // Layout control states
    var showBrightnessSlider by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSetupSheet by remember { mutableStateOf(false) }
    var showSavedConfirmation by remember { mutableStateOf(false) }
    var selectedVerseForPopup by remember { mutableStateOf<String?>(null) }

    // Preach Mode Configuration sheet states
    var durationMinutes by remember { mutableStateOf(45) }
    var scrollSpeed by remember { mutableStateOf(2) }
    var fontScaleMultiplier by remember { mutableStateOf(1.3f) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Trigger sermon document loading
    LaunchedEffect(sermonId, filePath, sermonTitle) {
        viewModel.loadDocument(sermonId, filePath, sermonTitle)
    }

    // Apply system window brightness when brightness state changes
    LaunchedEffect(brightness) {
        val activity = context as? Activity
        activity?.let { act ->
            val lp = act.window.attributes
            lp.screenBrightness = brightness.coerceIn(0.01f, 1.0f)
            act.window.attributes = lp
        }
    }

    // Helper to format sermon paragraphs to HTML
    val htmlContent = remember(paragraphs) {
        paragraphs.joinToString("\n") { paragraph ->
            val text = paragraph.rawText
            val tag = when (paragraph.headingLevel) {
                1 -> "h1"
                2 -> "h2"
                else -> "p"
            }
            
            if (paragraph.verseSpans.isEmpty()) {
                "<$tag>$text</$tag>"
            } else {
                val sb = java.lang.StringBuilder(text)
                // Sort spans in descending order to insert HTML without corrupting previous indices
                val sortedSpans = paragraph.verseSpans.sortedByDescending { it.startIndex }
                for (span in sortedSpans) {
                    if (span.startIndex in 0..sb.length && span.endIndex in 0..sb.length && span.startIndex <= span.endIndex) {
                        val reference = span.reference
                        val textToWrap = sb.substring(span.startIndex, span.endIndex)
                        val wrapped = """<span class="verse-ref" onclick="Android.showVerse('$reference')">$textToWrap</span>"""
                        sb.replace(span.startIndex, span.endIndex, wrapped)
                    }
                }
                "<$tag>$sb</$tag>"
            }
        }
    }

    val pageHtml = remember(sermonId, sermonTitle, htmlContent, isNightMode, fontSizeSp) {
        """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>
            body {
                margin: 0;
                padding: 24px 24px 120px 24px;
                background-color: #FFFFFF;
                color: #1A1A1A;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                transition: background-color 0.2s, color 0.2s;
                user-select: text;
                -webkit-user-select: text;
            }

            body.night-mode {
                background-color: #121212;
                color: #E0E0E0;
            }

            h1.chapter-title {
                font-size: 20px;
                font-weight: bold;
                color: #0D1B2A;
                margin-top: 8px;
                margin-bottom: 24px;
                line-height: 1.3;
            }

            body.night-mode h1.chapter-title {
                color: #FFFFFF;
            }

            p {
                font-size: ${fontSizeSp}px;
                line-height: 1.6;
                margin-top: 0;
                margin-bottom: 16px;
                color: #1A1A1A;
            }

            body.night-mode p {
                color: #E0E0E0;
            }

            mark {
                color: inherit;
                border-radius: 4px;
                padding: 1px 2px;
                cursor: pointer;
            }

            .verse-ref {
                color: #1976D2;
                font-weight: bold;
                text-decoration: underline;
                cursor: pointer;
            }

            body.night-mode .verse-ref {
                color: #90CAF9;
            }

            .floating-toolbar {
                position: absolute;
                background-color: #1E1E24;
                border-radius: 30px;
                box-shadow: 0 4px 15px rgba(0, 0, 0, 0.4);
                padding: 6px 12px;
                display: flex;
                align-items: center;
                gap: 8px;
                transition: opacity 0.15s ease-in-out;
                z-index: 9999;
            }

            .color-dots {
                display: flex;
                gap: 8px;
                align-items: center;
                padding: 0 4px;
            }

            .dot {
                width: 18px;
                height: 18px;
                border-radius: 50%;
                cursor: pointer;
                border: 1.5px solid rgba(255, 255, 255, 0.7);
                box-sizing: border-box;
                display: inline-block;
            }

            .dot.yellow { background-color: #FFEB3B; }
            .dot.orange { background-color: #FF9800; }
            .dot.pink { background-color: #E91E63; }
            .dot.blue { background-color: #03A9F4; }
            .dot.purple { background-color: #9C27B0; }

            .divider {
                width: 1px;
                height: 16px;
                background-color: rgba(255, 255, 255, 0.2);
            }

            .toolbar-btn {
                background: none;
                border: none;
                color: #FFFFFF;
                font-size: 13px;
                font-weight: bold;
                cursor: pointer;
                display: flex;
                align-items: center;
                justify-content: center;
                padding: 4px 6px;
                border-radius: 4px;
            }

            .toolbar-btn:hover {
                background-color: rgba(255, 255, 255, 0.1);
            }
        </style>
        </head>
        <body class="${if (isNightMode) "night-mode" else ""}">

        <h1 class="chapter-title">${sermonTitle}</h1>

        <div id="content-body">
            ${htmlContent}
        </div>

        <div id="floating-toolbar" class="floating-toolbar" style="display: none;">
            <div class="color-dots">
                <span class="dot yellow" onclick="highlightSelection('rgba(255, 235, 59, 0.35)')"></span>
                <span class="dot orange" onclick="highlightSelection('rgba(255, 152, 0, 0.35)')"></span>
                <span class="dot pink" onclick="highlightSelection('rgba(233, 30, 99, 0.35)')"></span>
                <span class="dot blue" onclick="highlightSelection('rgba(3, 169, 244, 0.35)')"></span>
                <span class="dot purple" onclick="highlightSelection('rgba(156, 39, 176, 0.35)')"></span>
            </div>
            <div class="divider"></div>
            <button class="toolbar-btn" onclick="toggleBold()">B</button>
            <button class="toolbar-btn" onclick="copySelection()">Copy</button>
            <button class="toolbar-btn" onclick="shareSelection()">Share</button>
            <button class="toolbar-btn" onclick="increaseFontSize()">Aa</button>
        </div>

        <script>
            const sermonId = "${sermonId}";
            const root = document.getElementById('content-body');
            const originalHtml = root.innerHTML;
            const toolbar = document.getElementById('floating-toolbar');

            toolbar.addEventListener('mousedown', (e) => e.preventDefault());
            toolbar.addEventListener('touchstart', (e) => e.preventDefault());

            function getCharOffset(container, node, offset) {
                let charCount = 0;
                const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, null, false);
                while (walker.nextNode()) {
                    const currentNode = walker.currentNode;
                    if (currentNode === node) {
                        return charCount + offset;
                    }
                    charCount += currentNode.textContent.length;
                }
                return charCount;
            }

            function getNodeAndOffset(container, targetOffset) {
                let charCount = 0;
                const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, null, false);
                while (walker.nextNode()) {
                    const currentNode = walker.currentNode;
                    const nextCharCount = charCount + currentNode.textContent.length;
                    if (targetOffset >= charCount && targetOffset <= nextCharCount) {
                        return { node: currentNode, offset: targetOffset - charCount };
                    }
                    charCount = nextCharCount;
                }
                return null;
            }

            function applyHighlight(startOffset, endOffset, color) {
                const startRes = getNodeAndOffset(root, startOffset);
                const endRes = getNodeAndOffset(root, endOffset);
                if (startRes && endRes) {
                    try {
                        const range = document.createRange();
                        range.setStart(startRes.node, startRes.offset);
                        range.setEnd(endRes.node, endRes.offset);
                        
                        const mark = document.createElement('mark');
                        mark.style.backgroundColor = color;
                        mark.className = 'highlight-mark';
                        mark.setAttribute('data-start', startOffset);
                        mark.setAttribute('data-end', endOffset);
                        
                        mark.onclick = function(e) {
                            e.stopPropagation();
                            if (confirm("Remove this highlight?")) {
                                removeHighlight(startOffset, endOffset);
                            }
                        };
                        
                        range.surroundContents(mark);
                    } catch (e) {
                        console.error("Failed to apply highlight:", e);
                    }
                }
            }

            function removeHighlight(start, end) {
                const key = 'highlights_' + sermonId;
                let highlights = JSON.parse(localStorage.getItem(key) || '[]');
                highlights = highlights.filter(hl => !(hl.startOffset === start && hl.endOffset === end));
                localStorage.setItem(key, JSON.stringify(highlights));
                renderAllHighlights();
                if (window.Android && window.Android.notifySaved) {
                    window.Android.notifySaved();
                }
            }

            function renderAllHighlights() {
                root.innerHTML = originalHtml;
                const key = 'highlights_' + sermonId;
                const highlights = JSON.parse(localStorage.getItem(key) || '[]');
                highlights.sort((a, b) => b.startOffset - a.startOffset);
                highlights.forEach(hl => {
                    applyHighlight(hl.startOffset, hl.endOffset, hl.color);
                });
            }

            function highlightSelection(color) {
                const selection = window.getSelection();
                if (!selection || selection.isCollapsed) return;
                
                const range = selection.getRangeAt(0);
                const startOffset = getCharOffset(root, range.startContainer, range.startOffset);
                const endOffset = getCharOffset(root, range.endContainer, range.endOffset);
                
                if (startOffset === endOffset) return;
                
                const key = 'highlights_' + sermonId;
                let highlights = JSON.parse(localStorage.getItem(key) || '[]');
                
                highlights.push({
                    chapterId: sermonId,
                    startOffset: startOffset,
                    endOffset: endOffset,
                    color: color
                });
                
                localStorage.setItem(key, JSON.stringify(highlights));
                renderAllHighlights();
                
                selection.removeAllRanges();
                hideToolbar();
                
                if (window.Android && window.Android.notifySaved) {
                    window.Android.notifySaved();
                }
            }

            function toggleBold() {
                document.execCommand('bold', false, null);
                hideToolbar();
            }

            function copySelection() {
                const text = window.getSelection().toString();
                if (window.Android && window.Android.copyToClipboard) {
                    window.Android.copyToClipboard(text);
                }
                window.getSelection().removeAllRanges();
                hideToolbar();
            }

            function shareSelection() {
                const text = window.getSelection().toString();
                if (window.Android && window.Android.shareText) {
                    window.Android.shareText(text);
                }
                window.getSelection().removeAllRanges();
                hideToolbar();
            }

            function increaseFontSize() {
                if (window.Android && window.Android.increaseFontSize) {
                    window.Android.increaseFontSize();
                }
            }

            function handleSelectionChange() {
                const selection = window.getSelection();
                if (!selection || selection.isCollapsed || selection.toString().trim() === "") {
                    hideToolbar();
                    return;
                }
                
                const range = selection.getRangeAt(0);
                const rect = range.getBoundingClientRect();
                
                toolbar.style.display = 'flex';
                const toolbarWidth = toolbar.offsetWidth || 280;
                const toolbarHeight = toolbar.offsetHeight || 42;
                
                let left = rect.left + (rect.width / 2) - (toolbarWidth / 2);
                let top = rect.top + window.scrollY - toolbarHeight - 12;
                
                if (left < 10) left = 10;
                if (left + toolbarWidth > window.innerWidth - 10) {
                    left = window.innerWidth - toolbarWidth - 10;
                }
                if (top < 10) {
                    top = rect.bottom + window.scrollY + 12;
                }
                
                toolbar.style.left = left + 'px';
                toolbar.style.top = top + 'px';
            }

            function hideToolbar() {
                toolbar.style.display = 'none';
            }

            document.addEventListener('mouseup', () => setTimeout(handleSelectionChange, 50));
            document.addEventListener('touchend', () => setTimeout(handleSelectionChange, 50));
            document.addEventListener('selectionchange', () => {
                const selection = window.getSelection();
                if (!selection || selection.isCollapsed) {
                    hideToolbar();
                }
            });

            function setNightMode(isNight) {
                if (isNight) {
                    document.body.classList.add('night-mode');
                } else {
                    document.body.classList.remove('night-mode');
                }
            }

            function setFontSize(spSize) {
                const paragraphs = document.querySelectorAll('p');
                paragraphs.forEach(p => {
                    p.style.fontSize = spSize + 'px';
                });
            }

            window.onload = function() {
                setFontSize(${fontSizeSp});
                renderAllHighlights();
            };
        </script>
        </body>
        </html>
        """.trimIndent()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = sermonTitle,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (isNightMode) Color.White else Color(0xFF0D1B2A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isNightMode) Color.White else Color(0xFF1A1A1A)
                        )
                    }
                },
                actions = {
                    // Bookmark toggle
                    IconButton(onClick = {
                        currentFile?.let { file ->
                            viewModel.toggleBookmark(file, sermonTitle)
                        }
                    }) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isNightMode) Color.White else Color(0xFF1A1A1A)
                        )
                    }

                    // Overflow Menu Trigger
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = if (isNightMode) Color.White else Color(0xFF1A1A1A)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Preach Mode Options") },
                            onClick = {
                                showMenu = false
                                showSetupSheet = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            // Persistent Bottom Toolbar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isNightMode) Color(0xFF1E1E1E) else Color.White)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            ) {
                // Smooth Brightness slider panel when toggled
                if (showBrightnessSlider) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LightMode,
                            contentDescription = "Brightness",
                            tint = if (isNightMode) Color.White else Color(0xFF0D1B2A),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = brightness,
                            onValueChange = { newBrightness ->
                                brightness = newBrightness
                                prefs.edit().putFloat("brightness", newBrightness).apply()
                            },
                            valueRange = 0.05f..1.0f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFF0D1B2A),
                                inactiveTrackColor = Color(0xFF0D1B2A).copy(alpha = 0.24f),
                                thumbColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(brightness * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = if (isNightMode) Color.White else Color(0xFF0D1B2A),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    HorizontalDivider(color = if (isNightMode) Color.DarkGray else Color.LightGray)
                }

                // Core control actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share Sermon entire content
                    IconButton(onClick = {
                        val sermonFullText = paragraphs.joinToString("\n\n") { it.rawText }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, sermonTitle)
                            putExtra(Intent.EXTRA_TEXT, sermonFullText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Sermon"))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Sermon",
                            tint = if (isNightMode) Color.White else Color(0xFF0D1B2A)
                        )
                    }

                    // Brightness slider toggle
                    IconButton(onClick = {
                        showBrightnessSlider = !showBrightnessSlider
                    }) {
                        Icon(
                            imageVector = if (showBrightnessSlider) Icons.Default.LightMode else Icons.Outlined.LightMode,
                            contentDescription = "Toggle Brightness Panel",
                            tint = if (isNightMode) Color.White else Color(0xFF0D1B2A)
                        )
                    }

                    // Aa font-size stepper controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (fontSizeSp > 12) {
                                    fontSizeSp--
                                    prefs.edit().putInt("font_size", fontSizeSp).apply()
                                    webViewRef?.evaluateJavascript("setFontSize($fontSizeSp)", null)
                                }
                            },
                            enabled = fontSizeSp > 12
                        ) {
                            Text(
                                text = "A-",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (fontSizeSp > 12) (if (isNightMode) Color.White else Color(0xFF0D1B2A)) else Color.Gray
                            )
                        }
                        
                        Text(
                            text = "${fontSizeSp}px",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isNightMode) Color.White else Color(0xFF0D1B2A)
                        )

                        IconButton(
                            onClick = {
                                if (fontSizeSp < 32) {
                                    fontSizeSp++
                                    prefs.edit().putInt("font_size", fontSizeSp).apply()
                                    webViewRef?.evaluateJavascript("setFontSize($fontSizeSp)", null)
                                }
                            },
                            enabled = fontSizeSp < 32
                        ) {
                            Text(
                                text = "A+",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (fontSizeSp < 32) (if (isNightMode) Color.White else Color(0xFF0D1B2A)) else Color.Gray
                            )
                        }
                    }

                    // Night-mode switch
                    IconButton(onClick = {
                        isNightMode = !isNightMode
                        prefs.edit().putBoolean("night_mode", isNightMode).apply()
                        webViewRef?.evaluateJavascript("setNightMode($isNightMode)", null)
                    }) {
                        Icon(
                            imageVector = if (isNightMode) Icons.Default.DarkMode else Icons.Outlined.DarkMode,
                            contentDescription = "Toggle Night Mode",
                            tint = if (isNightMode) Color.White else Color(0xFF0D1B2A)
                        )
                    }
                }
            }
        },
        containerColor = if (isNightMode) Color(0xFF121212) else Color.White
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // "Already Preached" Warning banner
                warning?.let { warnMsg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF8E1),
                            contentColor = Color(0xFF5D4037)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "⚠️",
                                fontSize = 20.sp,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                warnMsg,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Main Reading content
                if (isDocumentLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .widthIn(max = 400.dp)
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = ShepherdGold,
                                        strokeWidth = 4.dp,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = documentLoadingStatus.ifBlank { "Processing Sermon..." },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Scanning text content, metadata, and scripture cross-references.",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.85f)
                                                .height(14.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.LightGray.copy(alpha = 0.4f))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.95f)
                                                .height(14.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.LightGray.copy(alpha = 0.4f))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.6f)
                                                .height(14.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.LightGray.copy(alpha = 0.4f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (paragraphs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Article,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Parsing document or file is empty.\nSupports TXT, DOCX, and PDF formats.",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                lineHeight = 20.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Integrated interactive WebView E-reader
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    textZoom = 100
                                    cacheMode = WebSettings.LOAD_NO_CACHE
                                }
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        // Sync settings immediately on load
                                        evaluateJavascript("setFontSize($fontSizeSp)", null)
                                        evaluateJavascript("setNightMode($isNightMode)", null)
                                    }
                                }
                                addJavascriptInterface(
                                    WebAppInterface(
                                        context = ctx,
                                        onSaved = {
                                            scope.launch {
                                                showSavedConfirmation = true
                                                delay(1500)
                                                showSavedConfirmation = false
                                            }
                                        },
                                        onShare = { text ->
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, text)
                                            }
                                            ctx.startActivity(Intent.createChooser(shareIntent, "Share Highlight"))
                                        },
                                        onShowVerse = { reference ->
                                            selectedVerseForPopup = reference
                                        },
                                        onIncreaseFontSize = {
                                            if (fontSizeSp < 32) {
                                                fontSizeSp++
                                                prefs.edit().putInt("font_size", fontSizeSp).apply()
                                                evaluateJavascript("setFontSize($fontSizeSp)", null)
                                            } else {
                                                fontSizeSp = 16
                                                prefs.edit().putInt("font_size", fontSizeSp).apply()
                                                evaluateJavascript("setFontSize($fontSizeSp)", null)
                                            }
                                        },
                                        onCopied = {
                                            // Handle copied alert / UI confirmation if desired
                                        }
                                    ),
                                    "Android"
                                )
                                webViewRef = this
                            }
                        },
                        update = { webView ->
                            webView.loadDataWithBaseURL("https://local.sermonviewer", pageHtml, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Unobtrusive "Saved" Floating Confirmation Pill
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                AnimatedVisibility(
                    visible = showSavedConfirmation,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF0D1B2A), // Navy pill background
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .padding(bottom = 32.dp)
                            .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50), // Nice green accent
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Saved",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    // Verse Popup bottom sheet
    selectedVerseForPopup?.let { verseRef ->
        VersePopupBottomSheet(
            viewModel = viewModel,
            verseReference = verseRef,
            onDismiss = { selectedVerseForPopup = null }
        )
    }

    // Interactive Preach Configuration modal bottom sheet
    if (showSetupSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSetupSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Setup Preaching Mode",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider()

                // Slider 1: Timer Duration
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sermon Duration", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("$durationMinutes Mins", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = durationMinutes.toFloat(),
                        onValueChange = { durationMinutes = it.toInt() },
                        valueRange = 10f..120f,
                        steps = 11
                    )
                }

                // Slider 2: Autoscroll speed
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Prompter Auto-scroll Speed", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Speed $scrollSpeed", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = scrollSpeed.toFloat(),
                        onValueChange = { scrollSpeed = it.toInt() },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                }

                // Slider 3: Size multiplier
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Text Size Scale", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("${String.format("%.1f", fontScaleMultiplier)}x", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = fontScaleMultiplier,
                        onValueChange = { fontScaleMultiplier = it },
                        valueRange = 1.0f..2.0f
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        showSetupSheet = false
                        onNavigateToPreach(
                            sermonId,
                            filePath,
                            sermonTitle,
                            durationMinutes,
                            scrollSpeed,
                            fontScaleMultiplier
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enter Preach Mode", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

class WebAppInterface(
    private val context: Context,
    private val onSaved: () -> Unit,
    private val onShare: (String) -> Unit,
    private val onShowVerse: (String) -> Unit,
    private val onIncreaseFontSize: () -> Unit,
    private val onCopied: () -> Unit
) {
    @JavascriptInterface
    fun notifySaved() {
        onSaved()
    }

    @JavascriptInterface
    fun shareText(text: String) {
        onShare(text)
    }

    @JavascriptInterface
    fun showVerse(reference: String) {
        onShowVerse(reference)
    }

    @JavascriptInterface
    fun increaseFontSize() {
        onIncreaseFontSize()
    }

    @JavascriptInterface
    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Sermon Text", text)
        clipboard.setPrimaryClip(clip)
        onCopied()
    }
}
