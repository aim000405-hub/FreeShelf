package com.freeshelf.jianyue

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
        setRequestProperty("User-Agent", "JianYue-Reader/1.1")
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
                add(
                    Novel(
                        id = "gutenberg-${item.optInt("id")}",
                        title = item.optString("title", "未命名作品"),
                        author = author.ifBlank { "未知作者" },
                        summary = "古登堡计划公版电子书 · 可在线阅读",
                        downloadUrl = textUrl
                    )
                )
            }
        }
    } finally {
        connection.disconnect()
    }
}

private suspend fun downloadBook(book: Novel): String = withContext(Dispatchers.IO) {
    val address = book.downloadUrl ?: return@withContext book.content.orEmpty()
    val connection = (URL(address).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 30000
        setRequestProperty("User-Agent", "JianYue-Reader/1.1")
    }
    try {
        var text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val startMarkers = listOf("*** START OF THE PROJECT GUTENBERG", "*** START OF THIS PROJECT GUTENBERG")
        val start = startMarkers.map { text.indexOf(it, ignoreCase = true) }.filter { it >= 0 }.minOrNull()
        if (start != null) {
            val lineEnd = text.indexOf('\n', start)
            if (lineEnd >= 0) text = text.substring(lineEnd + 1)
        }
        val endMarkers = listOf("*** END OF THE PROJECT GUTENBERG", "*** END OF THIS PROJECT GUTENBERG")
        val end = endMarkers.map { text.indexOf(it, ignoreCase = true) }.filter { it >= 0 }.minOrNull()
        if (end != null) text = text.substring(0, end)
        text.trim()
    } finally {
        connection.disconnect()
    }
}

@Composable
fun JianYueApp() {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Novel>>(emptyList()) }
    var selected by remember { mutableStateOf<Novel?>(null) }
    var readerText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(setOf<String>()) }
    var showFavorites by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(false) }
    var fontSize by remember { mutableFloatStateOf(19f) }
    val scope = rememberCoroutineScope()
    val bg = if (dark) Color(0xFF17191D) else Color(0xFFF7F5F0)
    val fg = if (dark) Color(0xFFE9E5DC) else Color(0xFF292820)

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFFC6A878), background = bg, surface = Color(0xFF24272D)) else lightColorScheme(primary = Color(0xFF80633D), background = bg, surface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            if (selected != null) {
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                        IconButton(onClick = { selected = null; readerText = "" }) { Icon(Icons.Default.ArrowBack, "返回", tint = fg) }
                        Column(Modifier.weight(1f)) {
                            Text(selected!!.title, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(selected!!.author, color = fg.copy(alpha = .6f), fontSize = 12.sp)
                        }
                        IconButton(onClick = { val id = selected!!.id; favorites = if (id in favorites) favorites - id else favorites + id }) {
                            Icon(if (selected!!.id in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "收藏", tint = Color(0xFFB66B65))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("字号", color = fg, fontSize = 12.sp)
                        Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 16f..30f, modifier = Modifier.weight(1f))
                        Text(fontSize.toInt().toString(), color = fg)
                    }
                    if (loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("正在加载正文…", color = fg, modifier = Modifier.padding(12.dp))
                    } else if (error.isNotBlank()) {
                        Text(error, color = Color(0xFFB65D55), modifier = Modifier.padding(12.dp))
                        Button(onClick = { val book = selected!!; loading = true; error = ""; scope.launch { try { readerText = downloadBook(book) } catch (e: Exception) { error = "加载失败：${e.message ?: "网络连接异常"}" } finally { loading = false } } }) { Text("重试") }
                    } else {
                        Text(readerText.ifBlank { "暂无正文" }, color = fg, fontSize = fontSize.sp, lineHeight = (fontSize * 1.85f).sp, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("简阅", color = fg, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Text("清爽阅读 · 无广告 · 无弹窗", color = fg.copy(alpha = .6f), fontSize = 13.sp)
                        }
                        IconButton(onClick = { dark = !dark }) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "切换夜间模式", tint = fg) }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, placeholder = { Text("搜索公版小说书名或作者") },
                        leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp)
                    )
                    Button(
                        onClick = {
                            if (query.isBlank()) { error = "请先输入书名或作者"; return@Button }
                            loading = true; error = ""; results = emptyList()
                            scope.launch {
                                try { results = searchPublicBooks(query.trim()); if (results.isEmpty()) error = "没有找到可在线阅读的公版作品，请换个关键词" }
                                catch (e: Exception) { error = "搜索失败，请检查网络后重试：${e.message ?: "连接异常"}" }
                                finally { loading = false }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text("联网搜索小说") }
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(selected = !showFavorites, onClick = { showFavorites = false }, label = { Text("书架 / 搜索结果") })
                        FilterChip(selected = showFavorites, onClick = { showFavorites = true }, label = { Text("我的收藏") })
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (error.isNotBlank()) Text(error, color = Color(0xFFB65D55), fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                    val shown = if (showFavorites) (sampleBooks + results).distinctBy { it.id }.filter { it.id in favorites } else if (results.isNotEmpty()) results else sampleBooks
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                        items(shown, key = { it.id }) { book ->
                            Card(Modifier.fillMaxWidth().clickable {
                                selected = book; readerText = book.content.orEmpty(); error = ""
                                if (book.downloadUrl != null) {
                                    loading = true
                                    scope.launch { try { readerText = downloadBook(book) } catch (e: Exception) { error = "正文加载失败，请检查网络后重试" } finally { loading = false } }
                                }
                            }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xFF24272D) else Color.White)) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(48.dp, 64.dp).background(Color(0xFFE9DDC8), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                                        Text(book.title.take(2), color = Color(0xFF725A3A), fontWeight = FontWeight.Bold)
                                    }
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(book.title, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                        Text(book.author, color = fg.copy(alpha = .65f), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                                        Text(book.summary, color = fg.copy(alpha = .65f), fontSize = 11.sp, maxLines = 2, modifier = Modifier.padding(top = 5.dp))
                                    }
                                    IconButton(onClick = { favorites = if (book.id in favorites) favorites - book.id else favorites + book.id }) {
                                        Icon(if (book.id in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "收藏", tint = Color(0xFFB66B65))
                                    }
                                }
                            }
                        }
                    }
                    Text("在线书源：古登堡计划（公版作品）；不接入广告 SDK，不显示广告弹窗。", color = fg.copy(alpha = .45f), fontSize = 10.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}
