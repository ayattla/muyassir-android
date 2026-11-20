package com.muyassir.app.engine

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Minimal, dependency-free Office Open XML writers and readers (DOCX, XLSX, PPTX).
 * Writers produce files that open in Microsoft Office, Google Docs/Sheets/Slides, WPS and LibreOffice.
 * Readers extract text and table cells; layout (fonts, images, positions) is not reproduced.
 */
object Office {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it == '\t' || it == '\n' || it == '\r' || it.code >= 0x20 }
    fun unesc(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }.replace("&amp;", "&")

    private fun hasArabic(s: String) = s.any { it in '؀'..'ۿ' || it in 'ݐ'..'ݿ' || it in 'ﭐ'..'﻿' }

    private fun zip(out: File, entries: Map<String, String>, binary: Map<String, ByteArray> = emptyMap()) {
        ZipOutputStream(FileOutputStream(out)).use { z ->
            entries.forEach { (k, v) -> z.putNextEntry(ZipEntry(k)); z.write(v.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            binary.forEach { (k, v) -> z.putNextEntry(ZipEntry(k)); z.write(v); z.closeEntry() }
        }
    }

    private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    // ======================= DOCX =======================
    sealed class Para {
        data class Text(val text: String, val heading: Int = 0, val bold: Boolean = false) : Para()
        data class Table(val rows: List<List<String>>) : Para()
        object PageBreak : Para()
    }

    fun writeDocx(out: File, paras: List<Para>) {
        val body = StringBuilder()
        for (p in paras) when (p) {
            is Para.Text -> {
                val rtl = hasArabic(p.text)
                val size = when (p.heading) { 1 -> 36; 2 -> 30; 3 -> 26; else -> 24 }
                val b = p.bold || p.heading > 0
                body.append("<w:p><w:pPr>").append(if (rtl) "<w:bidi/><w:jc w:val=\"right\"/>" else "")
                    .append("<w:spacing w:after=\"120\"/></w:pPr>")
                p.text.split('\n').forEachIndexed { i, line ->
                    if (i > 0) body.append("<w:r><w:br/></w:r>")
                    body.append("<w:r><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:cs=\"Arial\"/>")
                        .append(if (b) "<w:b/><w:bCs/>" else "").append(if (rtl) "<w:rtl/>" else "")
                        .append("<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/></w:rPr>")
                        .append("<w:t xml:space=\"preserve\">").append(esc(line)).append("</w:t></w:r>")
                }
                body.append("</w:p>")
            }
            is Para.Table -> {
                val rtl = p.rows.flatten().any { hasArabic(it) }
                body.append("<w:tbl><w:tblPr>").append(if (rtl) "<w:bidiVisual/>" else "")
                    .append("<w:tblW w:w=\"5000\" w:type=\"pct\"/><w:tblBorders>")
                listOf("top", "left", "bottom", "right", "insideH", "insideV").forEach { body.append("<w:$it w:val=\"single\" w:sz=\"4\" w:color=\"BFC6D9\"/>") }
                body.append("</w:tblBorders></w:tblPr>")
                for (row in p.rows) {
                    body.append("<w:tr>")
                    for (c in row) body.append("<w:tc><w:p><w:pPr>").append(if (hasArabic(c)) "<w:bidi/>" else "")
                        .append("</w:pPr><w:r><w:rPr>").append(if (hasArabic(c)) "<w:rtl/>" else "")
                        .append("</w:rPr><w:t xml:space=\"preserve\">").append(esc(c)).append("</w:t></w:r></w:p></w:tc>")
                    body.append("</w:tr>")
                }
                body.append("</w:tbl><w:p/>")
            }
            Para.PageBreak -> body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
        }
        zip(out, mapOf(
            "[Content_Types].xml" to XML + """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""",
            "_rels/.rels" to XML + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$REL/officeDocument" Target="word/document.xml"/></Relationships>""",
            "word/document.xml" to XML + """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>""",
        ))
    }

    /** Paragraph texts of a .docx (tables become tab-separated lines). */
    fun readDocx(f: File): List<String> {
        val xml = ZipFile(f).use { z -> z.getEntry("word/document.xml")?.let { e -> z.getInputStream(e).readBytes().toString(Charsets.UTF_8) } } ?: return emptyList()
        val out = ArrayList<String>()
        val body = xml.substringAfter("<w:body>", xml)
        Regex("<w:tbl>.*?</w:tbl>|<w:p[ >].*?</w:p>|<w:p/>", RegexOption.DOT_MATCHES_ALL).findAll(body).forEach { m ->
            val block = m.value
            if (block.startsWith("<w:tbl")) {
                Regex("<w:tr[ >].*?</w:tr>", RegexOption.DOT_MATCHES_ALL).findAll(block).forEach { tr ->
                    out += Regex("<w:tc[ >].*?</w:tc>", RegexOption.DOT_MATCHES_ALL).findAll(tr.value).joinToString("\t") { runs(it.value) }
                }
            } else out += runs(block)
        }
        return out
    }

    private fun runs(xml: String): String {
        val sb = StringBuilder()
        Regex("<w:t(?: [^>]*)?>(.*?)</w:t>|<w:tab/>|<w:br[^>]*/>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach {
            when {
                it.value.startsWith("<w:tab") -> sb.append('\t')
                it.value.startsWith("<w:br") -> sb.append('\n')
                else -> sb.append(unesc(it.groupValues[1]))
            }
        }
        return sb.toString()
    }

    // ======================= XLSX =======================
    fun colName(i: Int): String { var n = i + 1; val sb = StringBuilder(); while (n > 0) { val r = (n - 1) % 26; sb.insert(0, 'A' + r); n = (n - 1) / 26 }; return sb.toString() }

    fun writeXlsx(out: File, sheets: List<Pair<String, List<List<String>>>>, rtl: Boolean = true) {
        val entries = LinkedHashMap<String, String>()
        val wbSheets = StringBuilder(); val wbRels = StringBuilder(); val ct = StringBuilder()
        sheets.forEachIndexed { si, (name, rows) ->
            val n = si + 1
            val sd = StringBuilder()
            rows.forEachIndexed { r, row ->
                sd.append("<row r=\"${r + 1}\">")
                row.forEachIndexed { c, v ->
                    val ref = colName(c) + (r + 1)
                    val t = v.trim()
                    val isNum = Regex("-?(0|[1-9]\\d*)(\\.\\d+)?").matches(t) && t.length < 16
                    if (isNum) sd.append("<c r=\"$ref\"><v>$t</v></c>")
                    else if (v.isNotEmpty()) sd.append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v)}</t></is></c>")
                }
                sd.append("</row>")
            }
            entries["xl/worksheets/sheet$n.xml"] = XML + """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"${if (rtl) " rightToLeft=\"1\"" else ""}/></sheetViews><sheetData>$sd</sheetData></worksheet>"""
            wbSheets.append("<sheet name=\"${esc(name.take(31).replace(Regex("[\\\\/?*\\[\\]:]"), " "))}\" sheetId=\"$n\" r:id=\"rId$n\"/>")
            wbRels.append("<Relationship Id=\"rId$n\" Type=\"$REL/worksheet\" Target=\"worksheets/sheet$n.xml\"/>")
            ct.append("<Override PartName=\"/xl/worksheets/sheet$n.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        entries["[Content_Types].xml"] = XML + """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>$ct</Types>"""
        entries["_rels/.rels"] = XML + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$REL/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
        entries["xl/workbook.xml"] = XML + """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="$REL"><sheets>$wbSheets</sheets></workbook>"""
        entries["xl/_rels/workbook.xml.rels"] = XML + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$wbRels</Relationships>"""
        zip(out, entries)
    }

    /** All sheets of an .xlsx as rows of cell strings (shared and inline strings, numbers, booleans). */
    fun readXlsx(f: File): List<Pair<String, List<List<String>>>> = ZipFile(f).use { z ->
        fun read(n: String) = z.getEntry(n)?.let { z.getInputStream(it).readBytes().toString(Charsets.UTF_8) }
        val shared = read("xl/sharedStrings.xml")?.let { s ->
            Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(s).map { si ->
                Regex("<t(?: [^>]*)?>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(si.groupValues[1]).joinToString("") { unesc(it.groupValues[1]) }
            }.toList()
        } ?: emptyList()
        val wb = read("xl/workbook.xml") ?: ""
        val rels = read("xl/_rels/workbook.xml.rels") ?: ""
        val names = Regex("<sheet [^>]*name=\"([^\"]*)\"[^>]*r:id=\"([^\"]*)\"").findAll(wb).map { unesc(it.groupValues[1]) to it.groupValues[2] }.toList()
        names.mapNotNull { (name, rid) ->
            val target = Regex("Id=\"$rid\"[^>]*Target=\"([^\"]*)\"|Target=\"([^\"]*)\"[^>]*Id=\"$rid\"").find(rels)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } } ?: return@mapNotNull null
            val path = if (target.startsWith("/")) target.drop(1) else "xl/" + target.removePrefix("./")
            val xml = read(path) ?: return@mapNotNull null
            val rows = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { row ->
                val cells = ArrayList<String>()
                Regex("<c ([^>]*?)(?:/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL).findAll(row.groupValues[1]).forEach { c ->
                    val attrs = c.groupValues[1]; val inner = c.groupValues[2]
                    val ref = Regex("r=\"([A-Z]+)\\d+\"").find(attrs)?.groupValues?.get(1)
                    if (ref != null) { val idx = ref.fold(0) { a, ch -> a * 26 + (ch - 'A' + 1) } - 1; while (cells.size < idx) cells += "" }
                    val t = Regex("t=\"([^\"]*)\"").find(attrs)?.groupValues?.get(1)
                    val v = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(inner)?.groupValues?.get(1)
                    cells += when (t) {
                        "s" -> v?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                        "inlineStr" -> Regex("<t(?: [^>]*)?>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(inner).joinToString("") { unesc(it.groupValues[1]) }
                        "b" -> if (v == "1") "TRUE" else "FALSE"
                        else -> v?.let { unesc(it).toDoubleOrNull()?.let { d -> if (d == Math.floor(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString() } ?: unesc(it) } ?: ""
                    }
                }
                cells.toList()
            }.toList()
            name to rows
        }
    }

    // ======================= PPTX =======================
    data class Slide(val title: String, val body: String = "", val imagePng: ByteArray? = null, val imgW: Int = 0, val imgH: Int = 0)

    /** 16:9 deck. Image slides fill the slide (used by PDF → PowerPoint so pages keep their exact look). */
    fun writePptx(out: File, slides: List<Slide>) {
        val cx = 12192000L; val cy = 6858000L
        val e = LinkedHashMap<String, String>(); val bin = LinkedHashMap<String, ByteArray>()
        val ct = StringBuilder(); val presRels = StringBuilder(); val sldIds = StringBuilder()
        presRels.append("<Relationship Id=\"rIdM\" Type=\"$REL/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>")
        presRels.append("<Relationship Id=\"rIdT\" Type=\"$REL/theme\" Target=\"theme/theme1.xml\"/>")
        val ns = "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"$REL\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\""
        slides.forEachIndexed { i, s ->
            val n = i + 1
            val rels = StringBuilder("<Relationship Id=\"rId1\" Type=\"$REL/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>")
            val shapes = StringBuilder()
            if (s.imagePng != null) {
                bin["ppt/media/image$n.png"] = s.imagePng
                rels.append("<Relationship Id=\"rId2\" Type=\"$REL/image\" Target=\"../media/image$n.png\"/>")
                val scale = minOf(cx.toDouble() / s.imgW, cy.toDouble() / s.imgH)
                val w = (s.imgW * scale).toLong(); val h = (s.imgH * scale).toLong()
                shapes.append("<p:pic><p:nvPicPr><p:cNvPr id=\"2\" name=\"Page $n\"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed=\"rId2\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr><a:xfrm><a:off x=\"${(cx - w) / 2}\" y=\"${(cy - h) / 2}\"/><a:ext cx=\"$w\" cy=\"$h\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>")
            } else {
                fun box(id: Int, y: Long, h: Long, text: String, size: Int, bold: Boolean): String {
                    val rtl = hasArabic(text)
                    val paras = text.split('\n').joinToString("") { line ->
                        "<a:p><a:pPr algn=\"${if (rtl) "r" else "l"}\" rtl=\"${if (rtl) 1 else 0}\"/><a:r><a:rPr lang=\"${if (rtl) "ar-SA" else "en-US"}\" sz=\"$size\" b=\"${if (bold) 1 else 0}\"/><a:t>${esc(line)}</a:t></a:r></a:p>"
                    }
                    return "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"Text $id\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x=\"457200\" y=\"$y\"/><a:ext cx=\"${cx - 914400}\" cy=\"$h\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr><p:txBody><a:bodyPr wrap=\"square\"/><a:lstStyle/>$paras</p:txBody></p:sp>"
                }
                shapes.append(box(2, 457200, 1143000, s.title, 3600, true))
                if (s.body.isNotBlank()) shapes.append(box(3, 1714500, cy - 2171700, s.body, 2000, false))
            }
            e["ppt/slides/slide$n.xml"] = XML + "<p:sld $ns><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>$shapes</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"
            e["ppt/slides/_rels/slide$n.xml.rels"] = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">$rels</Relationships>"
            ct.append("<Override PartName=\"/ppt/slides/slide$n.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>")
            presRels.append("<Relationship Id=\"rId$n\" Type=\"$REL/slide\" Target=\"slides/slide$n.xml\"/>")
            sldIds.append("<p:sldId id=\"${255 + n}\" r:id=\"rId$n\"/>")
        }
        val emptyTree = "<p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>"
        e["ppt/slideMasters/slideMaster1.xml"] = XML + "<p:sldMaster $ns>$emptyTree<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/><p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst></p:sldMaster>"
        e["ppt/slideMasters/_rels/slideMaster1.xml.rels"] = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"$REL/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/><Relationship Id=\"rId2\" Type=\"$REL/theme\" Target=\"../theme/theme1.xml\"/></Relationships>"
        e["ppt/slideLayouts/slideLayout1.xml"] = XML + "<p:sldLayout $ns type=\"blank\">$emptyTree<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"
        e["ppt/slideLayouts/_rels/slideLayout1.xml.rels"] = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"$REL/slideMaster\" Target=\"../slideMasters/slideMaster1.xml\"/></Relationships>"
        e["ppt/theme/theme1.xml"] = XML + THEME
        e["ppt/presentation.xml"] = XML + "<p:presentation $ns><p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rIdM\"/></p:sldMasterIdLst><p:sldIdLst>$sldIds</p:sldIdLst><p:sldSz cx=\"$cx\" cy=\"$cy\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>"
        e["ppt/_rels/presentation.xml.rels"] = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">$presRels</Relationships>"
        e["_rels/.rels"] = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"$REL/officeDocument\" Target=\"ppt/presentation.xml\"/></Relationships>"
        e["[Content_Types].xml"] = XML + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"png\" ContentType=\"image/png\"/><Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/><Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/><Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/><Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>$ct</Types>"
        zip(out, e, bin)
    }

    /** Text of each slide, in slide order. */
    fun readPptx(f: File): List<String> = ZipFile(f).use { z ->
        z.entries().toList().map { it.name }.filter { Regex("ppt/slides/slide\\d+\\.xml").matches(it) }
            .sortedBy { Regex("\\d+").find(it.substringAfterLast("slide"))!!.value.toInt() }
            .map { name ->
                val xml = z.getInputStream(z.getEntry(name)).readBytes().toString(Charsets.UTF_8)
                Regex("<a:p>(.*?)</a:p>|<a:p [^>]*>(.*?)</a:p>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { p ->
                    Regex("<a:t>(.*?)</a:t>", RegexOption.DOT_MATCHES_ALL).findAll(p.value).joinToString("") { unesc(it.groupValues[1]) }
                }.filter { it.isNotBlank() }.joinToString("\n")
            }
    }

    private const val THEME = """<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="Muyassir"><a:themeElements><a:clrScheme name="Muyassir"><a:dk1><a:srgbClr val="101935"/></a:dk1><a:lt1><a:srgbClr val="FFFFFF"/></a:lt1><a:dk2><a:srgbClr val="1F2A4D"/></a:dk2><a:lt2><a:srgbClr val="F3F5FA"/></a:lt2><a:accent1><a:srgbClr val="2F4BFF"/></a:accent1><a:accent2><a:srgbClr val="12C29A"/></a:accent2><a:accent3><a:srgbClr val="FFC23D"/></a:accent3><a:accent4><a:srgbClr val="7A5CFF"/></a:accent4><a:accent5><a:srgbClr val="FF6B8A"/></a:accent5><a:accent6><a:srgbClr val="23B5E8"/></a:accent6><a:hlink><a:srgbClr val="2F4BFF"/></a:hlink><a:folHlink><a:srgbClr val="7A5CFF"/></a:folHlink></a:clrScheme><a:fontScheme name="Muyassir"><a:majorFont><a:latin typeface="Arial"/><a:ea typeface=""/><a:cs typeface="Arial"/></a:majorFont><a:minorFont><a:latin typeface="Arial"/><a:ea typeface=""/><a:cs typeface="Arial"/></a:minorFont></a:fontScheme><a:fmtScheme name="Muyassir"><a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst><a:lnStyleLst><a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="12700"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="19050"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>"""
}
