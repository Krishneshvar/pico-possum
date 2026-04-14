package com.possum.ui.common.dialogs;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Window;

public class BillPreviewDialog extends Dialog<Void> {

    public BillPreviewDialog(String htmlContent, Window owner) {
        setTitle("Bill Preview");
        initOwner(owner);

        WebView webView = new WebView();
        webView.getEngine().loadContent(htmlContent);
        webView.setPrefSize(400, 600);
        webView.getStyleClass().add("bill-preview-webview");

        VBox content = new VBox(webView);
        content.setPadding(new Insets(10));
        content.getStyleClass().add("bill-preview-dialog-content");
        
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        getDialogPane().getStyleClass().add("bill-preview-dialog");
        DialogStyler.apply(this);
        
        setResizable(true);
    }
}
