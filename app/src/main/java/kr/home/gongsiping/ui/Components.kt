package kr.home.gongsiping.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.home.gongsiping.data.Fmt
import kr.home.gongsiping.data.YearPrice

@Composable
fun T(
    text: String,
    style: TextStyle = Type.body,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.ink,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text, style = style, color = color, modifier = modifier,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier, strong: Boolean = false) {
    val p = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .height(if (strong) 2.dp else 1.dp)
            .background(if (strong) p.ink else p.line)
    )
}

/** 형광펜으로 그은 듯한 강조 (글자 아래쪽 절반만 라임) */
@Composable
fun Marker(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text(
        text = text,
        style = style,
        color = if (p.dark) p.lime else p.ink,
        modifier = modifier.drawBehind {
            if (!p.dark) {
                drawRect(
                    color = p.lime,
                    topLeft = Offset(-4f, size.height * 0.52f),
                    size = Size(size.width + 8f, size.height * 0.40f)
                )
            }
        }
    )
}

/** 상승은 라임 꽉 찬 태그, 하락·보합은 테두리만 */
@Composable
fun RateTag(rate: Double, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val up = !rate.isNaN() && rate > 0
    val m = if (up) modifier.background(p.lime) else modifier.border(1.dp, p.sub)
    Box(m.padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(Fmt.rate(rate), style = Type.label, color = if (up) p.onLime else p.sub)
    }
}

@Composable
fun InkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .background(if (enabled) p.ink else p.line)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = Type.bodyBold, color = p.bg)
    }
}

@Composable
fun LinkText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color? = null) {
    val p = LocalPalette.current
    Text(
        text = text,
        style = Type.body.copy(textDecoration = TextDecoration.Underline),
        color = color ?: p.ink,
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 6.dp)
    )
}

/** "02  10년 흐름" 같은 섹션 머리 */
@Composable
fun SectionHead(no: String, title: String, modifier: Modifier = Modifier, right: (@Composable () -> Unit)? = null) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth()) {
        Hairline(strong = true)
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(no, style = Type.label, color = p.sub, modifier = Modifier.width(30.dp))
            Text(title, style = Type.title, color = p.ink, modifier = Modifier.weight(1f))
            right?.invoke()
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier.border(1.dp, p.ink)) {
        options.forEachIndexed { i, s ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(if (on) p.ink else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(s, style = Type.small.copy(fontWeight = Type.bodyBold.fontWeight), color = if (on) p.bg else p.ink)
            }
        }
    }
}

/** 밑줄 하나짜리 입력칸 */
@Composable
fun UnderlineField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Type.title,
    onDone: (() -> Unit)? = null
) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            if (value.isEmpty()) Text(hint, style = style, color = p.sub)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = style.copy(color = p.ink),
                singleLine = true,
                cursorBrush = SolidColor(p.ink),
                keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Search else ImeAction.Done),
                keyboardActions = KeyboardActions(onSearch = { onDone?.invoke() }, onDone = { onDone?.invoke() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Hairline(strong = true)
    }
}

/** 축·격자 없는 꺾은선. 마지막 점만 라임으로 */
@Composable
fun LineChart(points: List<YearPrice>, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    if (points.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("아직 한 해 치 데이터뿐이에요", style = Type.small, color = p.sub)
        }
        return
    }
    val min = points.minOf { it.price }.toFloat()
    val max = points.maxOf { it.price }.toFloat()
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val padX = 10.dp.toPx()
            val padY = 14.dp.toPx()
            val w = size.width - padX * 2
            val h = size.height - padY * 2
            val range = (max - min).takeIf { it > 0f } ?: 1f
            fun xy(i: Int, v: Long) = Offset(
                padX + w * i / (points.size - 1),
                padY + h * (1f - (v - min) / range)
            )
            // 바닥 기준선
            drawLine(p.line, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1.dp.toPx())
            val path = Path()
            points.forEachIndexed { i, yp ->
                val o = xy(i, yp.price)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, p.ink, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            // 최고점 표시(작은 눈금)
            val maxI = points.indexOfFirst { it.price.toFloat() == max }
            val mo = xy(maxI, points[maxI].price)
            drawLine(p.sub, Offset(mo.x, mo.y - 8.dp.toPx()), Offset(mo.x, mo.y - 3.dp.toPx()), 1.dp.toPx())
            // 마지막 점
            val last = xy(points.size - 1, points.last().price)
            drawCircle(p.lime, 7.dp.toPx(), last)
            drawCircle(p.ink, 7.dp.toPx(), last, style = Stroke(2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${points.first().year}", style = Type.label, color = p.sub)
            Text("최고 ${Fmt.won(max.toLong())}", style = Type.label, color = p.sub)
            Text("${points.last().year}", style = Type.label, color = p.ink)
        }
    }
}

/** 가로 막대 한 줄 (주변 비교) */
@Composable
fun BarRow(
    rank: Int, name: String, value: String, ratio: Float, highlight: Boolean,
    sub: String, rate: Double
) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("%02d".format(rank), style = Type.label, color = p.sub, modifier = Modifier.width(30.dp))
            Text(
                name + if (highlight) "  ← 내 집" else "",
                style = if (highlight) Type.bodyBold else Type.body,
                color = p.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(value, style = Type.bodyBold, color = p.ink)
        }
        Row(Modifier.padding(start = 30.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(ratio.coerceIn(0.03f, 1f))
                        .height(8.dp)
                        .background(if (highlight) p.lime else p.ink.copy(alpha = if (p.dark) 0.55f else 0.85f))
                        .then(if (highlight && !p.dark) Modifier.border(1.dp, p.ink) else Modifier)
                )
            }
        }
        Row(Modifier.padding(start = 30.dp, top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(sub, style = Type.small, color = p.sub, modifier = Modifier.weight(1f))
            if (!rate.isNaN()) RateTag(rate)
        }
    }
}
