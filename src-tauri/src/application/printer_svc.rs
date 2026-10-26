use std::process::Command;
use crate::errors::AppError;

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

    pub fn test_print(printer_name: Option<String>) -> Result<(), AppError> {
        let printers = Self::list_printers();
        if printers.is_empty() {
            return Err(AppError::Printer("No system printers detected".to_string()));
        }

        let target = printer_name
            .filter(|p| !p.trim().is_empty())
            .or_else(|| printers.first().cloned())
            .ok_or_else(|| AppError::Printer("No printer selected".to_string()))?;

        #[cfg(not(target_os = "windows"))]
        {
            // Send simple test banner via `lp -d <target>`
            let test_content = b"================================\n         PICO POSSUM POS        \n        PRINTER TEST PRINT      \n================================\nStatus: Online & Functional\nDate: Test Verification\n================================\n\n\n\n";
            let mut child = Command::new("lp")
                .args(["-d", &target])
                .stdin(std::process::Stdio::piped())
                .stdout(std::process::Stdio::piped())
                .stderr(std::process::Stdio::piped())
                .spawn()
                .map_err(|e| AppError::Printer(format!("Failed to execute lp command: {}", e)))?;

            if let Some(mut stdin) = child.stdin.take() {
                use std::io::Write;
                let _ = stdin.write_all(test_content);
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
            // On Windows, return Ok (WebView print also handles it)
        }

        Ok(())
    }
}
