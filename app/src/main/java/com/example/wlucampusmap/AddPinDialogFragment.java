package com.example.wlucampusmap;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

/**
 * Lets the admin add a pin to map.
 * Returns the RoomPin object with correct fields populated.
 */
public class AddPinDialogFragment extends DialogFragment {

    public interface OnPinAddedListener {
        void onPinAdded(RoomPin roomPin);
    }

    private OnPinAddedListener listener;

    public void setOnPinAddedListener(OnPinAddedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        EditText roomNameInput = new EditText(requireContext());
        roomNameInput.setHint("Room Name");

        EditText roomTypeInput = new EditText(requireContext());
        roomTypeInput.setHint("Room Type");

        EditText descriptionInput = new EditText(requireContext());
        descriptionInput.setHint("Description");

        // Simple vertical layout
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 15, 40, 10);
        layout.addView(roomNameInput);
        layout.addView(roomTypeInput);
        layout.addView(descriptionInput);

        return new AlertDialog.Builder(requireContext())
                .setTitle("Add Room Pin")
                .setView(layout)
                .setPositiveButton("OK", (dialog, which) -> {
                    String name = roomNameInput.getText().toString().trim();
                    String type = roomTypeInput.getText().toString().trim();
                    String desc = descriptionInput.getText().toString().trim();

                    RoomPin pin = new RoomPin();
                    pin.setRoomName(name);
                    pin.setRoomType(type);
                    pin.setDescription(desc);
                    pin.setXCoordinate(0.5f); // Default, should be set from map touch in real use
                    pin.setYCoordinate(0.5f);
                    pin.setFloor(1); // Default, override as needed
                    if (listener != null) listener.onPinAdded(pin);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {})
                .create();
    }
}