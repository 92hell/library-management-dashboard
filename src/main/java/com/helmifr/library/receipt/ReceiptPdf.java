package com.helmifr.library.receipt;

import com.helmifr.library.receipt.ReceiptContent.EventPart;
import com.helmifr.library.receipt.ReceiptService.Qr;
import com.helmifr.library.receipt.ReceiptService.ReceiptView;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * The printable receipt (A4): bilingual labels (English / Bahasa Indonesia), the loan, its history,
 * the late fee, a library stamp, the QR code to the verification page and the signature details.
 */
final class ReceiptPdf {

    private static final Color INK = new Color(0x2e, 0x2a, 0x24);
    private static final Color MUTED = new Color(0x6e, 0x66, 0x5a);
    private static final Color GREEN = new Color(0x2f, 0x5d, 0x50);
    private static final Color STAMP = new Color(0x8b, 0x2e, 0x2e);
    private static final Color RULE = new Color(0xe2, 0xd6, 0xbf);

    static final Map<String, String> STATUS = Map.ofEntries(
            Map.entry("PENDING", "Requested / Diminta"), Map.entry("WAITLISTED", "In queue / Dalam antrean"),
            Map.entry("READY", "Ready for pickup / Siap diambil"), Map.entry("ACTIVE", "On loan / Dipinjam"),
            Map.entry("RETURNED", "Returned / Dikembalikan"), Map.entry("REJECTED", "Declined / Ditolak"),
            Map.entry("CANCELLED", "Cancelled / Dibatalkan"), Map.entry("EXPIRED", "Not collected / Tidak diambil"));

    static final Map<String, String> FINE = Map.of(
            "UNPAID", "Unpaid / Belum dibayar", "PAID", "Paid / Lunas", "WAIVED", "Waived / Dihapuskan");

    static final Map<String, String> EVENT = Map.ofEntries(
            Map.entry("IMPORTED", "Earlier record / Catatan lama"), Map.entry("REQUESTED", "Requested / Diminta"),
            Map.entry("LENT", "Lent at the desk / Dipinjamkan di meja"), Map.entry("APPROVED", "Approved / Disetujui"),
            Map.entry("WAITLISTED", "Queued / Masuk antrean"), Map.entry("READY", "Set aside / Disiapkan"),
            Map.entry("COLLECTED", "Collected / Diambil"), Map.entry("RENEWED", "Renewed / Diperpanjang"),
            Map.entry("RETURNED", "Returned / Dikembalikan"), Map.entry("REJECTED", "Declined / Ditolak"),
            Map.entry("CANCELLED", "Cancelled / Dibatalkan"), Map.entry("EXPIRED", "Not collected / Tidak diambil"),
            Map.entry("EDITED", "Corrected / Dikoreksi"), Map.entry("DELETED", "Deleted / Dihapus"),
            Map.entry("FINE_CHARGED", "Late fee charged / Denda dikenakan"),
            Map.entry("FINE_PAID", "Late fee paid / Denda dibayar"),
            Map.entry("FINE_WAIVED", "Late fee waived / Denda dihapuskan"));

    private ReceiptPdf() {}

    static byte[] render(ReceiptView view, ReceiptContent c) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 48, 56);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        doc.addTitle("Receipt " + view.number());
        doc.addAuthor(c.library());
        doc.open();

        Font title = new Font(Font.HELVETICA, 17, Font.BOLD, GREEN);
        Font sub = new Font(Font.HELVETICA, 10, Font.NORMAL, MUTED);
        Font label = new Font(Font.HELVETICA, 8, Font.BOLD, MUTED);
        Font value = new Font(Font.HELVETICA, 10, Font.NORMAL, INK);
        Font small = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, MUTED);
        Font mono = new Font(Font.COURIER, 7f, Font.NORMAL, INK);

        doc.add(new Paragraph(c.library(), title));
        doc.add(new Paragraph("Loan receipt / Bukti Peminjaman", new Font(Font.HELVETICA, 12, Font.BOLD, INK)));
        Paragraph meta = new Paragraph("No. " + view.number() + "   ·   Issued / Diterbitkan " + c.issuedAt()
                + "   ·   by / oleh " + c.issuedBy(), sub);
        meta.setSpacingAfter(14);
        doc.add(meta);

        var loan = c.loan();
        PdfPTable details = new PdfPTable(new float[] {1.3f, 3f});
        details.setWidthPercentage(72);
        details.setHorizontalAlignment(Element.ALIGN_LEFT);
        row(details, "Book / Buku", loan.bookTitle(), label, value);
        row(details, "Author / Penulis", loan.authorName(), label, value);
        row(details, "Copy / Eksemplar", loan.copyBarcode(), label, value);
        row(details, "Member / Anggota", loan.memberName(), label, value);
        row(details, "Library card / Kartu", loan.libraryCardId(), label, value);
        row(details, "Status", STATUS.getOrDefault(loan.status(), loan.status()), label, value);
        row(details, "Borrowed / Dipinjam", loan.borrowDate(), label, value);
        row(details, "Due / Jatuh tempo", loan.dueDate(), label, value);
        row(details, "Returned / Kembali", loan.returnDate(), label, value);
        row(details, "Renewals / Perpanjangan", String.valueOf(loan.renewals()), label, value);
        if (c.fine() != null) {
            row(details, "Late fee / Denda", "%s %,d · %d days late · %s".formatted(c.fine().currency(), c.fine().amount(),
                    c.fine().daysLate(), FINE.getOrDefault(c.fine().status(), c.fine().status())), label, value);
        }
        doc.add(details);

        Paragraph historyTitle = new Paragraph("History / Riwayat", new Font(Font.HELVETICA, 11, Font.BOLD, INK));
        historyTitle.setSpacingBefore(16);
        historyTitle.setSpacingAfter(6);
        doc.add(historyTitle);
        PdfPTable history = new PdfPTable(new float[] {1.6f, 2.2f, 1.1f, 3f});
        history.setWidthPercentage(100);
        for (String h : new String[] {"When (UTC) / Waktu", "Event / Kejadian", "By / Oleh", "Details / Keterangan"}) {
            PdfPCell cell = new PdfPCell(new Phrase(h, new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE)));
            cell.setBackgroundColor(GREEN);
            cell.setPadding(5);
            cell.setBorderColor(GREEN);
            history.addCell(cell);
        }
        Font cellFont = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, INK);
        for (EventPart e : c.events()) {
            history.addCell(cell(e.occurredAt().replace('T', ' ').replace("Z", ""), cellFont));
            history.addCell(cell(EVENT.getOrDefault(e.type(), e.type()), cellFont));
            history.addCell(cell(e.actor(), cellFont));
            history.addCell(cell(e.detail() == null ? "" : e.detail(), cellFont));
        }
        doc.add(history);

        Paragraph sig = new Paragraph();
        sig.setSpacingBefore(18);
        sig.add(new Chunk("Digitally signed / Ditandatangani secara digital: " + view.algorithm() + " · key " + view.keyId() + "\n", small));
        sig.add(new Chunk("SHA-256: ", small));
        sig.add(new Chunk(view.sha256() + "\n", mono));
        sig.add(new Chunk("Verify / Periksa keaslian: " + view.verifyUrl() + "\n", small));
        sig.add(new Chunk("The signature proves this receipt was issued by the library and has not been changed since. "
                + "It is not a certified electronic signature (PSrE).\n"
                + "Tanda tangan ini membuktikan bukti ini diterbitkan oleh perpustakaan dan tidak diubah sejak itu. "
                + "Ini bukan tanda tangan elektronik tersertifikasi (PSrE).", small));
        doc.add(sig);

        PdfContentByte canvas = writer.getDirectContent();
        drawQr(canvas, view.qr(), PageSize.A4.getWidth() - 48 - 110, PageSize.A4.getHeight() - 48 - 110, 110);
        // Beside the details table (which is 72% wide), below the QR code: never over the text.
        drawStamp(canvas, c.library(), c.issuedAt().substring(0, 10), PageSize.A4.getWidth() - 48 - 62, 600);
        doc.close();
        return out.toByteArray();
    }

    private static void row(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
        PdfPCell v = new PdfPCell(new Phrase(value == null ? "—" : value, valueFont));
        for (PdfPCell cell : new PdfPCell[] {l, v}) {
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderColor(RULE);
            cell.setPaddingTop(4);
            cell.setPaddingBottom(5);
            table.addCell(cell);
        }
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        cell.setBorderColor(RULE);
        return cell;
    }

    /** The QR code as vector squares (sharp at any print size), with a quiet zone. */
    private static void drawQr(PdfContentByte canvas, Qr qr, float x, float y, float size) {
        float module = size / (qr.size() + 8);
        canvas.saveState();
        canvas.setColorFill(Color.WHITE);
        canvas.rectangle(x, y, size, size);
        canvas.fill();
        canvas.setColorFill(Color.BLACK);
        for (int row = 0; row < qr.size(); row++) {
            for (int col = 0; col < qr.size(); col++) {
                if (qr.modules().charAt(row * qr.size() + col) == '1') {
                    canvas.rectangle(x + (col + 4) * module, y + size - (row + 5) * module, module, module);
                }
            }
        }
        canvas.fill();
        canvas.restoreState();
    }

    /** A round rubber stamp, slightly rotated: library name, "receipt / bukti", the issue date. */
    private static void drawStamp(PdfContentByte canvas, String library, String date, float cx, float cy) {
        try {
            BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, false);
            canvas.saveState();
            canvas.setColorStroke(STAMP);
            canvas.setColorFill(STAMP);
            canvas.setGState(opacity(0.85f));
            canvas.setLineWidth(2.2f);
            canvas.circle(cx, cy, 58);
            canvas.stroke();
            canvas.setLineWidth(0.8f);
            canvas.circle(cx, cy, 52);
            canvas.stroke();
            canvas.beginText();
            String name = library.length() > 22 ? library.substring(0, 22) : library;
            canvas.setFontAndSize(bold, 8.5f);
            canvas.showTextAligned(Element.ALIGN_CENTER, name.toUpperCase(), cx, cy + 22, 12);
            canvas.setFontAndSize(bold, 11);
            canvas.showTextAligned(Element.ALIGN_CENTER, "LOAN RECEIPT", cx, cy + 4, 12);
            canvas.setFontAndSize(bold, 8);
            canvas.showTextAligned(Element.ALIGN_CENTER, "BUKTI PEMINJAMAN", cx, cy - 8, 12);
            canvas.setFontAndSize(bold, 9);
            canvas.showTextAligned(Element.ALIGN_CENTER, date, cx, cy - 24, 12);
            canvas.endText();
            canvas.restoreState();
        } catch (Exception e) {
            throw new IllegalStateException("Could not draw the stamp", e);
        }
    }

    private static com.lowagie.text.pdf.PdfGState opacity(float alpha) {
        var state = new com.lowagie.text.pdf.PdfGState();
        state.setFillOpacity(alpha);
        state.setStrokeOpacity(alpha);
        return state;
    }
}
