package com.muyassir.app

import com.muyassir.app.engine.Calc
import com.muyassir.app.engine.Expr
import com.muyassir.app.engine.Office
import com.muyassir.app.engine.TextTools
import com.muyassir.app.engine.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CalcTest {
    private fun near(e: Double, a: Double, d: Double = 0.01) = assertEquals(e, a, d)

    @Test fun vatAddAndExtract() {
        near(115.0, Calc.vat(100.0, inclusive = false).total)
        near(100.0, Calc.vat(115.0, inclusive = true).net)
        near(15.0, Calc.vat(115.0, inclusive = true).vat)
    }

    @Test fun gosiLegacySaudi() {
        val g = Calc.gosi(8000.0, 2000.0, saudi = true)
        near(975.0, g.employee); near(1175.0, g.employer)
    }

    @Test fun gosiCapAndForeign() {
        near(45000 * 0.0975, Calc.gosi(60000.0, 10000.0, true).employee)
        near(0.0, Calc.gosi(5000.0, 0.0, false).employee); near(100.0, Calc.gosi(5000.0, 0.0, false).employer)
        near(10000 * 0.1025, Calc.gosi(10000.0, 0.0, true, Calc.GosiSystem.NEW, 9.5).employee)
    }

    @Test fun endOfService() {
        // 10 years, termination: 5 × ½ + 5 × 1 = 7.5 months
        near(75000.0, Calc.endOfService(10000.0, 10, 0, 0, Calc.Leaving.TERMINATION).payable)
        // 3 years resignation: full = 1.5 months → one third
        near(5000.0, Calc.endOfService(10000.0, 3, 0, 0, Calc.Leaving.RESIGNATION).payable)
        near(0.0, Calc.endOfService(10000.0, 1, 11, 0, Calc.Leaving.RESIGNATION).payable)
        // 7 years resignation: (2.5 + 2) months × 2/3 = 3 months
        near(30000.0, Calc.endOfService(10000.0, 7, 0, 0, Calc.Leaving.RESIGNATION).payable)
    }

    @Test fun leave() {
        assertEquals(21, Calc.leave(2.0, 12.0, 0.0, 0.0).entitlement)
        assertEquals(30, Calc.leave(6.0, 6.0, 5.0, 0.0).entitlement)
        near(10.0, Calc.leave(6.0, 6.0, 5.0, 0.0).remaining)
    }

    @Test fun loans() {
        val f = Calc.flatLoan(100000.0, 3.0, 60)
        near(15000.0, f.profit); near(1916.67, f.monthly)
        assertTrue(f.apr > 5.5 && f.apr < 5.8) // flat 3% ≈ 5.6% APR over 5 years
        near(1887.12, Calc.reducingLoan(100000.0, 5.0, 60).monthly)
    }

    @Test fun discountsStack() { near(28.0, Calc.discount(100.0, 20.0, 10.0).effectivePct) }

    @Test fun gpa() {
        val (sem, cum) = Calc.gpa(listOf(Calc.Course(5.0, 3.0), Calc.Course(4.0, 3.0)), 4.0, 30.0)
        near(4.5, sem); near((4.5 * 6 + 4.0 * 30) / 36, cum)
    }

    @Test fun expressions() {
        near(14.0, Expr("2+3*4").eval()); near(20.0, Expr("(2+3)*4").eval())
        near(1.0, Expr("sin(90)").eval()); near(120.0, Expr("5!").eval()); near(512.0, Expr("2^3^2").eval())
        near(-4.0, Expr("-2^2").eval()); near(0.5, Expr("2^-1").eval())
        near(0.5, Expr("50%").eval()); near(3.0, Expr("√(9)").eval())
    }

    @Test fun units() {
        val len = Units.kinds.first { it.name == "الطول" }
        near(1000.0, Units.convert(len, 1.0, len.units.first { it.name == "كيلومتر" }, len.units.first { it.name == "متر" }))
        val temp = Units.kinds.first { it.name == "الحرارة" }
        near(212.0, Units.convert(temp, 100.0, temp.units[0], temp.units[1]))
    }

    @Test fun textTools() {
        assertEquals(4, TextTools.count("مرحبا بك في مُيسّر").words)
        assertEquals(1, TextTools.dedupe("أ\nب\nأ", true, true, false).second)
        val d = TextTools.diff("a b c", "a x c", true)
        assertEquals(listOf(TextTools.Op.SAME, TextTools.Op.REMOVED, TextTools.Op.ADDED, TextTools.Op.SAME), d.map { it.op })
    }

    @Test fun officeRoundTrip() {
        val dir = createTempDirSafe()
        val docx = File(dir, "t.docx")
        Office.writeDocx(docx, listOf(Office.Para.Text("عنوان", 1), Office.Para.Text("سطر عربي & <رمز>"), Office.Para.Table(listOf(listOf("أ", "ب")))))
        val paras = Office.readDocx(docx)
        assertEquals("عنوان", paras[0]); assertEquals("سطر عربي & <رمز>", paras[1]); assertEquals("أ\tب", paras[2])

        val xlsx = File(dir, "t.xlsx")
        Office.writeXlsx(xlsx, listOf("ورقة" to listOf(listOf("الاسم", "المبلغ"), listOf("سارة", "120.5"), listOf("خالد", "007"))))
        val sheets = Office.readXlsx(xlsx)
        assertEquals("ورقة", sheets[0].first); assertEquals(listOf("سارة", "120.5"), sheets[0].second[1]); assertEquals("007", sheets[0].second[2][1])

        val pptx = File(dir, "t.pptx")
        Office.writePptx(pptx, listOf(Office.Slide("الشريحة الأولى", "نقطة 1\nنقطة 2"), Office.Slide("الثانية")))
        val slides = Office.readPptx(pptx)
        assertEquals(2, slides.size); assertTrue(slides[0].contains("نقطة 2"))
    }

    private fun createTempDirSafe(): File = File(System.getProperty("java.io.tmpdir"), "mys-${System.nanoTime()}").apply { mkdirs() }
}
