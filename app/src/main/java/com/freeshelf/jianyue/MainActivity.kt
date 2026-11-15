package com.freeshelf.jianyue

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.ZipInputStream

data class Novel(
    val id: String,
    val title: String,
    val author: String,
    val summary: String,
    val downloadUrl: String? = null,
    val content: String? = null
)

private val sampleBooks = listOf(
    Novel("local-1", "西游记", "吴承恩", "中国古典四大名著之一。", content = "花果山上有一块仙石，内育仙胎。石猴出世，拜师学艺，后来保护唐僧西天取经。\n\n这是应用内的阅读演示内容。"),
    Novel("local-2", "水浒传", "施耐庵", "英雄豪杰聚义梁山的经典章回小说。", content = "话说天下大势，分久必合，合久必分。梁山泊英雄聚义，各有来历，各显神通。\n\n这是应用内的阅读演示内容。"),
    Novel("local-3", "三国演义", "罗贯中", "描写东汉末年至三国归晋的历史演义。", content = "滚滚长江东逝水，浪花淘尽英雄。话说天下大势，分久必合，合久必分。\n\n这是应用内的阅读演示内容。"),
    Novel("local-4", "红楼梦", "曹雪芹", "以贾、史、王、薛四大家族兴衰为背景。", content = "此开卷第一回也。作者自云：因曾历过一番梦幻之后，故将真事隐去。\n\n这是应用内的阅读演示内容。"),
    Novel("local-5", "骆驼祥子", "老舍", "描写旧北京人力车夫祥子的命运起伏。", content = "祥子是乡下人，口齿没有城里人那么灵便。\n\n这是应用内的阅读演示内容。")
)

private fun prefs(context: Context) = context.getSharedPreferences("jianyue_reader", Context.MODE_PRIVATE)

private fun loadImported(context: Context): List<Novel> {
    return try {
        val array = JSONArray(prefs(context).getString("imported_books", "[]"))
        (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            Novel(item.optString("id"), item.optString("title"), item.optString("author"), "本地导入", content = item.optString("content"))
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveImported(context: Context, books: List<Novel>) {
    val array = JSONArray()
    books.forEach { book ->
        array.put(JSONObject().put("id", book.id).put("title", book.title).put("author", book.author).put("content", book.content.orEmpty()))
    }
    prefs(context).edit().putString("imported_books", array.toString()).apply()
}

private suspend fun readLocalBook(context: Context, uri: Uri, displayName: String): String = withContext(Dispatchers.IO) {
    val input = context.contentResolver.openInputStream(uri) ?: error("无法打开文件")
    input.use { stream ->
        if (displayName.endsWith(".epub", true)) {
            val entries = mutableListOf<Pair<String, String>>()
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    if ((name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm")) && !name.contains("nav")) {
                        val html = zip.readBytes().toString(Charsets.UTF_8)
                        val text = html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), "")
                            .replace(Regex("(?i)</(p|div|h[1-6]|br|li|tr|section)>"), "\\n\\n")
                            .replace(Regex("<[^>]+>"), " ")
                            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                            .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                            .replace(Regex("[ \\t]+"), " ").replace(Regex("\\n[ \\t]+"), "\\n").trim()
                        if (text.isNotBlank()) entries.add(entry.name to text)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            entries.sortedBy { it.first }.joinToString("\\n\\n") { it.second }.also {
                if (it.isBlank()) error("未能从 EPUB 文件中提取正文")
            }
        } else {
            val bytes = stream.readBytes()
            try { bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF") }
            catch (_: Exception) { bytes.toString(Charset.forName("GB18030")) }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JianYueApp() }
    }
}

private suspend fun searchPublicBooks(keyword: String): List<Novel> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(keyword, "UTF-8")
    val connection = (URL("https://gutendex.com/books/?search=$encoded").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 12000
        readTimeout = 20000
        setRequestProperty("User-Agent", "JianYue-Reader/1.2")
    }
    try {
        val json = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        val results = json.optJSONArray("results") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val formats = item.optJSONObject("formats") ?: JSONObject()
                var textUrl: String? = null
                val keys = formats.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key.startsWith("text/plain", ignoreCase = true)) {
                        textUrl = formats.optString(key).takeIf { it.startsWith("https://") }
                        if (textUrl != null) break
                    }
                }
                if (textUrl == null) continue
                val authors = item.optJSONArray("authors")
                val author = if (authors != null && authors.length() > 0) authors.optJSONObject(0)?.optString("name").orEmpty() else "未知作者"
                add(Novel("gutenberg-${item.optInt("id")}", item.optString("title", "未命名作品"), author.ifBlank { "未知作者" }, "古登堡计划公版电子书 · 可在线阅读", textUrl))
            }
        }
    } finally { connection.disconnect() }
}

private suspend fun downloadBook(book: Novel): String = withContext(Dispatchers.IO) {
    val address = book.downloadUrl ?: return@withContext book.content.orEmpty()
    val connection = (URL(address).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 30000
        setRequestProperty("User-Agent", "JianYue-Reader/1.2")
    }
    try {
        var text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val starts = listOf("*** START OF THE PROJECT GUTENBERG", "*** START OF THIS PROJECT GUTENBERG")
        starts.map { text.indexOf(it, true) }.filter { it >= 0 }.minOrNull()?.let { pos -> text.indexOf('\n', pos).takeIf { it >= 0 }?.let { text = text.substring(it + 1) } }
        listOf("*** END OF THE PROJECT GUTENBERG", "*** END OF THIS PROJECT GUTENBERG").map { text.indexOf(it, true) }.filter { it >= 0 }.minOrNull()?.let { text = text.substring(0, it) }
        text.trim()
    } finally { connection.disconnect() }
}

@Composable
fun JianYueApp() {
    val context = LocalContext.current
    val stored = remember { prefs(context) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Novel>>(emptyList()) }
    var imported by remember { mutableStateOf(loadImported(context)) }
    var selected by remember { mutableStateOf<Novel?>(null) }
    var readerText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(stored.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var showFavorites by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(stored.getBoolean("dark", false)) }
    var settingsOpen by remember { mutableStateOf(false) }
    var fontSize by remember { mutableFloatStateOf(stored.getFloat("font_size", 19f)) }
    var lineSpacing by remember { mutableFloatStateOf(stored.getFloat("line_spacing", 1.85f)) }
    var paragraphSpacing by remember { mutableFloatStateOf(stored.getFloat("paragraph_spacing", 1)) }
    var letterSpacing by remember { mutableFloatStateOf(stored.getFloat("letter_spacing", 0f)) }
    var pageMargin by remember { mutableFloatStateOf(stored.getFloat("page_margin", 18f)) }
    var fontChoice by remember { mutableStateOf(stored.getString("font_family", "默认") ?: "默认") }
    var pageColor by remember { mutableStateOf(stored.getString("page_color", "米白") ?: "米白") }
    var bookmarked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bg = when (pageColor) {
        "纯白" -> Color.White
        "护眼绿" -> Color(0xFFE5EEDC)
        "羊皮纸" -> Color(0xFFE9DCC3)
        "深灰" -> Color(0xFF202226)
        else -> if (dark) Color(0xFF17191D) else Color(0xFFF7F5F0)
    }
    val fg = if (pageColor == "深灰" || (dark && pageColor == "米白")) Color(0xFFE9E5DC) else Color(0xFF292820)
    val fontFamily = if (fontChoice == "宋体") FontFamily.Serif else FontFamily.SansSerif

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                loading = true
                try {
                    val name = (uri.lastPathSegment ?: "本地书籍").substringAfterLast('/')
                    val content = readLocalBook(context, uri, name)
                    val title = name.substringBeforeLast('.', name)
                    val book = Novel("import-${System.currentTimeMillis()}", title, "本地书籍", "本地导入 · ${if (name.endsWith(".epub", true)) "EPUB" else "TXT"}", content = content)
                    imported = imported + book
                    saveImported(context, imported)
                    error = "已导入：$title"
                } catch (e: Exception) { error = "导入失败：${e.message ?: "文件读取异常"}" }
                finally { loading = false }
            }
        }
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFFC6A878), background = bg, surface = Color(0xFF24272D)) else lightColorScheme(primary = Color(0xFF80633D), background = bg, surface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            if (settingsOpen) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { settingsOpen = false }) { Icon(Icons.Default.ArrowBack, "返回", tint = fg) }
                        Text("阅读设置", color = fg, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("字体与排版", color = fg, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    SettingSlider("字号", fontSize, 14f, 34f, "${fontSize.toInt()} sp") { fontSize = it; stored.edit().putFloat("font_size", it).apply() }
                    SettingSlider("行间距", lineSpacing, 1.3f, 2.5f, String.format("%.2f 倍", lineSpacing)) { lineSpacing = it; stored.edit().putFloat("line_spacing", it).apply() }
                    SettingSlider("段落间距", paragraphSpacing, 0f, 3f, "${paragraphSpacing.toInt()} 行") { paragraphSpacing = it; stored.edit().putFloat("paragraph_spacing", it).apply() }
                    SettingSlider("字间距", letterSpacing, 0f, 2f, String.format("%.1f sp", letterSpacing)) { letterSpacing = it; stored.edit().putFloat("letter_spacing", it).apply() }
                    SettingSlider("左右页边距", pageMargin, 8f, 36f, "${pageMargin.toInt()} dp") { pageMargin = it; stored.edit().putFloat("page_margin", it).apply() }
                    Text("字体", color = fg, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("默认", "宋体").forEach { choice -> FilterChip(selected = fontChoice == choice, onClick = { fontChoice = choice; stored.edit().putString("font_family", choice).apply() }, label = { Text(choice) }) }
                    }
                    Text("阅读背景", color = fg, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        listOf("米白", "纯白", "护眼绿", "羊皮纸", "深灰").forEach { choice ->
                            FilterChip(selected = pageColor == choice, onClick = { pageColor = choice; stored.edit().putString("page_color", choice).apply() }, label = { Text(choice, fontSize = 11.sp) })
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("夜间模式", color = fg, modifier = Modifier.weight(1f))
                        Switch(checked = dark, onCheckedChange = { dark = it; stored.edit().putBoolean("dark", it).apply() })
                    }
                    Text("设置会自动保存，下次打开继续使用。", color = fg.copy(alpha = .6f), fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp))
                }
            } else if (selected != null) {
                val book = selected!!
                val savedPosition = stored.getInt("progress_${book.id}", 0)
                val scroll = rememberScrollState(savedPosition)
                LaunchedEffect(book.id, scroll.value) {
                    stored.edit().putInt("progress_${book.id}", scroll.value).apply()
                }
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selected = null }) { Icon(Icons.Default.ArrowBack, "返回", tint = fg) }
                        Column(Modifier.weight(1f)) {
                            Text(book.title, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(book.author, color = fg.copy(alpha = .6f), fontSize = 11.sp)
                        }
                        IconButton(onClick = {
                            bookmarked = !bookmarked
                            stored.edit().putInt("bookmark_${book.id}", if (bookmarked) scroll.value else -1).apply()
                        }) { Icon(if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "书签", tint = fg) }
                        IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Default.Settings, "阅读设置", tint = fg) }
                        IconButton(onClick = { favorites = if (book.id in favorites) favorites - book.id else favorites + book.id; stored.edit().putStringSet("favorites", favorites).apply() }) {
                            Icon(if (book.id in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "收藏", tint = Color(0xFFB66B65))
                        }
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (error.isNotBlank()) Text(error, color = Color(0xFFB65D55), modifier = Modifier.padding(horizontal = 18.dp))
                    if (!loading && readerText.isNotBlank()) {
                        val paragraphs = readerText.replace("\r", "").split(Regex("\\n\\s*\\n"))
                        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = pageMargin.dp, vertical = 10.dp)) {
                            paragraphs.forEachIndexed { index, paragraph ->
                                Text(paragraph.trim(), color = fg, fontSize = fontSize.sp, lineHeight = (fontSize * lineSpacing).sp,
                                    letterSpacing = letterSpacing.sp, fontFamily = fontFamily,
                                    modifier = Modifier.padding(bottom = (paragraphSpacing * fontSize).dp))
                            }
                        }
                    } else if (!loading) {
                        Text("暂无正文", color = fg, modifier = Modifier.padding(20.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("阅读进度", color = fg.copy(alpha = .65f), fontSize = 11.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(if (scroll.maxValue > 0) "${(scroll.value * 100 / scroll.maxValue).coerceIn(0, 100)}%" else "0%", color = fg, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { scroll.animateScrollTo(0) } }) { Text("回到顶部") }
                        TextButton(onClick = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } }) { Text("到底部") }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                    Spacer(Modifier.height(22.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("简阅", color = fg, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Text("清爽阅读 · 无广告 · 无弹窗", color = fg.copy(alpha = .6f), fontSize = 13.sp)
                        }
                        IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Default.Settings, "阅读设置", tint = fg) }
                        IconButton(onClick = { dark = !dark; stored.edit().putBoolean("dark", dark).apply() }) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "切换夜间模式", tint = fg) }
                    }
                    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("搜索公版小说书名或作者") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (query.isBlank()) { error = "请先输入书名或作者"; return@Button }
                            loading = true; error = ""; results = emptyList()
                            scope.launch { try { results = searchPublicBooks(query.trim()); if (results.isEmpty()) error = "没有找到可在线阅读的公版作品，请换个关键词" } catch (e: Exception) { error = "搜索失败，请检查网络后重试" } finally { loading = false } }
                        }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(5.dp)); Text("联网搜索") }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("text/plain", "application/epub+zip", "application/octet-stream")) }) {
                            Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(4.dp)); Text("导入书籍")
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !showFavorites, onClick = { showFavorites = false }, label = { Text("书架") })
                        FilterChip(selected = showFavorites, onClick = { showFavorites = true }, label = { Text("我的收藏") })
                        Text("${imported.size} 本本地书", color = fg.copy(alpha = .6f), fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterVertically))
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (error.isNotBlank()) Text(error, color = if (error.startsWith("已导入")) Color(0xFF43845A) else Color(0xFFB65D55), fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                    val allBooks = (sampleBooks + imported + results).distinctBy { it.id }
                    val shown = if (showFavorites) allBooks.filter { it.id in favorites } else if (results.isNotEmpty()) results else sampleBooks + imported
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                        items(shown, key = { it.id }) { book ->
                            Card(Modifier.fillMaxWidth().clickable {
                                selected = book; readerText = book.content.orEmpty(); error = ""
                                if (book.downloadUrl != null) { loading = true; scope.launch { try { readerText = downloadBook(book) } catch (_: Exception) { error = "正文加载失败，请检查网络后重试" } finally { loading = false } } }
                            }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xFF24272D) else Color.White)) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(48.dp, 64.dp).background(Color(0xFFE9DDC8), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.MenuBook, null, tint = Color(0xFF725A3A)) }
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(book.title, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                        Text(book.author, color = fg.copy(alpha = .65f), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                                        Text(book.summary, color = fg.copy(alpha = .65f), fontSize = 11.sp, maxLines = 2, modifier = Modifier.padding(top = 5.dp))
                                    }
                                    IconButton(onClick = { favorites = if (book.id in favorites) favorites - book.id else favorites + book.id; stored.edit().putStringSet("favorites", favorites).apply() }) {
                                        Icon(if (book.id in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "收藏", tint = Color(0xFFB66B65))
                                    }
                                    if (book.id.startsWith("import-")) IconButton(onClick = { imported = imported.filterNot { it.id == book.id }; saveImported(context, imported) }) { Text("删除", color = fg, fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                    Text("支持本地 TXT / EPUB；在线书源为古登堡计划公版作品。", color = fg.copy(alpha = .45f), fontSize = 10.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingSlider(title: String, value: Float, min: Float, max: Float, valueText: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), fontSize = 14.sp)
            Text(valueText, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = min..max)
    }
}
