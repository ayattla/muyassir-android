package com.muyassir.app.tools

import androidx.compose.runtime.Composable
import com.muyassir.app.core.Tools

@Composable
fun ToolHost(id: String, back: () -> Unit, open: (String) -> Unit) {
    val t = Tools.byId[id] ?: return back()
    when (id) {
        "pdf_word" -> PdfToWord(t, back); "word_pdf" -> WordToPdf(t, back)
        "pdf_excel" -> PdfToExcel(t, back); "excel_pdf" -> ExcelToPdf(t, back)
        "pdf_ppt" -> PdfToPpt(t, back); "ppt_pdf" -> PptToPdf(t, back)
        "pdf_img" -> PdfToImages(t, back); "img_pdf" -> ImagesToPdf(t, back)
        "pdf_merge" -> PdfMerge(t, back); "pdf_split" -> PdfSplit(t, back); "pdf_compress" -> PdfCompress(t, back)
        "pdf_reorder" -> PdfReorder(t, back); "pdf_delete" -> PdfDelete(t, back); "pdf_extract" -> PdfExtract(t, back); "pdf_rotate" -> PdfRotate(t, back)
        "ocr" -> OcrTool(t, back); "scan" -> ScanTool(t, back)
        "pdf_protect" -> PdfProtect(t, back); "pdf_unlock" -> PdfUnlock(t, back)
        "zip" -> ZipTool(t, back); "unzip" -> UnzipTool(t, back); "img_convert" -> ImgConvert(t, back)
        "img_compress" -> ImgCompress(t, back); "img_resize" -> ImgResize(t, back); "img_crop" -> ImgCrop(t, back)
        "img_rotate" -> ImgRotate(t, back); "img_enhance" -> ImgEnhance(t, back); "img_bg" -> ImgBackground(t, back)
        "img_batch" -> ImgBatch(t, back); "id_photo" -> IdPhoto(t, back)
        "gpa" -> GpaTool(t, back); "percent" -> PercentTool(t, back); "timetable" -> TimetableTool(t, back)
        "exams" -> ExamsTool(t, back); "homework" -> HomeworkTool(t, back); "attendance" -> AttendanceTool(t, back)
        "quiz_maker" -> QuizMaker(t, back); "worksheet" -> WorksheetTool(t, back); "flashcards" -> FlashcardsTool(t, back)
        "study_timer" -> StudyTimer(t, back); "certificate" -> CertificateTool(t, back)
        "vat" -> VatTool(t, back); "salary" -> SalaryTool(t, back); "eos" -> EosTool(t, back); "leave" -> LeaveTool(t, back)
        "finance" -> FinanceTool(t, back); "discount" -> DiscountTool(t, back); "hijri" -> HijriTool(t, back)
        "gov_dir" -> GovDirectory(t, back); "emergency" -> EmergencyTool(t, back); "documents" -> DocumentsTool(t, back)
        "notes" -> NotesTool(t, back); "tasks" -> TasksTool(t, back); "calendar" -> CalendarTool(t, back)
        "reminders" -> RemindersTool(t, back); "budget" -> BudgetTool(t, back); "expenses" -> ExpensesTool(t, back)
        "cv" -> CvTool(t, back); "invoice" -> InvoiceTool(t, back); "quote" -> QuoteTool(t, back)
        "letter" -> LetterTool(t, back); "sign_pdf" -> SignPdfTool(t, back); "text_pdf" -> TextPdfTool(t, back)
        "scientific" -> ScientificCalc(t, back); "units" -> UnitsTool(t, back); "currency" -> CurrencyTool(t, back); "area_volume" -> AreaVolume(t, back)
        "qr_make" -> QrMake(t, back); "qr_read" -> QrRead(t, back); "text_compare" -> TextCompare(t, back)
        "word_count" -> WordCount(t, back); "dedupe" -> Dedupe(t, back); "tts" -> TtsTool(t, back); "stt" -> SttTool(t, back)
        else -> back()
    }
}
