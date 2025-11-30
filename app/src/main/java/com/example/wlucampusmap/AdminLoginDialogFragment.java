package com.example.wlucampusmap;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class AdminLoginDialogFragment extends DialogFragment {
    private AdminManager adminManager;
    private LoginListener loginListener;

    public interface LoginListener {
        void onLoginResult(boolean success);
    }

    public void setAdminManager(AdminManager adminManager) {
        this.adminManager = adminManager;
    }

    public void setLoginListener(LoginListener loginListener) {
        this.loginListener = loginListener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_admin_login, null);

        EditText usernameInput = view.findViewById(R.id.edit_admin_username);
        EditText passwordInput = view.findViewById(R.id.edit_admin_password);

        // Set input types
        usernameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        return new AlertDialog.Builder(requireContext())
                .setTitle("Admin Login")
                .setView(view)
                .setPositiveButton("Login", (DialogInterface dialog, int which) -> {
                    String username = usernameInput.getText().toString().trim();
                    String password = passwordInput.getText().toString();
                    boolean success = adminManager != null && adminManager.login(username, password);
                    if (loginListener != null) loginListener.onLoginResult(success);
                })
                .setNegativeButton("Cancel", (d, w) -> {
                    if (loginListener != null) loginListener.onLoginResult(false);
                    dismiss();
                })
                .create();
    }
}