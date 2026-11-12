package com.superfit.app.ui

import com.superfit.app.domain.CoachExport

/**
 * "Share my report" on iPhone, done by Swift (ReportBridge.swift): draws the shared report layout
 * (CoachReportPdf) as a PDF on the phone and opens Apple's share sheet with it.
 */
interface IosReport {
    fun share(report: CoachExport)
}
