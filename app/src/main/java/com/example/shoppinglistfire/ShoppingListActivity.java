// ShoppingListActivity.java – complet, cu dropdown funcțional pentru „Listele mele” și corecții finale
package com.example.shoppinglistfire;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.content.Intent;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.google.firebase.dynamiclinks.DynamicLink;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.ShortDynamicLink;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ShoppingListActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private DatabaseReference rootRef;
    private List<ShoppingItem> items;
    private ListView listView;
    private EditText itemNameInput, itemDescriptionInput;
    private Button addButton, generateQRButton, scanQRButton, logoutButton, shareButton;
    private String listId;
    private ShoppingListAdapter adapter;
    private static final String DYNAMIC_LINK_DOMAIN = "https://listadecumparaturi.page.link";
    private LinearLayout menuListContainer;
    private SharedPreferences sharedPreferences;
    private ActivityResultLauncher<String> pickImageLauncher;
    private ImageView profileImage;
    private boolean isListExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shopping_list);

        mAuth = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences("ShoppingAppPrefs", MODE_PRIVATE);

        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        NavigationView navigationView = findViewById(R.id.navigationView);
        Button menuButton = findViewById(R.id.menuButton);

        menuButton.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        String userId = mAuth.getUid();
        if (userId == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        rootRef = FirebaseDatabase.getInstance().getReference("shoppingLists");
        items = new ArrayList<>();
        listView = findViewById(R.id.listView);
        itemNameInput = findViewById(R.id.itemNameInput);
        itemDescriptionInput = findViewById(R.id.itemDescriptionInput);
        addButton = findViewById(R.id.addButton);
        generateQRButton = findViewById(R.id.generateQRButton);
        scanQRButton = findViewById(R.id.scanQRButton);
        logoutButton = findViewById(R.id.logoutButton);
        shareButton = findViewById(R.id.shareButton);

        View headerView = navigationView.getHeaderView(0);
        profileImage = headerView.findViewById(R.id.profileImage);
        Button changeProfileButton = headerView.findViewById(R.id.changeProfileButton);
        menuListContainer = headerView.findViewById(R.id.menuListContainer);
        menuListContainer.setVisibility(View.GONE);

        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) saveImageLocally(uri);
        });

        changeProfileButton.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        String profilePath = sharedPreferences.getString("PROFILE_URI", null);
        if (profilePath != null) {
            File file = new File(profilePath);
            if (file.exists() && file.length() > 0) {
                Glide.with(this)
                        .load(file)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(profileImage);
            }
        } else {
            profileImage.setImageResource(R.drawable.user);
        }

        boolean inputsVisible = sharedPreferences.getBoolean("inputsVisible", false);
        itemNameInput.setVisibility(inputsVisible ? View.VISIBLE : View.GONE);
        itemDescriptionInput.setVisibility(inputsVisible ? View.VISIBLE : View.GONE);

        listId = sharedPreferences.getString("LIST_ID", null);
        if (listId != null) {
            rootRef.child(userId).child(listId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        initializeAdapterWithList(listId);
                    } else {
                        createAndInitializeNewList();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    createAndInitializeNewList();
                }
            });
        } else {
            createAndInitializeNewList();
        }

        addButton.setOnClickListener(v -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            if (itemNameInput.getVisibility() == View.GONE) {
                itemNameInput.setVisibility(View.VISIBLE);
                itemDescriptionInput.setVisibility(View.VISIBLE);
                editor.putBoolean("inputsVisible", true);
            } else {
                if (listId == null) {
                    Toast.makeText(this, "Listă inexistentă!", Toast.LENGTH_SHORT).show();
                    return;
                }
                String name = itemNameInput.getText().toString().trim();
                String description = itemDescriptionInput.getText().toString().trim();
                if (!name.isEmpty()) {
                    String itemId = rootRef.child(userId).push().getKey();
                    ShoppingItem newItem = new ShoppingItem(itemId, name, description);
                    rootRef.child(userId).child(listId).child("items").child(itemId).setValue(newItem);
                    itemNameInput.setText("");
                    itemDescriptionInput.setText("");
                    itemNameInput.setVisibility(View.GONE);
                    itemDescriptionInput.setVisibility(View.GONE);
                    editor.putBoolean("inputsVisible", false);
                }
            }
            editor.apply();
        });

        generateQRButton.setOnClickListener(v -> {
            Intent intent = new Intent(ShoppingListActivity.this, QRGenerator.class);
            intent.putExtra("LIST_ID", listId);
            startActivity(intent);
        });

        scanQRButton.setOnClickListener(v -> {
            Intent intent = new Intent(ShoppingListActivity.this, QRScannerActivity.class);
            startActivity(intent);
        });

        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        shareButton.setOnClickListener(v -> showShareOptions());

        navigationView.setNavigationItemSelectedListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.my_lists) {
                isListExpanded = !isListExpanded;
                menuListContainer.setVisibility(isListExpanded ? View.VISIBLE : View.GONE);
                if (isListExpanded) loadUserShoppingLists();
                return true;
            } else if (id == R.id.create_list) {
                showCreateListDialog();
                return true;
            }
            return false;
        });
    }

    private void saveImageLocally(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) throw new IOException("InputStream este null");

            File file = new File(getFilesDir(), "profile.jpg");

            if (file.exists()) file.delete();

            FileOutputStream outputStream = new FileOutputStream(file);

            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }
            inputStream.close();
            outputStream.close();

            if (file.exists() && file.length() > 0) {
                Glide.with(this)
                        .load(file)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(profileImage);
                sharedPreferences.edit().putString("PROFILE_URI", file.getAbsolutePath()).apply();
            } else {
                throw new IOException("Fișierul nu a fost creat corect");
            }
        } catch (Exception e) {
            Log.e("SaveImage", "Eroare la salvarea imaginii", e);
            Toast.makeText(this, "Eroare la salvarea pozei", Toast.LENGTH_SHORT).show();
        }
    }
    private void initializeAdapterWithList(String id) {
        adapter = new ShoppingListAdapter(this, items, rootRef.child(mAuth.getUid()), id);
        listView.setAdapter(adapter);
        loadItemsFromFirebase(id);
    }

    private void loadItemsFromFirebase(String listId) {
        rootRef.child(mAuth.getUid()).child(listId).child("items").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                items.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    ShoppingItem item = child.getValue(ShoppingItem.class);
                    if (item != null) items.add(item);
                }
                if (adapter != null) adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ShoppingListActivity.this, "Eroare la încărcarea produselor.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createNewList(String listName) {
        String newListId = rootRef.child(mAuth.getUid()).push().getKey();
        if (newListId == null) return;

        rootRef.child(mAuth.getUid()).child(newListId).child("name").setValue(listName);
        rootRef.child(mAuth.getUid()).child(newListId).child("participants").child(mAuth.getUid()).setValue(true);

        listId = newListId;
        sharedPreferences.edit().putString("LIST_ID", listId).apply();

        items.clear();
        if (adapter != null) adapter.notifyDataSetChanged();
        loadItemsFromFirebase(listId);

        Toast.makeText(this, "Listă creată: " + listName, Toast.LENGTH_SHORT).show();
    }

    private void createAndInitializeNewList() {
        createNewList("Listă implicită");
    }

    private void loadUserShoppingLists() {
        String uid = mAuth.getUid();
        if (uid == null) return;

        rootRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                menuListContainer.removeAllViews();

                for (DataSnapshot listSnapshot : snapshot.getChildren()) {
                    String listName = listSnapshot.child("name").getValue(String.class);
                    String id = listSnapshot.getKey();

                    if (listName != null && id != null) {
                        TextView listItem = new TextView(ShoppingListActivity.this);
                        listItem.setText(listName);
                        listItem.setTextSize(16);
                        listItem.setPadding(24, 16, 24, 16);
                        listItem.setTextColor(Color.BLACK);
                        listItem.setOnClickListener(v -> {
                            listId = id;
                            sharedPreferences.edit().putString("LIST_ID", listId).apply();
                            initializeAdapterWithList(listId);
                        });
                        menuListContainer.addView(listItem);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Eroare la încărcarea listelor: " + error.getMessage());
                Toast.makeText(ShoppingListActivity.this, "Eroare: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showCreateListDialog() {
        EditText input = new EditText(this);
        input.setHint("Nume listă");

        new AlertDialog.Builder(this)
                .setTitle("Creează listă nouă")
                .setView(input)
                .setPositiveButton("Creează", (dialog, which) -> {
                    String listName = input.getText().toString().trim();
                    if (!listName.isEmpty()) createNewList(listName);
                })
                .setNegativeButton("Anulează", null)
                .show();
    }

    private void showShareOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Alege metoda de partajare")
                .setItems(new String[]{"Partajare ca text", "Partajare cu link"}, (dialog, which) -> {
                    if (which == 0) shareListAsText();
                    else shareListWithDynamicLink();
                }).show();
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
}
