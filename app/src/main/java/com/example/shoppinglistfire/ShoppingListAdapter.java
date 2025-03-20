package com.example.shoppinglistfire;

import android.app.AlertDialog;
import android.content.Context;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import com.google.firebase.database.DatabaseReference;


import java.util.List;

public class ShoppingListAdapter extends ArrayAdapter<ShoppingItem> {
    private Context context;
    private List<ShoppingItem> items;
    private DatabaseReference database;
    private String listId; // ID-ul listei pentru Firebase

    public ShoppingListAdapter(Context context, List<ShoppingItem> items, DatabaseReference database, String listId) {
        super(context, 0, items);
        this.context = context;
        this.items = items;
        this.database = database;
        this.listId = listId;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_item, parent, false);
        }

        ShoppingItem item = getItem(position);
        if (item == null) return convertView;

        TextView itemName = convertView.findViewById(R.id.itemName);
        TextView itemDescription = convertView.findViewById(R.id.itemDescription);
        Button editButton = convertView.findViewById(R.id.editButton);
        Button deleteButton = convertView.findViewById(R.id.deleteButton);

        itemName.setText(item.getName());
        itemDescription.setText(item.getDescription());

        // Buton de editare
        editButton.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("Editează produsul");

            // Creăm un input pentru nume și descriere
            View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_item, null);
            EditText nameInput = dialogView.findViewById(R.id.editItemName);
            EditText descriptionInput = dialogView.findViewById(R.id.editItemDescription);

            nameInput.setText(item.getName());
            descriptionInput.setText(item.getDescription());

            builder.setView(dialogView);

            builder.setPositiveButton("Salvează", (dialog, which) -> {
                if (listId == null || item.getId() == null) {
                    Log.e("DEBUG", "listId sau itemId este null!"); // Debugging
                    return;
                }

                String newName = nameInput.getText().toString().trim();
                String newDescription = descriptionInput.getText().toString().trim();

                if (!newName.isEmpty()) {
                    item.setName(newName);
                    item.setDescription(newDescription);

                    database.child(listId).child(item.getId()).setValue(item)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(context, "Produs actualizat!", Toast.LENGTH_SHORT).show();
                                notifyDataSetChanged();
                            })
                            .addOnFailureListener(e -> {
                                Log.e("DEBUG", "Eroare la actualizare!", e);
                                Toast.makeText(context, "Eroare la actualizare!", Toast.LENGTH_SHORT).show();
                            });
                }
            });

            builder.setNegativeButton("Anulează", (dialog, which) -> dialog.cancel());
            builder.show();
        });

        // Buton de ștergere
        deleteButton.setOnClickListener(v -> {
            if (listId == null || item.getId() == null) {
                Log.e("DEBUG", "listId sau itemId este null!"); // Debugging
                return;
            }

            database.child(listId).child(item.getId()).removeValue()
                    .addOnSuccessListener(aVoid -> {
                        // Ștergem în siguranță itemul din listă
                        for (int i = 0; i < items.size(); i++) {
                            if (items.get(i).getId().equals(item.getId())) {
                                items.remove(i);
                                break; // Oprire după prima potrivire
                            }
                        }

                        notifyDataSetChanged(); // Actualizare UI
                        Toast.makeText(context, "Produs șters!", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e("DEBUG", "Eroare la ștergere: ", e);
                        Toast.makeText(context, "Eroare la ștergere!", Toast.LENGTH_SHORT).show();
                    });
        });

        return convertView;
    }
}