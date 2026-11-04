package com.danielbarrantesv.dashcam.reports

import android.content.Context
import com.danielbarrantesv.dashcam.data.model.GForceEventEntity
import com.danielbarrantesv.dashcam.data.model.GpsSampleEntity
import com.danielbarrantesv.dashcam.data.model.TripEntity
import com.danielbarrantesv.dashcam.util.FileUtils
import com.danielbarrantesv.dashcam.util.Formatters
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.draw.DashedLine
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.LineSeparator
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Insurer report payload. */
data class ReportInput(
    val trip: TripEntity,
    val events: List<GForceEventEntity>,
    val gpsSamples: List<GpsSampleEntity>,
    val policyHolderName: String,
    val policyNumber: String,
    val vehiclePlate: String
)

/**
 * Generates a formal PDF report for the insurer using iText 7.
 * Includes:
 *  - Header with brand + report title + report ID
 *  - Policy holder / vehicle / incident fields
 *  - Trip summary (distance, duration, max/avg speed, peak G)
 *  - Event log table (date/time, type, magnitude, GPS, speed)
 *  - GPS route summary (start/end coordinates, sample count)
 *  - Footer with disclaimer + page numbers
 */
@Singleton
class PdfGenerator @Inject constructor() {

    private val brandOrange = DeviceRgb(255, 106, 0)
    private val darkGray = DeviceRgb(20, 20, 25)
    private val lightGray = DeviceRgb(232, 232, 235)
    private val midGray = DeviceRgb(170, 170, 185)

    fun generate(ctx: Context, input: ReportInput): File {
        val ts = System.currentTimeMillis()
        val outFile = FileUtils.reportFile(ctx, input.trip.id, ts)
        val writer = PdfWriter(outFile)
        val pdf = PdfDocument(writer)
        val doc = Document(pdf, PageSize.A4)
        doc.setMargins(36f, 36f, 54f, 36f)

        val bold: PdfFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)
        val regular: PdfFont = PdfFontFactory.createFont(StandardFonts.HELVETICA)
        val oblique: PdfFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE)

        // ===== Header =====
        val headerTable = Table(floatArrayOf(1f, 2f))
        headerTable.setWidth(UnitValue.createPercentValue(100f))

        headerTable.addCell(
            Cell().add(
                Paragraph("DashCam")
                    .setFont(bold).setFontSize(24f).setFontColor(brandOrange)
            ).add(
                Paragraph("Reporte de trayecto")
                    .setFont(regular).setFontSize(10f).setFontColor(midGray)
            ).setBorder(Border.NO_BORDER).setPaddingTop(0f)
        )

        headerTable.addCell(
            Cell().add(
                Paragraph("No. ${input.trip.id}/$ts")
                    .setFont(regular).setFontSize(10f)
                    .setFontColor(midGray)
                    .setTextAlignment(TextAlignment.RIGHT)
            ).add(
                Paragraph("Generado: ${Formatters.dateTime(ts)}")
                    .setFont(regular).setFontSize(10f)
                    .setFontColor(midGray)
                    .setTextAlignment(TextAlignment.RIGHT)
            ).setBorder(Border.NO_BORDER).setPaddingTop(0f)
        )
        doc.add(headerTable)
        val sep = DashedLine(1f)
        sep.color = brandOrange
        doc.add(LineSeparator(sep).setMarginTop(8f).setMarginBottom(16f))

        // ===== Section: Policy holder / vehicle / incident =====
        doc.add(sectionTitle("Datos del reporte", bold))
        val data = Table(floatArrayOf(1f, 1f))
        data.setWidth(UnitValue.createPercentValue(100f))

        val incidentTs = input.events.firstOrNull()?.createdAt ?: input.trip.startedAt

        data.addCell(dataCell("Titular de la póliza", input.policyHolderName.ifBlank { "—" }, regular))
        data.addCell(dataCell("No. de póliza", input.policyNumber.ifBlank { "—" }, regular))
        data.addCell(dataCell("Placa del vehículo", input.vehiclePlate.ifBlank { "—" }, regular))
        data.addCell(dataCell("Fecha del incidente", Formatters.date(incidentTs), regular))
        data.addCell(dataCell("Hora del incidente", Formatters.time(incidentTs), regular))
        data.addCell(
            dataCell(
                "Ubicación",
                input.events.firstOrNull()?.let {
                    Formatters.coordinates(it.lat ?: input.trip.startLat ?: 0.0, it.lon ?: input.trip.startLon ?: 0.0)
                } ?: (input.trip.startLat?.let {
                    Formatters.coordinates(it, input.trip.startLon ?: 0.0)
                } ?: "—"),
                regular
            )
        )
        data.addCell(dataCell("Velocidad al momento del impacto", "${"%.0f".format(input.events.firstOrNull()?.speedKmh ?: 0f)} km/h", regular))
        data.addCell(
            dataCell(
                "Fuerzas G registradas",
                "Máx: ${"%.2f".format(input.events.maxOfOrNull { it.magnitudeG } ?: input.trip.maxGForce)} g",
                regular
            )
        )
        doc.add(data)
        doc.add(Paragraph("").setMarginBottom(12f))

        // ===== Section: Trip summary =====
        doc.add(sectionTitle("Resumen del trayecto", bold))
        val summary = Table(floatArrayOf(1f, 1f, 1f, 1f))
        summary.setWidth(UnitValue.createPercentValue(100f))
        summary.addCell(metricCell("Distancia", Formatters.distance(input.trip.distanceMeters), regular))
        summary.addCell(metricCell("Duración", Formatters.duration(input.trip.durationSeconds), regular))
        summary.addCell(metricCell("Vel. máxima", "${"%.0f".format(input.trip.maxSpeedKmh)} km/h", regular))
        summary.addCell(metricCell("Vel. promedio", "${"%.0f".format(input.trip.avgSpeedKmh)} km/h", regular))
        summary.addCell(metricCell("G máximo", "${"%.2f".format(input.trip.maxGForce)} g", regular))
        summary.addCell(metricCell("Eventos", "${input.trip.eventCount}", regular))
        summary.addCell(metricCell("Videos", "${input.trip.videoSegmentCount}", regular))
        summary.addCell(metricCell("Almacenamiento", FileUtils.formatBytes(input.trip.totalStorageBytes), regular))
        doc.add(summary)
        doc.add(Paragraph("").setMarginBottom(12f))

        // ===== Section: Event log =====
        doc.add(sectionTitle("Registro de eventos", bold))
        if (input.events.isEmpty()) {
            doc.add(
                Paragraph("No se registraron eventos de fuerza G durante el trayecto.")
                    .setFont(oblique).setFontSize(10f).setFontColor(midGray)
            )
        } else {
            val events = Table(floatArrayOf(1.4f, 1.6f, 1f, 1.2f, 1.2f, 1.6f))
            events.setWidth(UnitValue.createPercentValue(100f))
            listOf("Fecha/Hora", "Tipo", "G (g)", "Vel. (km/h)", "Coordenadas", "Notas").forEach { h ->
                events.addHeaderCell(
                    Cell().add(Paragraph(h).setFont(bold).setFontSize(9f).setFontColor(ColorConstants.WHITE))
                        .setBackgroundColor(darkGray)
                        .setBorder(Border.NO_BORDER)
                        .setPadding(6f)
                )
            }
            input.events.forEachIndexed { idx, e ->
                val bg = if (idx % 2 == 0) lightGray else ColorConstants.WHITE
                events.addCell(textCell(Formatters.dateTime(e.createdAt), regular, bg))
                events.addCell(textCell(e.type.replace("_", " "), regular, bg))
                events.addCell(textCell("%.2f".format(e.magnitudeG), regular, bg))
                events.addCell(textCell("%.0f".format(e.speedKmh ?: 0f), regular, bg))
                events.addCell(
                    textCell(
                        e.lat?.let { Formatters.coordinates(it, e.lon ?: 0.0) } ?: "—",
                        regular, bg
                    )
                )
                events.addCell(
                    textCell(
                        "X ${"%.2f".format(e.axisX)}  Y ${"%.2f".format(e.axisY)}  Z ${"%.2f".format(e.axisZ)}",
                        regular, bg
                    )
                )
            }
            doc.add(events)
        }
        doc.add(Paragraph("").setMarginBottom(12f))

        // ===== Section: GPS route summary =====
        doc.add(sectionTitle("Recorrido GPS", bold))
        val gpsTable = Table(floatArrayOf(1f, 1f, 1f))
        gpsTable.setWidth(UnitValue.createPercentValue(100f))
        gpsTable.addCell(
            metricCell(
                "Inicio",
                input.trip.startLat?.let {
                    Formatters.coordinates(it, input.trip.startLon ?: 0.0)
                } ?: "—",
                regular
            )
        )
        gpsTable.addCell(
            metricCell(
                "Fin",
                input.trip.endLat?.let {
                    Formatters.coordinates(it, input.trip.endLon ?: 0.0)
                } ?: "—",
                regular
            )
        )
        gpsTable.addCell(metricCell("Muestras GPS", "${input.gpsSamples.size}", regular))
        doc.add(gpsTable)
        doc.add(Paragraph("").setMarginBottom(20f))

        // ===== Footer / disclaimer =====
        val sep2 = DashedLine(0.5f)
        sep2.color = midGray
        doc.add(LineSeparator(sep2))
        doc.add(
            Paragraph(
                "Documento generado automáticamente por DashCam. Los datos provienen de los sensores del dispositivo móvil y se entregan sin modificación. Cualquier discrepancia con otros registros debe verificarse con la fuente original."
            )
                .setFont(oblique).setFontSize(8f).setFontColor(midGray)
                .setMarginTop(8f)
                .setTextAlignment(TextAlignment.JUSTIFIED)
        )

        doc.close()
        return outFile
    }

    private fun sectionTitle(text: String, font: PdfFont): Paragraph =
        Paragraph(text).setFont(font).setFontSize(14f).setFontColor(darkGray)
            .setMarginBottom(8f).setMarginTop(4f)

    private fun dataCell(label: String, value: String, font: PdfFont): Cell =
        Cell().add(
            Paragraph(label).setFont(font).setFontSize(8f).setFontColor(midGray)
                .setMarginBottom(2f)
        ).add(
            Paragraph(value).setFont(font).setFontSize(11f).setFontColor(darkGray)
        ).setBorder(SolidBorder(lightGray, 0.5f)).setPadding(8f)

    private fun metricCell(label: String, value: String, font: PdfFont): Cell =
        Cell().add(
            Paragraph(label).setFont(font).setFontSize(8f).setFontColor(midGray)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(2f)
        ).add(
            Paragraph(value).setFont(font).setFontSize(12f).setFontColor(darkGray)
                .setTextAlignment(TextAlignment.CENTER)
        ).setBorder(SolidBorder(lightGray, 0.5f)).setPadding(8f)

    private fun textCell(text: String, font: PdfFont, bg: com.itextpdf.kernel.colors.Color): Cell =
        Cell().add(
            Paragraph(text).setFont(font).setFontSize(8f).setFontColor(darkGray)
        ).setBackgroundColor(bg).setBorder(SolidBorder(lightGray, 0.5f)).setPadding(4f)
}
