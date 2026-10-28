package kr.home.gongsiping.data

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 외부 라이브러리 없이 만드는 아주 작은 .xlsx 작성기.
 * 엑셀·구글 시트·한셀에서 열린다. (글자는 inlineStr, 숫자는 숫자 칸)
 */
object Xlsx {

    enum class Style(val id: Int) { TEXT(0), HEADER(1), WON(2), PCT(3), AREA(4), TITLE(5) }

    sealed interface Cell
    data class Txt(val v: String, val style: Style = Style.TEXT) : Cell
    data class Num(val v: Double, val style: Style = Style.WON) : Cell
    data object Empty : Cell

    data class Sheet(
        val name: String,
        val widths: List<Double>,
        val rows: List<List<Cell>>,
        val freezeRows: Int = 0,
        val freezeCols: Int = 0,
        val filterRow: Int = -1 // 0부터, -1이면 필터 없음
    )

    fun write(out: OutputStream, sheets: List<Sheet>) {
        ZipOutputStream(out).use { z ->
            fun put(path: String, text: String) {
                z.putNextEntry(ZipEntry(path))
                z.write(text.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            put("[Content_Types].xml", contentTypes(sheets.size))
            put("_rels/.rels", ROOT_RELS)
            put("xl/workbook.xml", workbook(sheets))
            put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
    }

    private fun sheetXml(s: Sheet): String {
        val sb = StringBuilder(64 * 1024)
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        if (s.freezeRows > 0 || s.freezeCols > 0) {
            val top = col(s.freezeCols) + (s.freezeRows + 1)
            val pane = when {
                s.freezeRows > 0 && s.freezeCols > 0 -> "bottomRight"
                s.freezeRows > 0 -> "bottomLeft"
                else -> "topRight"
            }
            sb.append("<sheetViews><sheetView workbookViewId=\"0\"><pane")
            if (s.freezeCols > 0) sb.append(" xSplit=\"${s.freezeCols}\"")
            if (s.freezeRows > 0) sb.append(" ySplit=\"${s.freezeRows}\"")
            sb.append(" topLeftCell=\"$top\" activePane=\"$pane\" state=\"frozen\"/></sheetView></sheetViews>")
        }
        if (s.widths.isNotEmpty()) {
            sb.append("<cols>")
            s.widths.forEachIndexed { i, w -> sb.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>") }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        s.rows.forEachIndexed { r, row ->
            sb.append("<row r=\"${r + 1}\">")
            row.forEachIndexed { c, cell ->
                val ref = col(c) + (r + 1)
                when (cell) {
                    is Txt -> sb.append("<c r=\"$ref\" t=\"inlineStr\" s=\"${cell.style.id}\"><is><t xml:space=\"preserve\">")
                        .append(esc(cell.v)).append("</t></is></c>")
                    is Num -> if (cell.v.isNaN() || cell.v.isInfinite()) Unit
                    else sb.append("<c r=\"$ref\" s=\"${cell.style.id}\"><v>").append(num(cell.v)).append("</v></c>")
                    Empty -> Unit
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")
        if (s.filterRow >= 0 && s.rows.size > s.filterRow) {
            val w = s.rows[s.filterRow].size
            sb.append("<autoFilter ref=\"A${s.filterRow + 1}:${col(w - 1)}${s.rows.size}\"/>")
        }
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun num(v: Double): String =
        if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()

    fun col(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) {
            val m = (n - 1) % 26
            sb.insert(0, ('A' + m))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun esc(s: String): String = buildString(s.length) {
        for (ch in s) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> if (ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r') append(' ') else append(ch)
        }
    }

    private fun sheetName(n: String): String =
        n.replace(Regex("""[\[\]:*?/\\]"""), " ").take(31).ifBlank { "Sheet" }

    private fun contentTypes(n: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..n) append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        append("</Types>")
    }

    private fun workbook(sheets: List<Sheet>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { i, s ->
            append("<sheet name=\"${esc(sheetName(s.name))}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>")
        }
        append("</sheets>")
        val defs = sheets.withIndex().filter { (_, s) -> s.filterRow >= 0 && s.rows.size > s.filterRow }
        if (defs.isNotEmpty()) {
            append("<definedNames>")
            defs.forEach { (i, s) ->
                val w = s.rows[s.filterRow].size
                append("<definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"$i\" hidden=\"1\">")
                append("'${esc(sheetName(s.name)).replace("'", "''")}'!\$A\$${s.filterRow + 1}:\$${col(w - 1)}\$${s.rows.size}")
                append("</definedName>")
            }
            append("</definedNames>")
        }
        append("</workbook>")
    }

    private fun workbookRels(n: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..n) append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        append("""<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private const val ROOT_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    // 0 기본, 1 머리글(굵게·옅은 회색), 2 #,##0, 3 0.0, 4 0.00, 5 제목(굵게 크게)
    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="3"><numFmt numFmtId="164" formatCode="#,##0"/><numFmt numFmtId="165" formatCode="0.0"/><numFmt numFmtId="166" formatCode="0.00"/></numFmts><fonts count="3"><font><sz val="11"/><name val="맑은 고딕"/></font><font><b/><sz val="11"/><name val="맑은 고딕"/></font><font><b/><sz val="14"/><name val="맑은 고딕"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFEAE6DC"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left/><right/><top/><bottom style="thin"><color rgb="FF141414"/></bottom><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="6"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="166" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
