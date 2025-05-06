package com.example.shoppinglistfire;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.dynamiclinks.DynamicLink;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShoppingListActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private DatabaseReference rootRef;
    private List<ShoppingItem> items;
    private ShoppingListAdapter adapter;
    private ListView listView;
    private EditText itemNameInput, itemDescriptionInput;
    private Button addButton, generateQRButton, scanQRButton, logoutButton, shareButton;
    private ImageView profileImage;
    private SharedPreferences sharedPreferences;
    private ActivityResultLauncher<String> pickImageLauncher;
    private String listId;
    private static final String DYNAMIC_LINK_DOMAIN = "https://listadecumparaturi.page.link";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shopping_list);

        // Initialize Firebase Auth and SharedPreferences
        mAuth = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences("ShoppingAppPrefs", MODE_PRIVATE);
        rootRef = FirebaseDatabase.getInstance().getReference("shoppingLists");

        // Ensure user is logged in
        String uid = mAuth.getUid();
        if (uid == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // DrawerLayout and menu button
        DrawerLayout drawer = findViewById(R.id.drawerLayout);
        findViewById(R.id.menuButton).setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));

        // Bind main UI elements
        listView            = findViewById(R.id.listView);
        itemNameInput       = findViewById(R.id.itemNameInput);
        itemDescriptionInput= findViewById(R.id.itemDescriptionInput);
        addButton           = findViewById(R.id.addButton);
        generateQRButton    = findViewById(R.id.generateQRButton);
        scanQRButton        = findViewById(R.id.scanQRButton);
        shareButton         = findViewById(R.id.shareButton);
        logoutButton        = findViewById(R.id.logoutButton);

        // Hide input fields initially
        itemNameInput.setVisibility(View.GONE);
        itemDescriptionInput.setVisibility(View.GONE);

        // Logout button logic
        logoutButton.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // Drawer custom views
        profileImage        = findViewById(R.id.profileImage);
        Button changePic    = findViewById(R.id.changeProfileButton);
        TextView btnMyLists = findViewById(R.id.btnMyLists);
        TextView btnCreate  = findViewById(R.id.btnCreateList);
        LinearLayout listsBox = findViewById(R.id.listsContainer);

        // Initialize items list and adapter
        items = new ArrayList<>();
        adapter = new ShoppingListAdapter(this, items, rootRef.child(uid), null);
        listView.setAdapter(adapter);

        // Image picker launcher
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) saveImageLocally(uri);
        });
        changePic.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        // Load saved profile image if exists
        String profPath = sharedPreferences.getString("PROFILE_URI", null);
        if (profPath != null && new File(profPath).exists()) {
            Glide.with(this)
                    .load(new File(profPath))
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(profileImage);
        }

        // Restore or create default list
        listId = sharedPreferences.getString("LIST_ID", null);
        if (listId == null) createAndInitializeNewList();
        else initializeAdapterWithList(listId);

        // Add button toggles input fields / adds product
        addButton.setOnClickListener(v -> {
            if (itemNameInput.getVisibility() == View.GONE) {
                itemNameInput.setVisibility(View.VISIBLE);
                itemDescriptionInput.setVisibility(View.VISIBLE);
            } else {
                addProduct(uid);
                itemNameInput.setVisibility(View.GONE);
                itemDescriptionInput.setVisibility(View.GONE);
            }
        });

        // Generate QR code
        generateQRButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, QRGenerator.class);
            intent.putExtra("LIST_ID", listId);
            startActivity(intent);
        });

        // Scan QR code
        scanQRButton.setOnClickListener(v -> startActivity(new Intent(this, QRScannerActivity.class)));

        // Share list
        shareButton.setOnClickListener(v -> showShareOptions());

        // Toggle "Listele mele" dropdown
        btnMyLists.setOnClickListener(v -> {
            if (listsBox.getVisibility() == View.GONE) loadUserShoppingLists(listsBox, btnMyLists);
            else {
                listsBox.setVisibility(View.GONE);
                btnMyLists.setText("Listele mele ▼");
            }
        });

        // Create new list
        btnCreate.setOnClickListener(v -> showCreateListDialog());
    }

    /** Load user shopping lists into drawer dropdown */
    private void loadUserShoppingLists(LinearLayout listsBox, TextView btn) {
        String uid = mAuth.getUid();
        if (uid == null) return;
        listsBox.removeAllViews();
        DrawerLayout drawer = findViewById(R.id.drawerLayout);

        rootRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.hasChildren()) {
                    listsBox.addView(makeListItem("(nici o listă)", false));
                } else {
                    for (DataSnapshot ls : snapshot.getChildren()) {
                        String name = ls.child("name").getValue(String.class);
                        String id   = ls.getKey();

                        // each list row
                        TextView it = makeListItem(name, true);

                        // normal click = select list
                        it.setOnClickListener(v -> {
                            listId = id;
                            sharedPreferences.edit().putString("LIST_ID", id).apply();
                            initializeAdapterWithList(id);
                            drawer.closeDrawer(GravityCompat.START);
                        });

                        // long-press = delete list
                        it.setOnLongClickListener(v -> {
                            new AlertDialog.Builder(ShoppingListActivity.this)
                                    .setTitle("Confirmare ștergere")
                                    .setMessage("Ștergi lista '" + name + "'? Toate produsele vor fi pierdute.")
                                    .setPositiveButton("Șterge", (d, w) -> {
                                        // Șterge lista
                                        rootRef.child(uid).child(id).removeValue()
                                                .addOnSuccessListener(aVoid -> {
                                                    Toast.makeText(ShoppingListActivity.this,
                                                            "Lista '" + name + "' a fost ștearsă.",
                                                            Toast.LENGTH_SHORT).show();
                                                    // După ștergere, selectează următoarea listă
                                                    rootRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
                                                        @Override
                                                        public void onDataChange(@NonNull DataSnapshot snap) {
                                                            if (snap.hasChildren()) {
                                                                // ia primul ID rămas
                                                                String nextId = snap.getChildren().iterator().next().getKey();
                                                                listId = nextId;
                                                                sharedPreferences.edit().putString("LIST_ID", nextId).apply();
                                                                initializeAdapterWithList(nextId);
                                                            } else {
                                                                // dacă nu mai sunt liste
                                                                items.clear();
                                                                adapter.notifyDataSetChanged();
                                                                listId = null;
                                                                sharedPreferences.edit().remove("LIST_ID").apply();
                                                            }
                                                            // Reîmprospătează dropdown-ul
                                                            loadUserShoppingLists(listsBox, btn);
                                                        }
                                                        @Override
                                                        public void onCancelled(@NonNull DatabaseError e) { }
                                                    });
                                                })
                                                .addOnFailureListener(e ->
                                                        Toast.makeText(ShoppingListActivity.this,
                                                                "Nu s-a putut șterge lista.",
                                                                Toast.LENGTH_SHORT).show()
                                                );
                                    })
                                    .setNegativeButton("Anulează", null)
                                    .show();
                            return true;
                        });

                        listsBox.addView(it);
                    }
                }
                listsBox.setVisibility(View.VISIBLE);
                btn.setText("Listele mele ▲");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ShoppingListActivity.this,
                        "Eroare la încărcarea listelor: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Utility to create a list item view */
    private TextView makeListItem(String text, boolean enabled) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setPadding(32, 12, 32, 12);
        tv.setEnabled(enabled);
        tv.setTextColor(enabled ? Color.BLACK : Color.GRAY);
        return tv;
    }

    /** Add product to Firebase */
    private void addProduct(String uid) {
        if (listId == null) {
            Toast.makeText(this, "Listă inexistentă!", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = itemNameInput.getText().toString().trim();
        String desc = itemDescriptionInput.getText().toString().trim();
        if (name.isEmpty()) return;
        String itemId = rootRef.child(uid).push().getKey();
        rootRef.child(uid).child(listId).child("items").child(itemId)
                .setValue(new ShoppingItem(itemId, name, desc));
        itemNameInput.getText().clear();
        itemDescriptionInput.getText().clear();
    }

    /** Initialize adapter for selected list */
    private void initializeAdapterWithList(String id) {
        adapter = new ShoppingListAdapter(this, items, rootRef.child(mAuth.getUid()), id);
        listView.setAdapter(adapter);
        loadItemsFromFirebase(id);
    }

    /** Load items from Firebase into ListView */
    private void loadItemsFromFirebase(String id) {
        rootRef.child(mAuth.getUid()).child(id).child("items")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        items.clear();
                        for (DataSnapshot c : snap.getChildren()) {
                            ShoppingItem it = c.getValue(ShoppingItem.class);
                            if (it != null) items.add(it);
                        }
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError err) {
                        Toast.makeText(ShoppingListActivity.this, "Eroare la încărcarea produselor.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Create a new list in Firebase */
    private void createNewList(String name) {
        String uid = mAuth.getUid();
        String id  = rootRef.child(uid).push().getKey();
        if (id == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("participants/" + uid, true);

        // Atomizează scrierea numelui + participants
        rootRef.child(uid)
                .child(id)
                .updateChildren(data)
                .addOnSuccessListener(aVoid -> {
                    // Abia acum numele e garantat scris
                    listId = id;
                    sharedPreferences.edit().putString("LIST_ID", id).apply();
                    initializeAdapterWithList(id);
                    Toast.makeText(this, "Listă creată: " + name, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e("ShoppingList", "Eroare creare listă", e);
                    Toast.makeText(this, "Nu s-a putut crea lista.", Toast.LENGTH_SHORT).show();
                });
    }

    private void createAndInitializeNewList() { createNewList("Listă implicită"); }

    /** Show dialog to create a new list */
    private void showCreateListDialog() {
        EditText et = new EditText(this);
        et.setHint("Nume listă");
        new AlertDialog.Builder(this)
                .setTitle("Creează listă nouă").setView(et)
                .setPositiveButton("Creează", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (!n.isEmpty()) createNewList(n);
                })
                .setNegativeButton("Anulează", null)
                .show();
    }

    /** Save profile image locally and load it */
    private void saveImageLocally(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            File f = new File(getFilesDir(), "profile.jpg");
            try (FileOutputStream out = new FileOutputStream(f)) {
                byte[] buf = new byte[1024]; int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            }
            Glide.with(this)
                    .load(f)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(profileImage);
            sharedPreferences.edit().putString("PROFILE_URI", f.getAbsolutePath()).apply();
        } catch (Exception e) {
            Log.e("SaveImage", "Eroare la salvarea imaginii", e);
            Toast.makeText(this, "Eroare la salvarea pozei", Toast.LENGTH_SHORT).show();
        }
    }

    /** Show share options for the current list */
    private void showShareOptions() {
        new AlertDialog.Builder(this)
                .setTitle("Alege metoda de partajare")
                .setItems(new String[]{"Partajare ca text", "Partajare cu link"}, (dialog, which) -> {
                    if (which == 0) shareListAsText();
                    else shareListWithDynamicLink();
                })
                .show();
    }

    /** Share list as plain text */
    private void shareListAsText() {
        if (items.isEmpty()) { Toast.makeText(this, "Lista este goală!", Toast.LENGTH_SHORT).show(); return; }
        StringBuilder sb = new StringBuilder("Lista mea de cumpărături:\n");
        for (ShoppingItem it : items) {
            sb.append("• ").append(it.getName());
            if (!it.getDescription().isEmpty()) sb.append(" - ").append(it.getDescription());
            sb.append("\n");
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(share, "Trimite lista prin"));
    }

    /** Share list via Firebase Dynamic Links */
    private void shareListWithDynamicLink() {
        String deepLink = DYNAMIC_LINK_DOMAIN + "?LIST_ID=" + listId;
        FirebaseDynamicLinks.getInstance().createDynamicLink()
                .setLink(Uri.parse(deepLink))
                .setDomainUriPrefix(DYNAMIC_LINK_DOMAIN)
                .setAndroidParameters(new DynamicLink.AndroidParameters.Builder().build())
                .buildShortDynamicLink()
                .addOnSuccessListener(shortDL -> {
                    Uri link = shortDL.getShortLink();
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_TEXT, "Deschide lista mea de cumpărături: " + link);
                    startActivity(Intent.createChooser(share, "Trimite link-ul prin"));
                });
    }
}
