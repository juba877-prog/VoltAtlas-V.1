package com.example.pdf

interface ReportSectionProvider {
    val sectionName: String
    fun render(renderer: PdfDocumentRenderer, snapshot: ProjectReportSnapshot)
}
