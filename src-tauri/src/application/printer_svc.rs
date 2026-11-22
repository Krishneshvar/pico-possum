use std::process::Command;
use crate::errors::AppError;
#[cfg(target_os = "windows")]
use super::thermal_print_script;

pub struct PrinterService;

impl PrinterService {
    pub fn list_printers() -> Vec<String> {
        let mut printers = Vec::new();

        #[cfg(target_os = "windows")]
        {
            if let Ok(output) = Command::new("powershell")
                .args(["-NoProfile", "-Command", "Get-CimInstance Win32_Printer | Select-Object -ExpandProperty Name"])
                .output()
            {
                if output.status.success() {
                    let text = String::from_utf8_lossy(&output.stdout);
                    for line in text.lines() {
                        let trimmed = line.trim();
                        if !trimmed.is_empty() {
                            printers.push(trimmed.to_string());
                        }
                    }
                }
            }
        }

        #[cfg(not(target_os = "windows"))]
        {
            // First try `lpstat -e` (lists printer destination names cleanly)
            if let Ok(output) = Command::new("lpstat").arg("-e").output() {
                if output.status.success() {
                    let text = String::from_utf8_lossy(&output.stdout);
                    for line in text.lines() {
                        let trimmed = line.trim();
                        if !trimmed.is_empty() {
                            printers.push(trimmed.to_string());
                        }
                    }
                }
            }

            // If empty, try `lpstat -p` (lines start with "printer <name>")
            if printers.is_empty() {
                if let Ok(output) = Command::new("lpstat").arg("-p").output() {
                    if output.status.success() {
                        let text = String::from_utf8_lossy(&output.stdout);
                        for line in text.lines() {
                            let trimmed = line.trim();
                            if trimmed.starts_with("printer ") {
                                if let Some(name) = trimmed.strip_prefix("printer ") {
                                    if let Some(space_idx) = name.find(' ') {
                                        printers.push(name[..space_idx].to_string());
                                    } else {
                                        printers.push(name.to_string());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        printers.sort_by(|a, b| a.to_lowercase().cmp(&b.to_lowercase()));
        printers.dedup();
        printers
    }

    pub fn test_print(printer_name: Option<String>, paper_width: Option<String>) -> Result<(), AppError> {
        let printers = Self::list_printers();
        let target = printer_name
            .filter(|p| !p.trim().is_empty())
            .or_else(|| printers.first().cloned())
            .ok_or_else(|| AppError::Printer("No printer selected".to_string()))?;

        #[cfg(not(target_os = "windows"))]
        {
            let is_58 = paper_width.as_deref() == Some("58mm");
            let test_content = if is_58 {
                "================================\n        PICO POSSUM POS         \n      THERMAL TEST (58mm)       \n================================\nStatus: Online & Ready\nFont: Times New Roman\n================================\nItem        Qty    Rate   Amount\nSample Test   1  100.00   100.00\n================================\nTOTAL:                    100.00\n================================\nThermal Print Configuration OK!\n\n\n\n"
            } else {
                "==========================================\n             PICO POSSUM POS              \n           THERMAL PRINTER TEST           \n==========================================\nStatus: Online & Ready\nFont: Times New Roman\n==========================================\nItem                     Qty   Rate Amount\nSample Product Test        1 100.00 100.00\n==========================================\nTOTAL:                              100.00\n==========================================\n     Thermal Print Configuration OK!      \n\n\n\n"
            };

            let mut child = Command::new("lp")
                .args(["-d", &target])
                .stdin(std::process::Stdio::piped())
                .stdout(std::process::Stdio::piped())
                .stderr(std::process::Stdio::piped())
                .spawn()
                .map_err(|e| AppError::Printer(format!("Failed to execute lp command: {}", e)))?;

            if let Some(mut stdin) = child.stdin.take() {
                use std::io::Write;
                let _ = stdin.write_all(test_content.as_bytes());
            }

            let output = child.wait_with_output()
                .map_err(|e| AppError::Printer(format!("Printer command wait error: {}", e)))?;

            if !output.status.success() {
                let err_msg = String::from_utf8_lossy(&output.stderr);
                return Err(AppError::Printer(format!("Printer returned error: {}", err_msg)));
            }
        }

        #[cfg(target_os = "windows")]
        {
            let script_path = std::env::temp_dir().join(format!("pos_test_print_{}.ps1", uuid::Uuid::new_v4()));
            let _ = std::fs::write(&script_path, thermal_print_script::TEST_PRINT_SCRIPT);

            let paper_width_val = paper_width.unwrap_or_else(|| "80mm".to_string());

            let mut cmd = Command::new("powershell");
            cmd.args(["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass"]);
            cmd.arg("-File").arg(&script_path);
            cmd.arg("-Target").arg(&target);
            cmd.arg("-PaperWidth").arg(&paper_width_val);

            let output = cmd.output()
                .map_err(|e| AppError::Printer(format!("Failed to execute Windows test print: {}", e)));

            let _ = std::fs::remove_file(&script_path);

            let output = output?;
            if !output.status.success() {
                let err_msg = String::from_utf8_lossy(&output.stderr);
                return Err(AppError::Printer(format!("Windows test print error: {}", err_msg)));
            }
        }

        Ok(())
    }

    pub fn print_receipt(printer_name: Option<String>, content: &str) -> Result<(), AppError> {
        let trimmed_name = printer_name.as_deref().map(str::trim).filter(|s| !s.is_empty());

        #[cfg(not(target_os = "windows"))]
        {
            let printers = Self::list_printers();
            let target = trimmed_name
                .or_else(|| printers.first().map(|s| s.as_str()))
                .ok_or_else(|| AppError::Printer("No system printer available".to_string()))?;

            let mut child = Command::new("lp")
                .args(["-d", target])
                .stdin(std::process::Stdio::piped())
                .stdout(std::process::Stdio::piped())
                .stderr(std::process::Stdio::piped())
                .spawn()
                .map_err(|e| AppError::Printer(format!("Failed to execute lp command: {}", e)))?;

            let print_data = if content.trim_start().starts_with('{') {
                if let Ok(v) = serde_json::from_str::<serde_json::Value>(content) {
                    v.get("plainText")
                        .and_then(|pt| pt.as_str())
                        .map(ToOwned::to_owned)
                        .unwrap_or_else(|| content.to_string())
                } else {
                    content.to_string()
                }
            } else {
                content.to_string()
            };

            if let Some(mut stdin) = child.stdin.take() {
                use std::io::Write;
                let _ = stdin.write_all(print_data.as_bytes());
            }

            let output = child.wait_with_output()
                .map_err(|e| AppError::Printer(format!("Printer command wait error: {}", e)))?;

            if !output.status.success() {
                let err_msg = String::from_utf8_lossy(&output.stderr);
                return Err(AppError::Printer(format!("Printer returned error: {}", err_msg)));
            }
        }

        #[cfg(target_os = "windows")]
        {
            let target_str = trimmed_name.unwrap_or_default();
            let is_json = content.trim_start().starts_with('{');

            if is_json {
                let unique_id = uuid::Uuid::new_v4();
                let script_path = std::env::temp_dir().join(format!("pos_receipt_{}.ps1", unique_id));
                let data_path = std::env::temp_dir().join(format!("pos_data_{}.json", unique_id));

                let _ = std::fs::write(&script_path, thermal_print_script::RECEIPT_PRINT_SCRIPT);
                let _ = std::fs::write(&data_path, content.as_bytes());

                let mut cmd = Command::new("powershell");
                cmd.args(["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass"]);
                cmd.arg("-File").arg(&script_path);
                cmd.arg("-Target").arg(target_str);
                cmd.arg("-DataFile").arg(&data_path);

                let output = cmd.output()
                    .map_err(|e| AppError::Printer(format!("Failed to execute Windows receipt print: {}", e)));

                let _ = std::fs::remove_file(&script_path);
                let _ = std::fs::remove_file(&data_path);

                let output = output?;
                if !output.status.success() {
                    let err_msg = String::from_utf8_lossy(&output.stderr);
                    return Err(AppError::Printer(format!("Windows receipt print error: {}", err_msg)));
                }
            } else {
                let unique_id = uuid::Uuid::new_v4();
                let script_path = std::env::temp_dir().join(format!("pos_text_print_{}.ps1", unique_id));
                let data_path = std::env::temp_dir().join(format!("pos_text_{}.txt", unique_id));

                let text_script = r#"param([string]$Target = "", [string]$DataFile = "")
Add-Type -AssemblyName System.Drawing
$doc = New-Object System.Drawing.Printing.PrintDocument
if ($Target -and $Target.Trim() -ne '') { $doc.PrinterSettings.PrinterName = $Target.Trim() }
$doc.DefaultPageSettings.Margins = New-Object System.Drawing.Printing.Margins(0, 0, 0, 0)
$doc.OriginAtMargins = $false
$text = Get-Content $DataFile -Raw -Encoding UTF8
$doc.add_PrintPage({
    param($s, $e)
    $font = New-Object System.Drawing.Font("Times New Roman", 9.5)
    $brush = [System.Drawing.Brushes]::Black
    $rect = New-Object System.Drawing.RectangleF(6, 8, 280, $e.PageBounds.Height)
    $sf = New-Object System.Drawing.StringFormat
    $e.Graphics.DrawString($text, $font, $brush, $rect, $sf)
    $e.HasMorePages = $false
})
$doc.Print()
"#;
                let _ = std::fs::write(&script_path, text_script);
                let _ = std::fs::write(&data_path, content.as_bytes());

                let mut cmd = Command::new("powershell");
                cmd.args(["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass"]);
                cmd.arg("-File").arg(&script_path);
                cmd.arg("-Target").arg(target_str);
                cmd.arg("-DataFile").arg(&data_path);

                let output = cmd.output()
                    .map_err(|e| AppError::Printer(format!("Failed to execute Windows text print: {}", e)));

                let _ = std::fs::remove_file(&script_path);
                let _ = std::fs::remove_file(&data_path);

                let output = output?;
                if !output.status.success() {
                    let err_msg = String::from_utf8_lossy(&output.stderr);
                    return Err(AppError::Printer(format!("Windows text print error: {}", err_msg)));
                }
            }
        }

        Ok(())
    }
}
