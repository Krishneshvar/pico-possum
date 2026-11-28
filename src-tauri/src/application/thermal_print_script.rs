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
    $pen       = New-Object System.Drawing.Pen([System.Drawing.Color]::Black, 2.2)

    $sfCenter = New-Object System.Drawing.StringFormat
    $sfCenter.Alignment = [System.Drawing.StringAlignment]::Center
    $sfLeft   = New-Object System.Drawing.StringFormat
    $sfLeft.Alignment = [System.Drawing.StringAlignment]::Near
    $sfRight  = New-Object System.Drawing.StringFormat
    $sfRight.Alignment = [System.Drawing.StringAlignment]::Far

    $y = 0

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

function Draw-LabelValue($g, [string]$label, $fontLabel, [string]$val, $fontVal, $brush, [float]$x, [float]$y, [float]$maxW, [float]$h, [bool]$alignRight = $false) {
    $lblSize = if ($label) { $g.MeasureString($label, $fontLabel) } else { [System.Drawing.SizeF]::Empty }
    $valSize = if ($val) { $g.MeasureString($val, $fontVal) } else { [System.Drawing.SizeF]::Empty }
    $totalW = $lblSize.Width + $valSize.Width

    $startX = if ($alignRight) { [float]($x + [Math]::Max(0, ($maxW - $totalW))) } else { $x }
    if ($label) {
        $rLbl = New-Object System.Drawing.RectangleF($startX, $y, ($lblSize.Width + 2), $h)
        $g.DrawString($label, $fontLabel, $brush, $rLbl)
    }
    if ($val) {
        $rVal = New-Object System.Drawing.RectangleF(($startX + $lblSize.Width), $y, [Math]::Max(10, ($maxW - $lblSize.Width)), $h)
        $g.DrawString($val, $fontVal, $brush, $rVal)
    }
}

function Draw-LabelValueCentered($g, [string]$label, $fontLabel, [string]$val, $fontVal, $brush, [float]$x, [float]$y, [float]$w, [float]$h) {
    $lblSize = if ($label) { $g.MeasureString($label, $fontLabel) } else { [System.Drawing.SizeF]::Empty }
    $valSize = if ($val) { $g.MeasureString($val, $fontVal) } else { [System.Drawing.SizeF]::Empty }
    $totalW = $lblSize.Width + $valSize.Width
    $startX = [float]($x + [Math]::Max(0, ($w - $totalW) / 2))
    if ($label) {
        $rLbl = New-Object System.Drawing.RectangleF($startX, $y, ($lblSize.Width + 2), $h)
        $g.DrawString($label, $fontLabel, $brush, $rLbl)
    }
    if ($val) {
        $rVal = New-Object System.Drawing.RectangleF(($startX + $lblSize.Width), $y, [Math]::Max(10, ($w - $lblSize.Width)), $h)
        $g.DrawString($val, $fontVal, $brush, $rVal)
    }
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

    $headerSize = if ($data.headerFontSize) { [string]$data.headerFontSize } else { "medium" }
    $titlePt = if ($headerSize -eq "large") { 20 } elseif ($headerSize -eq "small") { 15 } else { 17 }
    $logoMaxH = if ($headerSize -eq "large") { 52.0 } elseif ($headerSize -eq "small") { 34.0 } else { 42.0 }
    $logoMaxW = if ($headerSize -eq "large") { 80.0 } elseif ($headerSize -eq "small") { 50.0 } else { 64.0 }

    $fontTitle = New-Object System.Drawing.Font("Times New Roman", $titlePt, [System.Drawing.FontStyle]::Bold)
    $fontSub   = New-Object System.Drawing.Font("Times New Roman", 9.5)
    $fontBold  = New-Object System.Drawing.Font("Times New Roman", 9.5, [System.Drawing.FontStyle]::Bold)
    $fontTotal = New-Object System.Drawing.Font("Times New Roman", 13, [System.Drawing.FontStyle]::Bold)
    $fontSmall = New-Object System.Drawing.Font("Times New Roman", 8.5)
    $fontSmallBold = New-Object System.Drawing.Font("Times New Roman", 8.5, [System.Drawing.FontStyle]::Bold)
    $brush     = [System.Drawing.Brushes]::Black
    $pen       = New-Object System.Drawing.Pen([System.Drawing.Color]::Black, 2.2)

    $sfCenter = New-Object System.Drawing.StringFormat
    $sfCenter.Alignment = [System.Drawing.StringAlignment]::Center
    $sfLeft   = New-Object System.Drawing.StringFormat
    $sfLeft.Alignment = [System.Drawing.StringAlignment]::Near
    $sfRight  = New-Object System.Drawing.StringFormat
    $sfRight.Alignment = [System.Drawing.StringAlignment]::Far
    $sfWrap = New-Object System.Drawing.StringFormat
    $sfWrap.Alignment = [System.Drawing.StringAlignment]::Near
    $sfWrap.LineAlignment = [System.Drawing.StringAlignment]::Near

    $y = 0

    # Cancellation banner
    if ($data.isCancelled) {
        $g.DrawRectangle($pen, [float]$margin, [float]$y, $contentW, 22)
        Draw-RectText $g "*** CANCELLED ***" $fontBold $brush $margin ($y + 4) $contentW 16 $sfCenter
        $y += 28
    }

    # Decode Logo if provided
    $logoBmp = $null
    if ($data.logoData -and [string]$data.logoData -ne "") {
        try {
            $rawB64 = [string]$data.logoData
            if ($rawB64.Contains(",")) {
                $rawB64 = $rawB64.Substring($rawB64.IndexOf(",") + 1)
            }
            $bytes = [System.Convert]::FromBase64String($rawB64)
            $ms = New-Object System.IO.MemoryStream(,$bytes)
            $logoBmp = [System.Drawing.Image]::FromStream($ms)
        } catch {
            $logoBmp = $null
        }
    }

    # Store Name & Logo Header
    if ($logoBmp -ne $null -and $data.storeName) {
        $origW = [float]$logoBmp.Width
        $origH = [float]$logoBmp.Height
        $scale = [Math]::Min(($logoMaxW / $origW), ($logoMaxH / $origH))
        $logoDrawW = [float]($origW * $scale)
        $logoDrawH = [float]($origH * $scale)

        $storeTitle = [string]$data.storeName
        $titleSize = $g.MeasureString($storeTitle, $fontTitle)
        $gap = 8.0
        $totalHeaderW = $logoDrawW + $gap + $titleSize.Width
        $headerStartX = [float]($margin + [Math]::Max(0, ($contentW - $totalHeaderW) / 2))

        $blockH = [float][Math]::Max($logoDrawH, $titleSize.Height)
        $logoY = [float]($y + ($blockH - $logoDrawH) / 2)
        $titleY = [float]($y + ($blockH - $titleSize.Height) / 2)

        $g.DrawImage($logoBmp, $headerStartX, $logoY, $logoDrawW, $logoDrawH)
        $rTitle = New-Object System.Drawing.RectangleF(($headerStartX + $logoDrawW + $gap), $titleY, ($contentW - ($headerStartX + $logoDrawW + $gap)), ($titleSize.Height + 4))
        $g.DrawString($storeTitle, $fontTitle, $brush, $rTitle)
        $y += $blockH + 4
    } elseif ($logoBmp -ne $null) {
        $origW = [float]$logoBmp.Width
        $origH = [float]$logoBmp.Height
        $scale = [Math]::Min((($logoMaxW * 1.4) / $origW), (($logoMaxH * 1.2) / $origH))
        $logoDrawW = [float]($origW * $scale)
        $logoDrawH = [float]($origH * $scale)
        $logoX = [float]($margin + [Math]::Max(0, ($contentW - $logoDrawW) / 2))
        $g.DrawImage($logoBmp, $logoX, $y, $logoDrawW, $logoDrawH)
        $y += $logoDrawH + 4
    } elseif ($data.storeName) {
        $storeTitle = [string]$data.storeName
        $measured = $g.MeasureString($storeTitle, $fontTitle, [int]$contentW, $sfCenter)
        $h = [float][Math]::Max(24, [Math]::Ceiling($measured.Height) + 2)
        Draw-RectText $g $storeTitle $fontTitle $brush $margin $y $contentW $h $sfCenter
        $y += $h + 2
    }

    # Store Address (8.5pt font)
    if ($data.storeDetails) {
        $lines = $data.storeDetails -split "`n"
        foreach ($l in $lines) {
            $t = $l.Trim()
            if ($t) {
                $measured = $g.MeasureString($t, $fontSmall, [int]$contentW, $sfCenter)
                $h = [float][Math]::Max(14, [Math]::Ceiling($measured.Height) + 1)
                Draw-RectText $g $t $fontSmall $brush $margin $y $contentW $h $sfCenter
                $y += $h
            }
        }
    }

    # Phone: centered, 8.5pt font, bold label
    if ($data.phone) {
        Draw-LabelValueCentered $g "Phone: " $fontSmallBold ([string]$data.phone) $fontSmall $brush $margin $y $contentW 16
        $y += 15
    }

    # GSTIN: centered, 8.5pt font, bold label
    if ($data.gstin) {
        Draw-LabelValueCentered $g "GSTIN: " $fontSmallBold ([string]$data.gstin) $fontSmall $brush $margin $y $contentW 16
        $y += 15
    }

    $y += 3
    $g.DrawLine($pen, [float]$margin, [float]$y, [float]($margin + $contentW), [float]$y)
    $y += 6

    # Bill Metadata
    $metaLeftW  = [float][Math]::Floor($contentW * 0.54)
    $metaRightW = [float]($contentW - $metaLeftW)

    # Bill No & Date (D in Date and T in Time aligned straight in right column)
    if ($data.invoiceNumber -or $data.date) {
        if ($data.invoiceNumber) {
            Draw-LabelValue $g "Bill No: " $fontBold ([string]$data.invoiceNumber) $fontSub $brush $margin $y $metaLeftW 18 $false
        }
        if ($data.date) {
            Draw-LabelValue $g "Date: " $fontBold ([string]$data.date) $fontSub $brush ($margin + $metaLeftW) $y $metaRightW 18 $false
        }
        $y += 17
    }

    # Cust (wraps if long) & Time
    $custVal = if ($data.customerName -and [string]$data.customerName.Trim() -ne "" -and [string]$data.customerName.Trim().ToLower() -ne "walk-in customer") { [string]$data.customerName.Trim() } else { "" }
    $custLabel = "Cust: "
    $custLblSize = $g.MeasureString($custLabel, $fontBold)
    $rCustLbl = New-Object System.Drawing.RectangleF($margin, $y, ($custLblSize.Width + 2), 18)
    $g.DrawString($custLabel, $fontBold, $brush, $rCustLbl)
    $custRowH = 17.0
    if ($custVal) {
        $availCustW = [float][Math]::Max(20, ($metaLeftW - $custLblSize.Width - 4))
        $measuredCust = $g.MeasureString($custVal, $fontSub, [int]$availCustW, $sfWrap)
        $custH = [float][Math]::Max(17, [Math]::Ceiling($measuredCust.Height) + 2)
        $rCustVal = New-Object System.Drawing.RectangleF(($margin + $custLblSize.Width), $y, $availCustW, $custH)
        $g.DrawString($custVal, $fontSub, $brush, $rCustVal, $sfWrap)
        $custRowH = $custH
    }
    if ($data.time) {
        Draw-LabelValue $g "Time: " $fontBold ([string]$data.time) $fontSub $brush ($margin + $metaLeftW) $y $metaRightW 18 $false
    }
    $y += [float][Math]::Max(17, $custRowH)

    # User
    if ($data.billerName) {
        Draw-LabelValue $g "User: " $fontBold ([string]$data.billerName) $fontSub $brush $margin $y $contentW 18 $false
        $y += 17
    }

    $y += 3
    $g.DrawLine($pen, [float]$margin, [float]$y, [float]($margin + $contentW), [float]$y)
    $y += 6

    # Items Header: Item | Qty | Rate | [Tax] | Amt
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
        $colItemW = [float][Math]::Floor($contentW * 0.44)
        $colQtyW  = [float][Math]::Floor($contentW * 0.12)
        $colRateW = [float][Math]::Floor($contentW * 0.22)
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
    Draw-RectText $g "Amt" $fontBold $brush $xAmt $y $colAmtW 18 $sfRight
    $y += 20

    $g.DrawLine($pen, [float]$margin, [float]$y, [float]($margin + $contentW), [float]$y)
    $y += 6

    # Item Rows
    $sym = if ($data.currencySymbol) { $data.currencySymbol } else { "" }

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

    # Totals (Subtotal + Discount ONLY if discountCents > 0, NO extra separator if neither discount nor tax)
    $hasDiscount = ($data.discountCents -gt 0)
    $hasTax = ($data.showTax -and ($data.taxCents -gt 0))
    $hasPreTotal = ($hasDiscount -or $hasTax)

    if ($hasDiscount) {
        $subVal = if ($data.subtotalStr) { [string]$data.subtotalStr } else { [string]::Format("{0:0.00}", (($data.totalCents + $data.discountCents) / 100)) }
        $subStr = if ($sym) { $sym + " " + $subVal } else { $subVal }
        Draw-RectText $g "Subtotal:" $fontBold $brush $margin $y $halfW 18 $sfLeft
        Draw-RectText $g $subStr $fontSub $brush ($margin + $halfW) $y $halfRightW 18 $sfRight
        $y += 18

        $discVal = if ($data.discountStr) { [string]$data.discountStr } else { [string]::Format("{0:0.00}", ($data.discountCents / 100)) }
        $discStr = if ($sym) { "-" + $sym + " " + $discVal } else { "-" + $discVal }
        Draw-RectText $g "Discount:" $fontBold $brush $margin $y $halfW 18 $sfLeft
        Draw-RectText $g $discStr $fontSub $brush ($margin + $halfW) $y $halfRightW 18 $sfRight
        $y += 18
    }

    if ($hasTax) {
        $taxVal = if ($data.taxStr) { [string]$data.taxStr } else { [string]::Format("{0:0.00}", ($data.taxCents / 100)) }
        $taxStr = if ($sym) { $sym + " " + $taxVal } else { $taxVal }
        Draw-RectText $g "Tax Amount:" $fontBold $brush $margin $y $halfW 18 $sfLeft
        Draw-RectText $g $taxStr $fontSub $brush ($margin + $halfW) $y $halfRightW 18 $sfRight
        $y += 18
    }

    if ($hasPreTotal) {
        $y += 2
        $g.DrawLine($pen, [float]$margin, [float]$y, [float]($margin + $contentW), [float]$y)
        $y += 6
    }

    # TOTAL (Bold, matching format: Total:     ₹ 140.00)
    $totVal = if ($data.totalStr) { [string]$data.totalStr } else { [string]::Format("{0:0.00}", ($data.totalCents / 100)) }
    $totStr = if ($sym) { $sym + " " + $totVal } else { $totVal }
    $totLabelW = [float][Math]::Floor($contentW * 0.45)
    $totValW   = [float]($contentW - $totLabelW)
    Draw-RectText $g "Total:" $fontTotal $brush $margin $y $totLabelW 24 $sfLeft
    Draw-RectText $g $totStr $fontTotal $brush ($margin + $totLabelW) $y $totValW 24 $sfRight
    $y += 26

    $y += 2
    $g.DrawLine($pen, [float]$margin, [float]$y, [float]($margin + $contentW), [float]$y)
    $y += 6

    # Footer (wraps if long)
    if ($data.footerText) {
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
