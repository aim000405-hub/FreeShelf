package com.freeshelf.jianyue

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Book(val title: String, val author: String, val intro: String, val content: String)
private val books = listOf(
    Book("西游记", "吴承恩", "中国古典四大名著之一，讲述师徒四人西天取经的故事。", "诗曰：混沌未分天地乱，茫茫渺渺无人见。\n\n自开辟以来，天地渐分，万物生长。花果山上有一块仙石，内育仙胎……\n\n欲知后事如何，且听下回分解。"),
    Book("水浒传", "施耐庵", "英雄豪杰聚义梁山的经典章回小说。", "话说天下大势，分久必合，合久必分。\n\n且说梁山泊英雄聚义，各有来历，各显神通……"),
    Book("三国演义", "罗贯中", "描写东汉末年至三国归晋的历史演义。", "滚滚长江东逝水，浪花淘尽英雄。\n\n话说天下大势，分久必合，合久必分……"),
    Book("红楼梦", "曹雪芹", "以贾、史、王、薛四大家族兴衰为背景。", "此开卷第一回也。作者自云：因曾历过一番梦幻之后，故将真事隐去……"),
    Book("骆驼祥子", "老舍", "描写旧北京人力车夫祥子的命运起伏。", "祥子是乡下人，口齿没有城里人那么灵便……"),
    Book("傲慢与偏见", "简·奥斯汀", "英国作家简·奥斯汀的经典爱情小说。", "凡是有财产的单身汉，必定需要娶一位太太，这已经成了一条举世公认的真理。")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JianYueApp() }
    }
}

@Composable
fun JianYueApp() {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Book?>(null) }
    var favorites by remember { mutableStateOf(setOf<String>()) }
    var showFavorites by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(false) }
    var fontSize by remember { mutableFloatStateOf(19f) }
    val bg = if (dark) Color(0xFF17191D) else Color(0xFFF7F5F0)
    val fg = if (dark) Color(0xFFE9E5DC) else Color(0xFF292820)
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFFC6A878), background = bg, surface = Color(0xFF24272D)) else lightColorScheme(primary = Color(0xFF80633D), background = bg, surface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            if (selected != null) {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selected = null }) { Icon(Icons.Default.ArrowBack, "返回", tint = fg) }
                        Text(selected!!.title, color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { val name = selected!!.title; favorites = if (name in favorites) favorites - name else favorites + name }) {
                            Icon(if (selected!!.title in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "收藏", tint = Color(0xFFB66B65))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("字号", color = fg)
                        Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 16f..30f, modifier = Modifier.weight(1f))
                        Text(fontSize.toInt().toString(), color = fg)
                    }
                    Text("作者：${selected!!.author}", color = fg.copy(alpha = .65f), modifier = Modifier.padding(vertical = 12.dp))
                    Text(selected!!.content, color = fg, fontSize = fontSize.sp, lineHeight = (fontSize * 1.9f).sp)
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(28.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("简阅", color = fg, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Text("让阅读，回归简单", color = fg.copy(alpha = .6f), fontSize = 13.sp)
                        }
                        IconButton(onClick = { dark = !dark }) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "切换夜间模式", tint = fg) }
                    }
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("搜索书名或作者") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp))
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(selected = !showFavorites, onClick = { showFavorites = false }, label = { Text("书架") })
                        FilterChip(selected = showFavorites, onClick = { showFavorites = true }, label = { Text("我的收藏") })
                    }
                    val shown = books.filter { (it.title.contains(query, true) || it.author.contains(query, true)) && (!showFavorites || it.title in favorites) }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(shown) { book ->
                            Card(Modifier.fillMaxWidth().clickable { selected = book }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xFF24272D) else Color.White)) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(54.dp, 70.dp).background(Color(0xFFE9DDC8), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                        Text(book.title.take(2), color = Color(0xFF725A3A), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    }
                                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                        Text(book.title, color = fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                        Text(book.author, color = fg.copy(alpha = .65f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                                        Text(book.intro, color = fg.copy(alpha = .65f), fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 7.dp))
                                    }
                                    if (book.title in favorites) Icon(Icons.Default.Favorite, null, tint = Color(0xFFB66B65))
                                }
                            }
                        }
                        if (shown.isEmpty()) item { Text("没有找到相关书籍", color = fg.copy(alpha = .6f), modifier = Modifier.padding(24.dp)) }
                    }
                    Text("当前为内置演示书目；在线书库接入将在后续版本完善。", color = fg.copy(alpha = .45f), fontSize = 11.sp, modifier = Modifier.padding(vertical = 10.dp))
                }
            }
        }
    }
}
