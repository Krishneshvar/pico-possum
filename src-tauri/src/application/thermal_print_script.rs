//! PowerShell thermal printing scripts for Windows.
//! Uses System.Drawing.Printing.PrintDocument with zero margins
//! and Times New Roman fonts across the full 80mm / 58mm paper width.

pub const TEST_PRINT_SCRIPT: &str = r#"param(
    [string]$Target = ""
)

Add-Type -AssemblyName System.Drawing
$doc = New-Object System.Drawing.Printing.PrintDocument

if ($Target -and $Target.Trim() -ne '') {
    $doc.PrinterSettings.PrinterName = $Target.Trim()
}

$doc.DefaultPageSettings.Margins = New-Object System.Drawing.Printing.Margins(0, 0, 0, 0)
$doc.OriginAtMargins = $false

$doc.add_PrintPage({
    param($sender, $e)
    $g = $e.Graphics
    $g.PageUnit = [System.Drawing.GraphicsUnit]::Display

    $paperW = 285
    $margin = 6
    $contentW = $paperW - ($margin * 2)

    $fontTitle = New-Object System.Drawing.Font("Times New Roman", 13, [System.Drawing.FontStyle]::Bold)
    $fontSub = New-Object System.Drawing.Font("Times New Roman", 9.5)
    $fontBold = New-Object System.Drawing.Font("Times New Roman", 9.5, [System.Drawing.FontStyle]::Bold)
    $fontTotal = New-Object System.Drawing.Font("Times New Roman", 12, [System.Drawing.FontStyle]::Bold)
    $brush = [System.Drawing.Brushes]::Black
    $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::Black, 1)

    $sfCenter = New-Object System.Drawing.StringFormat
    $sfCenter.Alignment = [System.Drawing.StringAlignment]::Center
    $sfLeft = New-Object System.Drawing.StringFormat
    $sfLeft.Alignment = [System.Drawing.StringAlignment]::Near
    $sfRight = New-Object System.Drawing.StringFormat
    $sfRight.Alignment = [System.Drawing.StringAlignment]::Far

    $y = 8

    # Title
    $rect = New-Object System.Drawing.RectangleF($margin, $y, $contentW, 20)
    $g.DrawString("PICO POSSUM POS", $fontTitle, $brush, $rect, $sfCenter)
    $y += 22

    # Subtitle
    $rect = New-Object System.Drawing.RectangleF($margin, $y, $contentW, 16)
    $g.DrawString("THERMAL PRINTER TEST", $fontBold, $brush, $rect, $sfCenter)
    $y += 18

    $y += 2
    $g.DrawLine($pen, $margin, $y, $margin + $contentW, $y)
    $y += 6

    # Status & Font
    $rectL = New-Object System.Drawing.RectangleF($margin, $y, $contentW * 0.50, 14)
    $rectR = New-Object System.Drawing.RectangleF($margin + $contentW * 0.50, $y, $contentW * 0.50, 14)
    $g.DrawString("Status: Online & Ready", $fontSub, $brush, $rectL, $sfLeft)
    $g.DrawString("Font: Times New Roman", $fontSub, $brush, $rectR, $sfRight)
    $y += 15

    # Printer
    $printerDisplay = if ($Target) { $Target } else { "System Default Printer" }
    $rect = New-Object System.Drawing.RectangleF($margin, $y, $contentW, 14)
    $g.DrawString("Printer: " + $printerDisplay, $fontSub, $brush, $rect, $sfLeft)
    $y += 15

    # Date
    $dateStr = Get-Date -Format "dd-MM-yyyy HH:mm:ss"
    $rect = New-Object System.Drawing.RectangleF($margin, $y, $contentW, 14)
    $g.DrawString("Date: " + $dateStr, $fontSub, $brush, $rect, $sfLeft)
    $y += 15

    $y += 2
    $g.DrawLine($pen, $margin, $y, $margin + $contentW, $y)
    $y += 6

    # Sample Table Columns
    $colItemW = $contentW * 0.44
    $colQtyW  = $contentW * 0.14
    $colRateW = $contentW * 0.20
    $colAmtW  = $contentW * 0.22

    $rect = New-Object System.Drawing.RectangleF($margin, $y, $colItemW, 14)
    $g.DrawString("Item", $fontBold, $brush, $rect, $sfLeft)
    $rect = New-Object System.Drawing.RectangleF($margin + $colItemW, $y, $colQtyW, 14)
    $g.DrawString("Qty", $fontBold, $brush, $rect, $sfCenter)
    $rect = New-Object System.Drawing.RectangleF($margin + $colItemW + $colQtyW, $y, $colRateW, 14)
    $g.DrawString("Rate", $fontBold, $brush, $rect, $sfRight)
    $rect = New-Object System.Drawing.RectangleF($margin + $colItemW + $colQtyW + $colRateW, $y, $colAmtW, 14)
    $g.DrawString("Amount", $fontBold, $brush, $rect, $sfRight)
    $y += 16

    $g.DrawLine($pen, $margin, $y, $margin + $contentW, $y)
    $y += 6

    # Sample Item Row
    $rect = New-Object System.Drawing.RectangleF($margin, $y, $colItemW, 14)
    $g.DrawString("Sample Product Test", $fontSub, $brush, $rect, $sfLeft)
    $rect = New-Object System.Drawing.RectangleF($margin + $colItemW, $y, $colQtyW, 14)
    $g.DrawString("1", $fontSub, $brush, $rect, $sfCenter)
    $rect = New-Object System.Drawing.RectangleF($margin + $colItemW + $colQtyW, $y, $colRateW, 14)
    $g.DrawString("100.00", $fontSub, $brush, $rect, $sfRight)
    $rect = New-Object System.Drawing.RectangleF($margin + $colItemW + $colQtyW + $colRateW, $y, $colAmtW, 14)
    $g.DrawString("100.00", $fontSub, $brush, $rect, $sfRight)
    $y += 16

    $y += 2
    $g.DrawLine($pen, $margin, $y, $margin + $contentW, $y)
    $y += 6

    # Total
    $rectL = New-Object System.Drawing.RectangleF($margin, $y, $contentW * 0.45, 18)
    $rectR = New-Object System.Drawing.RectangleF($margin + $contentW * 0.45, $y, $contentW * 0.55, 18)
    $g.DrawString("TOTAL:", $fontTotal, $brush, $rectL, $sfLeft)
    $g.DrawString("100.00", $fontTotal, $brush, $rectR, $sfRight)
    $y += 22

    $y += 2
    $g.DrawLine($pen, $margin, $y, $margin + $contentW, $y)
    $y += 8

    # Footer
    $rect = New-Object System.Drawing.RectangleF($margin, $y, $contentW, 14)
    $g.DrawString("Thermal Print Configuration OK!", $fontSub, $brush, $rect, $sfCenter)
    $y += 18

    $e.HasMorePages = $false
})

$doc.Print()
"#;

pub const RECEIPT_PRINT_SCRIPT: &str = r#"param(
    [string]$Target = "",
    [string]$DataFile = ""
)

if (-not (Test-Path $DataFile)) {
    Write-Error "Data file not found: $DataFile"
    exit 1
}

$rawJson = Get-Content $DataFile -Raw -Encoding UTF8
$data = $rawJson | ConvertFrom-Json

Add-Type -AssemblyName System.Drawing
$doc = New-Object System.Drawing.Printing.PrintDocument

if ($Target -and $Target.Trim() -ne '') {
    $doc.PrinterSettings.PrinterName = $Target.Trim()
}

$doc.DefaultPageSettings.Margins = New-Object System.Drawing.Printing.Margins(0, 0, 0, 0)
$doc.OriginAtMargins = $false

$doc.add_PrintPage({
    param($sender, $e)
    $g = $e.Graphics
    $g.PageUnit = [System.Drawing.GraphicsUnit]::Display

    $paperW = if ($data.paperWidth -eq '58mm') { 210 } else { 285 }
    $margin = 6
    $contentW = $paperW - ($margin * 2)

    $fontTitle = New-Object System.Drawing.Font("Times New Roman", 13, [System.Drawing.FontStyle]::Bold)
    $fontSub = New-Object System.Drawing.Font("Times New Roman", 9.5)
    $fontBold = New-Object System.Drawing.Font("Times New Roman", 9.5, [System.Drawing.FontStyle]::Bold)
    $fontTotal = New-Object System.Drawing.Font("Times New Roman", 12, [System.Drawing.FontStyle]::Bold)
    $fontSmall = New-Object System.Drawing.Font("Times New Roman", 8.5)
    $brush = [System.Drawing.Brushes]::Black
    $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::Black, 1)

    $sfCenter = New-Object System.Drawing.StringFormat
    $sfCenter.Alignment = [System.Drawing.StringAlignment]::Center
    $sfLeft = New-Object System.Drawing.StringFormat
    $sfLeft.Alignment = [System.Drawing.StringAlignment]::Near
    $sfRight = New-Object System.Drawing.StringFormat
    $sfRight.Alignment = [System.Drawing.StringAlignment]::Far

    $y = 8

    # Cancellation / Amendment banner
    if ($data.isCancelled) {
        $g.DrawRectangle($pen, $margin, $y, $contentW, 20)
        $rect = New-Object System.Drawing.RectangleF($margin, $y + 3, $contentW, 16)
        $g.DrawString("*** CANCELLED ***", $fontBold, $brush, $rect, $sfCenter)
        $y += 26
    } elseif ($data.isAmended) {
        $g.DrawRectangle($pen, $margin, $y, $contentW, 18)
        $rect = New-Object System.Drawing.RectangleF($margin, $y + 2, $contentW, 14)
        $g.DrawString("*** AMENDED BILL ***", $fontBold, $brush, $rect, $sfCenter)
        $y += 24
    }

    # Store Name
    if ($data.storeName) {
        $rect = New-Object System.Drawing.RectangleF($margin, $y, $contentW, 20)
        $g.DrawString($data.storeName.ToUpper(), $fontTitle, $brush, $rect, $sfCenter)
        $y += 22
    }

    # Store Address
    if ($data.storeDetails) {
        $lines = $data.storeDetails -split "`n"
        foreach ($l in $lines) {
            $t = $l.Trim()
            if ($t) {
                $rect = New-Object System.Drawing.RectangleF($margin, $y, $contentW, 14)
                $g.DrawString($t, $fontSmall, $brush, $rect, $sfCenter)
                $y += 14
            }
        }
    }

    # Phone
    if ($data.phone) {
        $rect = New-Object System.Drawing.RectangleF($margin, $y, $contentW, 14)
        $g.DrawString("Phone: " + $data.phone, $fontBold, $brush, $rect, $sfCenter)
        $y += 15
    }

    # GSTIN
    if ($data.gstin) {
        $rect = New-Object System.Drawing.RectangleF($margin, $y, $contentW, 14)
        $g.DrawString("GSTIN: " + $data.gstin, $fontBold, $brush, $rect, $sfCenter)
        $y += 15
    }

    $y += 3
    $g.DrawLine($pen, $margin, $y, $margin + $contentW, $y)
    $y += 6

    # Bill Metadata
    $rectL = New-Object System.Drawing.RectangleF($margin, $y, $contentW * 0.55, 14)
    $rectR = New-Object System.Drawing.RectangleF($margin + $contentW * 0.55, $y, $contentW * 0.45, 14)
    $g.DrawString("Bill No: " + $data.invoiceNumber, $fontSub, $brush, $rectL, $sfLeft)
    $g.DrawString($data.date, $fontSub, $brush, $rectR, $sfRight)
    $y += 14

    $custText = if ($data.customerName) { "Cust: " + $data.customerName } else { "" }
    $rectL = New-Object System.Drawing.RectangleF($margin, $y, $contentW * 0.65, 14)
    $rectR = New-Object System.Drawing.RectangleF($margin + $contentW * 0.65, $y, $contentW * 0.35, 14)
    if ($custText) { $g.DrawString($custText, $fontSub, $brush, $rectL, $sfLeft) }
    $g.DrawString($data.time, $fontSub, $brush, $rectR, $sfRight)
    $y += 14

    if ($data.billerName) {
        $rect = New-Object System.Drawing.RectangleF($margin, $y, $contentW, 14)
        $g.DrawString("Cashier: " + $data.billerName, $fontSub, $brush, $rect, $sfLeft)
        $y += 14
    }

    $y += 3
    $g.DrawLine($pen, $margin, $y, $margin + $contentW, $y)
    $y += 6

    # Items Header: Item | Qty | Rate | Amount
    $colItemW = $contentW * 0.44
    $colQtyW  = $contentW * 0.14
    $colRateW = $contentW * 0.20
    $colAmtW  = $contentW * 0.22

    $rect = New-Object System.Drawing.RectangleF($margin, $y, $colItemW, 14)
    $g.DrawString("Item", $fontBold, $brush, $rect, $sfLeft)
    $rect = New-Object System.Drawing.RectangleF($margin + $colItemW, $y, $colQtyW, 14)
    $g.DrawString("Qty", $fontBold, $brush, $rect, $sfCenter)
    $rect = New-Object System.Drawing.RectangleF($margin + $colItemW + $colQtyW, $y, $colRateW, 14)
    $g.DrawString("Rate", $fontBold, $brush, $rect, $sfRight)
    $rect = New-Object System.Drawing.RectangleF($margin + $colItemW + $colQtyW + $colRateW, $y, $colAmtW, 14)
    $g.DrawString("Amount", $fontBold, $brush, $rect, $sfRight)
    $y += 16

    $g.DrawLine($pen, $margin, $y, $margin + $contentW, $y)
    $y += 6

    # Items Rows (ONLY Item Name, NO SKU)
    $sym = if ($data.currencySymbol) { $data.currencySymbol } else { "" }
    foreach ($it in $data.items) {
        $name = $it.name
        $qtyStr = [string]$it.quantity
        $rateStr = [string]::Format("{0:0.00}", ($it.unitPriceCents / 100))
        $amtStr = [string]::Format("{0:0.00}", ($it.totalCents / 100))

        $rect = New-Object System.Drawing.RectangleF($margin, $y, $colItemW, 14)
        $g.DrawString($name, $fontSub, $brush, $rect, $sfLeft)
        $rect = New-Object System.Drawing.RectangleF($margin + $colItemW, $y, $colQtyW, 14)
        $g.DrawString($qtyStr, $fontSub, $brush, $rect, $sfCenter)
        $rect = New-Object System.Drawing.RectangleF($margin + $colItemW + $colQtyW, $y, $colRateW, 14)
        $g.DrawString($rateStr, $fontSub, $brush, $rect, $sfRight)
        $rect = New-Object System.Drawing.RectangleF($margin + $colItemW + $colQtyW + $colRateW, $y, $colAmtW, 14)
        $g.DrawString($amtStr, $fontSub, $brush, $rect, $sfRight)
        $y += 15
    }

    $y += 3
    $g.DrawLine($pen, $margin, $y, $margin + $contentW, $y)
    $y += 6

    # Totals (NO Subtotal, Discount ONLY if > 0, NO Paid via)
    if ($data.discountCents -gt 0) {
        $discStr = "-" + $sym + [string]::Format("{0:0.00}", ($data.discountCents / 100))
        $rectL = New-Object System.Drawing.RectangleF($margin, $y, $contentW * 0.50, 14)
        $rectR = New-Object System.Drawing.RectangleF($margin + $contentW * 0.50, $y, $contentW * 0.50, 14)
        $g.DrawString("Discount:", $fontSub, $brush, $rectL, $sfLeft)
        $g.DrawString($discStr, $fontSub, $brush, $rectR, $sfRight)
        $y += 15
    }

    if ($data.showTax -and $data.taxCents -gt 0) {
        $taxStr = $sym + [string]::Format("{0:0.00}", ($data.taxCents / 100))
        $rectL = New-Object System.Drawing.RectangleF($margin, $y, $contentW * 0.50, 14)
        $rectR = New-Object System.Drawing.RectangleF($margin + $contentW * 0.50, $y, $contentW * 0.50, 14)
        $g.DrawString("Tax Amount:", $fontSub, $brush, $rectL, $sfLeft)
        $g.DrawString($taxStr, $fontSub, $brush, $rectR, $sfRight)
        $y += 15
    }

    # Total Row (Large Bold)
    $totStr = $sym + [string]::Format("{0:0.00}", ($data.totalCents / 100))
    $rectL = New-Object System.Drawing.RectangleF($margin, $y, $contentW * 0.45, 18)
    $rectR = New-Object System.Drawing.RectangleF($margin + $contentW * 0.45, $y, $contentW * 0.55, 18)
    $g.DrawString("TOTAL:", $fontTotal, $brush, $rectL, $sfLeft)
    $g.DrawString($totStr, $fontTotal, $brush, $rectR, $sfRight)
    $y += 20

    # Footer
    if ($data.footerText) {
        $y += 4
        $g.DrawLine($pen, $margin, $y, $margin + $contentW, $y)
        $y += 8
        $fLines = $data.footerText -split "`n"
        foreach ($fl in $fLines) {
            $ft = $fl.Trim()
            if ($ft) {
                $rect = New-Object System.Drawing.RectangleF($margin, $y, $contentW, 14)
                $g.DrawString($ft, $fontSmall, $brush, $rect, $sfCenter)
                $y += 14
            }
        }
    }

    $y += 20
    $e.HasMorePages = $false
})

$doc.Print()
"#;
