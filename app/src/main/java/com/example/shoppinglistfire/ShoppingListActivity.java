package com.example.shoppinglistfire;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.ArrayList;
import java.util.List;


import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.DynamicLink;
import com.google.firebase.dynamiclinks.ShortDynamicLink;

public class ShoppingListActivity extends AppCompatActivity {

    private DatabaseReference database;
    private List<ShoppingItem> items;
    private ListView listView;
    private EditText itemNameInput, itemDescriptionInput;
    private Button addButton, generateQRButton, scanQRButton, logoutButton, shareButton ;
    private String listId;
    private ShoppingListAdapter adapter; // Adapter personalizat
    private static final String DYNAMIC_LINK_DOMAIN = "https://listadecumparaturi.page.link";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shopping_list);






        String userId = FirebaseAuth.getInstance().getUid();
        //Log.d("DEBUG", "UserId: " + userId); // Verifică id-ul utilizatorului
        if (userId == null) {
            startActivity(new Intent(ShoppingListActivity.this, LoginActivity.class));
            finish();
            return;
        }
        database = FirebaseDatabase.getInstance().getReference("shoppingLists").child(userId);
        items = new ArrayList<>();
        listView = findViewById(R.id.listView);
        itemNameInput = findViewById(R.id.itemNameInput);
        itemDescriptionInput = findViewById(R.id.itemDescriptionInput);
        addButton = findViewById(R.id.addButton);
        generateQRButton = findViewById(R.id.generateQRButton);
        scanQRButton = findViewById(R.id.scanQRButton);
        logoutButton = findViewById(R.id.logoutButton);
        shareButton = findViewById(R.id.shareButton);

        SharedPreferences sharedPreferences = getSharedPreferences("ShoppingAppPrefs", MODE_PRIVATE);
        boolean inputsVisible = sharedPreferences.getBoolean("inputsVisible", false);

        listId = sharedPreferences.getString("LIST_ID", null);

        if (inputsVisible) {
            itemNameInput.setVisibility(View.VISIBLE);
            itemDescriptionInput.setVisibility(View.VISIBLE);
        } else {
            itemNameInput.setVisibility(View.GONE);
            itemDescriptionInput.setVisibility(View.GONE);
        }


        // Obținem ID-ul listei din intent sau generăm unul nou
        //listId = getIntent().getStringExtra("LIST_ID");
        // Log.d("DEBUG", "listId citit din Intent: " + listId);

        if (listId == null) {
            listId = database.push().getKey();
            //Log.e("DEBUG", "listId este null! Verificați dacă este transmis corect!");

            sharedPreferences.edit().putString("LIST_ID", listId).apply();

            //listId = database.push().getKey(); // Generează un ID pentru listă dacă nu este specificat
            // Log.d("DEBUG", "listId generat automat: " + listId); // Verifică dacă este generat corect
        }

        Log.d("DEBUG", "ListId înainte de citire: " + listId);

        // Inițializăm adapterul după ce listId este setat corect
        adapter = new ShoppingListAdapter(this, items, database, listId);
        listView.setAdapter(adapter);
        Log.d("DEBUG", "ListId dupa initializarea adapterului: " + listId);

        // Ascultăm modificările din Firebase și actualizăm lista locală

        database.child(listId).child("items").addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("DEBUG", "ListId onDataChange1" + listId);
                Log.d("DEBUG", "onDataChange triggered");
                items.clear();
                Log.d("DEBUG", "ListId onDataChange2" + listId);
                if (snapshot.exists()) {
                    Log.d("DEBUG", "Snapshot children count: " + snapshot.getChildrenCount());
                    for (DataSnapshot child : snapshot.getChildren()) {
                        Log.d("DEBUG", "Key: " + child.getKey() + ", Value: " + child.getValue());
                        ShoppingItem item = child.getValue(ShoppingItem.class);
                        if (item != null) {
                            Log.d("DEBUG", "ListId onDataChange3" + listId);
                           Log.d("DEBUG", "Item citit: " + item.toString());
                            items.add(item);
                        }
                    }
                    Log.d("DEBUG", "Items încărcate: " + items.size());
                    adapter.notifyDataSetChanged(); // Actualizăm UI-ul
                } else {
                    Log.d("DEBUG", "Nu au fost găsite produse.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ShoppingListActivity.this, "Eroare la încărcarea datelor.", Toast.LENGTH_SHORT).show();
                Log.e("DEBUG", "Eroare la citirea din Firebase: " + error.getMessage());
            }
        });

        addButton.setOnClickListener(v -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();


            if (itemNameInput.getVisibility() == View.GONE) {
                itemNameInput.setVisibility(View.VISIBLE);
                itemDescriptionInput.setVisibility(View.VISIBLE);
                editor.putBoolean("inputsVisible", true); // Salvează starea ca vizibile
            } else {
                String name = itemNameInput.getText().toString().trim();
                String description = itemDescriptionInput.getText().toString().trim();

                if (!name.isEmpty()) {
                    String itemId = database.push().getKey();
                    ShoppingItem newItem = new ShoppingItem(itemId, name, description);

                    database.child(listId).child("items").child(itemId).setValue(newItem)
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    Log.d("DEBUG", "Item adăugat cu succes!");
                                } else {
                                    Log.e("DEBUG", "Eroare la salvare", task.getException());
                                }
                            });

                    // Golește câmpurile și ascunde-le
                    itemNameInput.setText("");
                    itemDescriptionInput.setText("");
                    itemNameInput.setVisibility(View.GONE);
                    itemDescriptionInput.setVisibility(View.GONE);
                    editor.putBoolean("inputsVisible", false); // Salvează starea ca ascunse
                }
            }
            editor.apply(); // Aplică schimbările în SharedPreferences
        });
        // Generare QR Code pentru partajare
        generateQRButton.setOnClickListener(v -> {
            Intent intent = new Intent(ShoppingListActivity.this, QRGenerator.class);
            intent.putExtra("LIST_ID", listId);
            startActivity(intent);
        });

        // Scanare QR Code pentru a prelua o listă
        scanQRButton.setOnClickListener(v -> {
            Intent intent = new Intent(ShoppingListActivity.this, QRScannerActivity.class);
            startActivity(intent);
        });

        // Logout
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        shareButton.setOnClickListener(v -> showShareOptions());
    }
    private void showShareOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Alege metoda de partajare")
                .setItems(new String[]{"Partajare ca text", "Partajare cu link"}, (dialog, which) -> {
                    if (which == 0) {
                        shareListAsText();
                    } else if (which == 1) {
                        shareListWithDynamicLink();
                    }
                })
                .show();
    }

    private void shareListAsText() {
        if (items.isEmpty()) {
            Toast.makeText(this, "Lista este goală!", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder shoppingListText = new StringBuilder("Lista mea de cumpărături:\n");
        for (ShoppingItem item : items) {
            shoppingListText.append("• ").append(item.getName());
            if (!item.getDescription().isEmpty()) {
                shoppingListText.append(" - ").append(item.getDescription());
            }
            shoppingListText.append("\n");
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shoppingListText.toString());
        startActivity(Intent.createChooser(shareIntent, "Trimite lista prin"));
    }

    private void shareListWithDynamicLink() {
        String deepLink = DYNAMIC_LINK_DOMAIN + "?LIST_ID=" + listId;
        FirebaseDynamicLinks.getInstance().createDynamicLink()
                .setLink(Uri.parse(deepLink))
                .setDomainUriPrefix(DYNAMIC_LINK_DOMAIN)
                .setAndroidParameters(new DynamicLink.AndroidParameters.Builder().build())
                .buildShortDynamicLink()
                .addOnSuccessListener(shortDynamicLink -> {
                    Uri shortLink = shortDynamicLink.getShortLink();
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, "Deschide lista mea de cumpărături: " + shortLink.toString());
                    startActivity(Intent.createChooser(shareIntent, "Trimite link-ul prin"));
                })
                .addOnFailureListener(e -> Log.e("DynamicLink", "Eroare generare link", e));
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseDynamicLinks.getInstance()
                .getDynamicLink(getIntent())
                .addOnSuccessListener(this, pendingDynamicLinkData -> {
                    if (pendingDynamicLinkData != null) {
                        Uri deepLink = pendingDynamicLinkData.getLink();
                        if (deepLink != null) {
                            String receivedListId = deepLink.getQueryParameter("LIST_ID");
                            if (receivedListId != null) {
                                listId = receivedListId;
                                SharedPreferences sharedPreferences = getSharedPreferences("ShoppingAppPrefs", MODE_PRIVATE);
                                sharedPreferences.edit().putString("LIST_ID", listId).apply();

                                // Adaugă utilizatorul curent la lista partajată
                                String userId = FirebaseAuth.getInstance().getUid();
                                if (userId != null) {
                                    database.child(listId).child("participants").child(userId).setValue(true);
                                }

                                database.child(listId).child("items").addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        items.clear();
                                        for (DataSnapshot child : snapshot.getChildren()) {
                                            ShoppingItem item = child.getValue(ShoppingItem.class);
                                            if (item != null) {
                                                items.add(item);
                                            }
                                        }
                                        adapter.notifyDataSetChanged();
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        Log.e("Firebase", "Eroare la citirea listei partajate", error.toException());
                                    }
                                });
                            }
                        }
                    }
                });
    }
}