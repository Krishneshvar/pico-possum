use std::process::Command;

#[tauri::command]
pub async fn prompt_save_file_location(
    default_filename: String,
    extension: String,
) -> Result<Option<String>, String> {
    #[cfg(target_os = "linux")]
    {
        let download_dir = dirs::download_dir()
            .or_else(dirs::document_dir)
            .unwrap_or_else(|| std::path::PathBuf::from("."));
        let full_default = download_dir.join(&default_filename);
        let full_default_str = full_default.to_string_lossy().to_string();
        let output = Command::new("zenity")
            .arg("--file-selection")
            .arg("--save")
            .arg("--confirm-overwrite")
            .arg(format!("--filename={}", full_default_str))
            .arg(format!("--file-filter=*.{}", extension))
            .output();

        if let Ok(out) = output {
            if out.status.success() {
                let path = String::from_utf8_lossy(&out.stdout).trim().to_string();
                if !path.is_empty() {
                    let final_path = if !path.ends_with(&format!(".{}", extension)) {
                        format!("{}.{}", path, extension)
                    } else {
                        path
                    };
                    return Ok(Some(final_path));
                }
            } else if out.status.code() == Some(1) {
                // User pressed Cancel in zenity (exit status 1)
                return Ok(None);
            }
        }

        let kdialog_output = Command::new("kdialog")
            .arg("--getsavefilename")
            .arg(&full_default_str)
            .arg(format!("*.{}", extension))
            .output();

        if let Ok(out) = kdialog_output {
            if out.status.success() {
                let path = String::from_utf8_lossy(&out.stdout).trim().to_string();
                if !path.is_empty() {
                    let final_path = if !path.ends_with(&format!(".{}", extension)) {
                        format!("{}.{}", path, extension)
                    } else {
                        path
                    };
                    return Ok(Some(final_path));
                }
            } else if out.status.code() == Some(1) {
                return Ok(None);
            }
        }

        // Neither zenity nor kdialog was found or succeeded.
        // Returning an error triggers frontend fallback to browser download/picker.
        return Err("No native Linux dialog utility (zenity or kdialog) available".to_string());
    }

    #[cfg(target_os = "macos")]
    {
        let safe_name = default_filename.replace('\\', "\\\\").replace('"', "\\\"");
        let script = format!(
            "set f to choose file name with prompt \"Save file as:\" default name \"{}\"\nPOSIX path of f",
            safe_name
        );
        let output = Command::new("osascript")
            .arg("-e")
            .arg(&script)
            .output();

        if let Ok(out) = output {
            if out.status.success() {
                let path = String::from_utf8_lossy(&out.stdout).trim().to_string();
                if !path.is_empty() {
                    let final_path = if !path.ends_with(&format!(".{}", extension)) {
                        format!("{}.{}", path, extension)
                    } else {
                        path
                    };
                    return Ok(Some(final_path));
                }
            } else {
                let stderr = String::from_utf8_lossy(&out.stderr);
                if stderr.contains("User canceled") || stderr.contains("-128") {
                    return Ok(None);
                }
                return Err(format!("macOS dialog error: {}", stderr));
            }
        } else {
            return Err("Failed to execute osascript for save file dialog".to_string());
        }
    }

    #[cfg(target_os = "windows")]
    {
        let safe_name = default_filename.replace('"', "");
        let safe_ext = extension.replace('"', "").replace('*', "");
        let script = format!(
            r#"[System.Reflection.Assembly]::LoadWithPartialName("System.Windows.Forms") | Out-Null;
            $f = New-Object System.Windows.Forms.SaveFileDialog;
            $f.FileName = "{}";
            $f.Filter = "Files (*.{})|*.{}";
            if ($f.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {{
                Write-Output $f.FileName
            }}"#,
            safe_name, safe_ext, safe_ext
        );
        let output = Command::new("powershell")
            .args(["-NoProfile", "-Command", &script])
            .output();

        if let Ok(out) = output {
            if out.status.success() {
                let path = String::from_utf8_lossy(&out.stdout).trim().to_string();
                if !path.is_empty() {
                    return Ok(Some(path));
                }
                return Ok(None);
            } else {
                return Ok(None);
            }
        } else {
            return Err("Failed to execute PowerShell save file dialog".to_string());
        }
    }

    #[allow(unreachable_code)]
    Ok(None)
}

#[tauri::command]
pub async fn save_file_to_disk(file_path: String, contents: Vec<u8>) -> Result<(), String> {
    tokio::fs::write(&file_path, contents)
        .await
        .map_err(|e| format!("Failed to write file to {}: {}", file_path, e))
}

