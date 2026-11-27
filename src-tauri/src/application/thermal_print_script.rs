//! PowerShell thermal printing scripts for Windows.
//! Uses System.Drawing.Printing.PrintDocument with zero margins
//! and Times New Roman fonts across the full 80mm / 58mm paper width.

pub const TEST_PRINT_SCRIPT: &str = r#"param(
    [string]$Target = "",
    [string]$PaperWidth = "80mm"
)

Add-Type -AssemblyName System.Drawing
$doc = New-Object System.Drawing.Printing.PrintDocument

if ($Target -and $Target.Trim() -ne '') {
    $doc.PrinterSettings.PrinterName = $Target.Trim()
}

$doc.DefaultPageSettings.Margins = New-Object System.Drawing.Printing.Margins(0, 0, 0, 0)
$doc.OriginAtMargins = $false

function Draw-RectText($g, [string]$text, $font, $brush, [float]$x, [float]$y, [float]$w, [float]$h, $sf) {
    if (-not $text) { return }
    $r = New-Object System.Drawing.RectangleF($x, $y, $w, $h)
    $g.DrawString($text, $font, $brush, $r, $sf)
}

$doc.add_PrintPage({
    param($sender, $e)
    $g = $e.Graphics
    $g.PageUnit = [System.Drawing.GraphicsUnit]::Display
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::SingleBitPerPixelGridFit

    $driverW = $e.PageBounds.Width
    $is58 = ($PaperWidth -eq '58mm')
    $paperW = if ($is58) {
        if ($driverW -gt 150 -and $driverW -lt 250) { $driverW } else { 210 }
    } else {
        if ($driverW -gt 240 -and $driverW -lt 360) { $driverW } else { 285 }
    }

    $margin = 0
    $contentW = [float]$paperW

    $fontTitle = New-Object System.Drawing.Font("Times New Roman", 13, [System.Drawing.FontStyle]::Bold)
    $fontSub   = New-Object System.Drawing.Font("Times New Roman", 9.5)
    $fontBold  = New-Object System.Drawing.Font("Times New Roman", 9.5, [System.Drawing.FontStyle]::Bold)
    $fontTotal = New-Object System.Drawing.Font("Times New Roman", 12, [System.Drawing.FontStyle]::Bold)
    $fontSmall = New-Object System.Drawing.Font("Times New Roman", 8.5)
    $brush     = [System.Drawing.Brushes]::Black
    $pen       = New-Object System.Drawing.Pen([System.Drawing.Color]::Black, 1)

    $sfCenter = New-Object System.Drawing.StringFormat
    $sfCenter.Alignment = [System.Drawing.StringAlignment]::Center
    $sfLeft   = New-Object System.Drawing.StringFormat
    $sfLeft.Alignment = [System.Drawing.StringAlignment]::Near
    $sfRight  = New-Object System.Drawing.StringFormat
    $sfRight.Alignment = [System.Drawing.StringAlignment]::Far

    $y = 8

    # Title: PICO POSSUM POS
    Draw-RectText $g "PICO POSSUM POS" $fontTitle $brush $margin $y $contentW 24 $sfCenter
    $y += 24

    # Subtitle: THERMAL PRINTER TEST
    Draw-RectText $g "THERMAL PRINTER TEST" $fontBold $brush $margin $y $contentW 18 $sfCenter
    $y += 20

    $y += 2
    $g.DrawLine($pen, [float]$margin, [float]$y, [float]($margin + $contentW), [float]$y)
    $y += 6

    # Status & Font
    $halfW = [float]($contentW * 0.50)
    Draw-RectText $g "Status: Online & Ready" $fontSub $brush $margin $y $halfW 18 $sfLeft
    Draw-RectText $g "Font: Times New Roman" $fontSub $brush ($margin + $halfW) $y $halfW 18 $sfRight
    $y += 18

    # Printer Display
    $printerDisplay = if ($Target) { $Target } else { "System Default Printer" }
    Draw-RectText $g ("Printer: " + $printerDisplay) $fontSub $brush $margin $y $contentW 18 $sfLeft
    $y += 18

    # Date
    $dateStr = Get-Date -Format "dd-MM-yyyy HH:mm:ss"
    Draw-RectText $g ("Date: " + $dateStr) $fontSub $brush $margin $y $contentW 18 $sfLeft
    $y += 18

    $y += 2
    $g.DrawLine($pen, [float]$margin, [float]$y, [float]($margin + $contentW), [float]$y)
    $y += 6

    # Sample Table Columns (Item: 46%, Qty: 12%, Rate: 20%, Amount: 22%)
    $colItemW = [float][Math]::Floor($contentW * 0.46)
    $colQtyW  = [float][Math]::Floor($contentW * 0.12)
    $colRateW = [float][Math]::Floor($contentW * 0.20)
    $colAmtW  = [float]($contentW - $colItemW - $colQtyW - $colRateW)

    $xItem = [float]$margin
    $xQty  = [float]($xItem + $colItemW)
    $xRate = [float]($xQty + $colQtyW)
    $xAmt  = [float]($xRate + $colRateW)

    Draw-RectText $g "Item" $fontBold $brush $xItem $y $colItemW 18 $sfLeft
    Draw-RectText $g "Qty" $fontBold $brush $xQty $y $colQtyW 18 $sfCenter
    Draw-RectText $g "Rate" $fontBold $brush $xRate $y $colRateW 18 $sfRight
    Draw-RectText $g "Amount" $fontBold $brush $xAmt $y $colAmtW 18 $sfRight
    $y += 20

    $g.DrawLine($pen, [float]$margin, [float]$y, [float]($margin + $contentW), [float]$y)
    $y += 6

    # Sample Item Row
    Draw-RectText $g "Sample Product Test" $fontSub $brush $xItem $y $colItemW 18 $sfLeft
    Draw-RectText $g "1" $fontSub $brush $xQty $y $colQtyW 18 $sfCenter
    Draw-RectText $g "100.00" $fontSub $brush $xRate $y $colRateW 18 $sfRight
    Draw-RectText $g "100.00" $fontSub $brush $xAmt $y $colAmtW 18 $sfRight
    $y += 18

    $y += 2
    $g.DrawLine($pen, [float]$margin, [float]$y, [float]($margin + $contentW), [float]$y)
    $y += 6

    # Total
    $totLabelW = [float]($contentW * 0.45)
    $totValW   = [float]($contentW - $totLabelW)
    Draw-RectText $g "TOTAL:" $fontTotal $brush $margin $y $totLabelW 22 $sfLeft
    Draw-RectText $g "100.00" $fontTotal $brush ($margin + $totLabelW) $y $totValW 22 $sfRight
    $y += 24

    $y += 2
    $g.DrawLine($pen, [float]$margin, [float]$y, [float]($margin + $contentW), [float]$y)
    $y += 8

    # Footer
    Draw-RectText $g "Thermal Print Configuration OK!" $fontSub $brush $margin $y $contentW 18 $sfCenter
    $y += 18

    $paperLabel = if ($is58) { "Paper: 58mm Thermal Roll" } else { "Paper: 80mm Thermal Roll" }
    Draw-RectText $g $paperLabel $fontSmall $brush $margin $y $contentW 18 $sfCenter
    $y += 28

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

function Draw-RectText($g, [string]$text, $font, $brush, [float]$x, [float]$y, [float]$w, [float]$h, $sf) {
    if (-not $text) { return }
    $r = New-Object System.Drawing.RectangleF($x, $y, $w, $h)
    $g.DrawString($text, $font, $brush, $r, $sf)
}

$doc.add_PrintPage({
    param($sender, $e)
    $g = $e.Graphics
    $g.PageUnit = [System.Drawing.GraphicsUnit]::Display
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::SingleBitPerPixelGridFit

    $driverW = $e.PageBounds.Width
    $is58 = ($data.paperWidth -eq '58mm')
    $paperW = if ($is58) {
        if ($driverW -gt 150 -and $driverW -lt 250) { $driverW } else { 210 }
    } else {
        if ($driverW -gt 240 -and $driverW -lt 360) { $driverW } else { 285 }
    }

    $margin = 0
    $contentW = [float]$paperW

    $fontTitle = New-Object System.Drawing.Font("Times New Roman", 13, [System.Drawing.FontStyle]::Bold)
    $fontSub   = New-Object System.Drawing.Font("Times New Roman", 9.5)
    $fontBold  = New-Object System.Drawing.Font("Times New Roman", 9.5, [System.Drawing.FontStyle]::Bold)
    $fontTotal = New-Object System.Drawing.Font("Times New Roman", 12, [System.Drawing.FontStyle]::Bold)
    $fontSmall = New-Object System.Drawing.Font("Times New Roman", 8.5)
    $brush     = [System.Drawing.Brushes]::Black
    $pen       = New-Object System.Drawing.Pen([System.Drawing.Color]::Black, 1)

    $sfCenter = New-Object System.Drawing.StringFormat
    $sfCenter.Alignment = [System.Drawing.StringAlignment]::Center
    $sfLeft   = New-Object System.Drawing.StringFormat
    $sfLeft.Alignment = [System.Drawing.StringAlignment]::Near
    $sfRight  = New-Object System.Drawing.StringFormat
    $sfRight.Alignment = [System.Drawing.StringAlignment]::Far

    $y = 8

    # Cancellation / Amendment banner
    if ($data.isCancelled) {
        $g.DrawRectangle($pen, [float]$margin, [float]$y, $contentW, 22)
        Draw-RectText $g "*** CANCELLED ***" $fontBold $brush $margin ($y + 4) $contentW 16 $sfCenter
        $y += 28
    } elseif ($data.isAmended) {
        $g.DrawRectangle($pen, [float]$margin, [float]$y, $contentW, 20)
        Draw-RectText $g "*** AMENDED BILL ***" $fontBold $brush $margin ($y + 3) $contentW 16 $sfCenter
        $y += 26
    }

    # Store Name (wraps if long)
    if ($data.storeName) {
        $storeTitle = $data.storeName.ToUpper()
        $measured = $g.MeasureString($storeTitle, $fontTitle, [int]$contentW, $sfCenter)
        $h = [float][Math]::Max(22, [Math]::Ceiling($measured.Height) + 2)
        Draw-RectText $g $storeTitle $fontTitle $brush $margin $y $contentW $h $sfCenter
        $y += $h
    }

    # Store Address (wraps to next line cleanly if wider than paper)
    if ($data.storeDetails) {
        $lines = $data.storeDetails -split "`n"
        foreach ($l in $lines) {
            $t = $l.Trim()
            if ($t) {
                $measured = $g.MeasureString($t, $fontSmall, [int]$contentW, $sfCenter)
                $h = [float][Math]::Max(15, [Math]::Ceiling($measured.Height) + 2)
                Draw-RectText $g $t $fontSmall $brush $margin $y $contentW $h $sfCenter
                $y += $h
            }
        }
    }

    # Phone
    if ($data.phone) {
        Draw-RectText $g ("Phone: " + $data.phone) $fontBold $brush $margin $y $contentW 18 $sfCenter
        $y += 16
    }

    # GSTIN
    if ($data.gstin) {
        Draw-RectText $g ("GSTIN: " + $data.gstin) $fontBold $brush $margin $y $contentW 18 $sfCenter
        $y += 16
    }

    $y += 3
    $g.DrawLine($pen, [float]$margin, [float]$y, [float]($margin + $contentW), [float]$y)
    $y += 6

    # Bill Metadata
    if ($data.invoiceNumber -or $data.date) {
        $metaLeftW  = [float][Math]::Floor($contentW * 0.58)
        $metaRightW = [float]($contentW - $metaLeftW)
        if ($data.invoiceNumber) {
            Draw-RectText $g ("Bill No: " + $data.invoiceNumber) $fontSub $brush $margin $y $metaLeftW 18 $sfLeft
        }
        if ($data.date) {
            Draw-RectText $g ([string]$data.date) $fontSub $brush ($margin + $metaLeftW) $y $metaRightW 18 $sfRight
        }
        $y += 16
    }

    if ($data.customerName -or $data.time) {
        $custLeftW  = [float][Math]::Floor($contentW * 0.65)
        $custRightW = [float]($contentW - $custLeftW)
        if ($data.customerName) {
            Draw-RectText $g ("Cust: " + $data.customerName) $fontSub $brush $margin $y $custLeftW 18 $sfLeft
        }
        if ($data.time) {
            Draw-RectText $g ([string]$data.time) $fontSub $brush ($margin + $custLeftW) $y $custRightW 18 $sfRight
        }
        $y += 16
    }

    if ($data.billerName) {
        Draw-RectText $g ("Cashier: " + $data.billerName) $fontSub $brush $margin $y $contentW 18 $sfLeft
        $y += 16
    }

    $y += 3
    $g.DrawLine($pen, [float]$margin, [float]$y, [float]($margin + $contentW), [float]$y)
    $y += 6

    # Items Header: Item | Qty | Rate | [Tax] | Amount
    $hasTaxCol = ($data.showTax -eq $true) -and ($data.taxCents -gt 0)
    if ($hasTaxCol) {
        $colItemW = [float][Math]::Floor($contentW * 0.36)
        $colQtyW  = [float][Math]::Floor($contentW * 0.11)
        $colRateW = [float][Math]::Floor($contentW * 0.17)
        $colTaxW  = [float][Math]::Floor($contentW * 0.14)
        $colAmtW  = [float]($contentW - $colItemW - $colQtyW - $colRateW - $colTaxW)

        $xItem = [float]$margin
        $xQty  = [float]($xItem + $colItemW)
        $xRate = [float]($xQty + $colQtyW)
        $xTax  = [float]($xRate + $colRateW)
        $xAmt  = [float]($xTax + $colTaxW)
    } else {
        $colItemW = [float][Math]::Floor($contentW * 0.46)
        $colQtyW  = [float][Math]::Floor($contentW * 0.12)
        $colRateW = [float][Math]::Floor($contentW * 0.20)
        $colTaxW  = [float]0
        $colAmtW  = [float]($contentW - $colItemW - $colQtyW - $colRateW)

        $xItem = [float]$margin
        $xQty  = [float]($xItem + $colItemW)
        $xRate = [float]($xQty + $colQtyW)
        $xTax  = [float]0
        $xAmt  = [float]($xRate + $colRateW)
    }

    Draw-RectText $g "Item" $fontBold $brush $xItem $y $colItemW 18 $sfLeft
    Draw-RectText $g "Qty" $fontBold $brush $xQty $y $colQtyW 18 $sfCenter
    Draw-RectText $g "Rate" $fontBold $brush $xRate $y $colRateW 18 $sfRight
    if ($hasTaxCol) {
        Draw-RectText $g "Tax" $fontBold $brush $xTax $y $colTaxW 18 $sfRight
    }
    Draw-RectText $g "Amount" $fontBold $brush $xAmt $y $colAmtW 18 $sfRight
    $y += 20

    $g.DrawLine($pen, [float]$margin, [float]$y, [float]($margin + $contentW), [float]$y)
    $y += 6

    # Item Rows
    $sym = if ($data.currencySymbol) { $data.currencySymbol } else { "" }
    $sfWrap = New-Object System.Drawing.StringFormat
    $sfWrap.Alignment = [System.Drawing.StringAlignment]::Near
    $sfWrap.LineAlignment = [System.Drawing.StringAlignment]::Near

    foreach ($it in $data.items) {
        $name = [string]$it.name
        $qtyStr = [string]$it.quantity
        $rateStr = if ($it.rateStr) { [string]$it.rateStr } else { [string]::Format("{0:0.00}", ($it.unitPriceCents / 100)) }
        $amtStr = if ($it.amtStr) { [string]$it.amtStr } else { [string]::Format("{0:0.00}", ($it.totalCents / 100)) }
        $taxRateStr = if ($it.taxRate) { [string]$it.taxRate + "%" } else { "0%" }

        $measured = $g.MeasureString($name, $fontSub, [int]$colItemW, $sfWrap)
        $rowH = [float][Math]::Max(16, [Math]::Ceiling($measured.Height) + 2)

        Draw-RectText $g $name $fontSub $brush $xItem $y $colItemW $rowH $sfWrap
        Draw-RectText $g $qtyStr $fontSub $brush $xQty $y $colQtyW 18 $sfCenter
        Draw-RectText $g $rateStr $fontSub $brush $xRate $y $colRateW 18 $sfRight
        if ($hasTaxCol) {
            Draw-RectText $g $taxRateStr $fontSub $brush $xTax $y $colTaxW 18 $sfRight
        }
        Draw-RectText $g $amtStr $fontSub $brush $xAmt $y $colAmtW 18 $sfRight

        $y += $rowH + 2
    }

    $y += 2
    $g.DrawLine($pen, [float]$margin, [float]$y, [float]($margin + $contentW), [float]$y)
    $y += 6

    $halfW = [float][Math]::Floor($contentW * 0.50)
    $halfRightW = [float]($contentW - $halfW)

    # Totals (NO Subtotal, Discount ONLY if > 0, NO Paid via, NO Change, NO Balance Due)
    if ($data.discountCents -gt 0) {
        $discVal = if ($data.discountStr) { [string]$data.discountStr } else { [string]::Format("{0:0.00}", ($data.discountCents / 100)) }
        $discStr = "-" + $sym + $discVal
        Draw-RectText $g "Discount:" $fontSub $brush $margin $y $halfW 18 $sfLeft
        Draw-RectText $g $discStr $fontSub $brush ($margin + $halfW) $y $halfRightW 18 $sfRight
        $y += 18
    }

    if ($data.showTax -and $data.taxCents -gt 0) {
        $taxVal = if ($data.taxStr) { [string]$data.taxStr } else { [string]::Format("{0:0.00}", ($data.taxCents / 100)) }
        $taxStr = $sym + $taxVal
        Draw-RectText $g "Tax Amount:" $fontSub $brush $margin $y $halfW 18 $sfLeft
        Draw-RectText $g $taxStr $fontSub $brush ($margin + $halfW) $y $halfRightW 18 $sfRight
        $y += 18
    }

    $y += 2
    $g.DrawLine($pen, [float]$margin, [float]$y, [float]($margin + $contentW), [float]$y)
    $y += 6

    # TOTAL (Large Bold)
    $totVal = if ($data.totalStr) { [string]$data.totalStr } else { [string]::Format("{0:0.00}", ($data.totalCents / 100)) }
    $totStr = $sym + $totVal
    $totLabelW = [float][Math]::Floor($contentW * 0.45)
    $totValW   = [float]($contentW - $totLabelW)
    Draw-RectText $g "TOTAL:" $fontTotal $brush $margin $y $totLabelW 22 $sfLeft
    Draw-RectText $g $totStr $fontTotal $brush ($margin + $totLabelW) $y $totValW 22 $sfRight
    $y += 24

    # Footer (wraps if long)
    if ($data.footerText) {
        $y += 4
        $g.DrawLine($pen, [float]$margin, [float]$y, [float]($margin + $contentW), [float]$y)
        $y += 8
        $fLines = $data.footerText -split "`n"
        foreach ($fl in $fLines) {
            $ft = $fl.Trim()
            if ($ft) {
                $measured = $g.MeasureString($ft, $fontSmall, [int]$contentW, $sfCenter)
                $h = [float][Math]::Max(15, [Math]::Ceiling($measured.Height) + 2)
                Draw-RectText $g $ft $fontSmall $brush $margin $y $contentW $h $sfCenter
                $y += $h
            }
        }
    }

    $y += 28
    $e.HasMorePages = $false
})

$doc.Print()
"#;
