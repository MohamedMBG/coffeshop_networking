package com.example.loyaltyapp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.loyaltyapp.fragments.ActivityFragment;
import com.example.loyaltyapp.fragments.HomeFragment;
import com.example.loyaltyapp.fragments.ProfileFragment;
import com.example.loyaltyapp.fragments.RewarsdFragment;
import com.example.loyaltyapp.fragments.ScanFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class LoyaltyActivity extends AppCompatActivity {

    private static final String KEY_SELECTED = "selected_menu";

    private ListenerRegistration gateListener;
    private BottomNavigationView bottomNav;

    private int selectedItemId = R.id.homeFragment;
    private boolean profileRequired = false;
    private boolean suppressNavCallback = false;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String uid;

    private final Map<Integer, Fragment> fragments = new HashMap<>(5);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loyalty); // must have nav_host_fragment & bottom_navigation

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }


        checkBirthdayReward();

        if (savedInstanceState != null) {
            selectedItemId = savedInstanceState.getInt(KEY_SELECTED, R.id.homeFragment);
        }

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
            return;
        }
        uid = user.getUid();

        bottomNav = findViewById(R.id.bottom_navigation);
        setupBottomNav();

        boolean requireProfileExtra = getIntent().getBooleanExtra("require_profile", false);
        if (requireProfileExtra) {
            setProfileRequired(true, false);
            selectTabProgrammatically(R.id.profileFragment);
        } else {
            checkProfileCompletenessAndRoute();
        }

        // Ensure there is an initial fragment visible (prevents empty screen if listener not fired yet)
        if (getSupportFragmentManager().findFragmentByTag(String.valueOf(selectedItemId)) == null) {
            selectTabProgrammatically(selectedItemId);
        }
    }
    @Override protected void onStart() {
        super.onStart();
        gateListener = db.collection("meta").document("app_status")
                .addSnapshotListener((doc, err) -> {
                    if (err != null || doc == null) return;
                    boolean active = Boolean.TRUE.equals(doc.getBoolean("isActive"));
                    if (!active) {
                        Intent i = new Intent(this, BlockedActivity.class);
                        i.putExtra("reason", doc.getString("message"));
                        startActivity(i);
                        finish();
                    }
                });
    }

    @Override protected void onStop() {
        super.onStop();
        if (gateListener != null) {
            gateListener.remove();
            gateListener = null;
        }
    }

    private void setupBottomNav() {
        bottomNav.setOnItemSelectedListener(item -> {
            if (suppressNavCallback) return true;

            int id = item.getItemId();
            if (profileRequired && id != R.id.profileFragment) {
                Toast.makeText(this, "Please complete your profile first.", Toast.LENGTH_SHORT).show();
                selectTabProgrammatically(R.id.profileFragment);
                return false;
            }
            switchTo(id);
            return true;
        });
    }

    public void selectTabProgrammatically(@IdRes int menuId) {
        if (bottomNav.getSelectedItemId() != menuId) {
            suppressNavCallback = true;
            bottomNav.setSelectedItemId(menuId);
            suppressNavCallback = false;
        }
        switchTo(menuId);
    }

    private void switchTo(@IdRes int menuId) {
        if (selectedItemId == menuId && fragments.containsKey(menuId)) return;

        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();

        Fragment current = fragments.get(selectedItemId);
        if (current != null && current.isAdded()) tx.hide(current);

        Fragment target = fragments.get(menuId);
        if (target == null) {
            target = createFragmentFor(menuId);
            fragments.put(menuId, target);
            tx.add(R.id.nav_host_fragment, target, String.valueOf(menuId));
        } else if (target.isAdded()) {
            tx.show(target);
        } else {
            tx.add(R.id.nav_host_fragment, target, String.valueOf(menuId));
        }

        tx.setReorderingAllowed(true).commitAllowingStateLoss();
        selectedItemId = menuId;
    }

    private Fragment createFragmentFor(@IdRes int menuId) {
        if (menuId == R.id.homeFragment)          return new HomeFragment();
        if (menuId == R.id.navigation_activity)   return new ActivityFragment();
        if (menuId == R.id.scanFragment)          return new ScanFragment();
        if (menuId == R.id.profileFragment)       return new ProfileFragment();
        if (menuId == R.id.rewardsFragment)       return new RewarsdFragment(); // ✅ correct
        return new HomeFragment();
    }

    private void checkProfileCompletenessAndRoute() {
        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(this::handleUserDoc)
                .addOnFailureListener(e -> {
                    setProfileRequired(false, false);
                    selectTabProgrammatically(selectedItemId);
                });
    }

    private void checkBirthdayReward() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference userRef = db.collection("users").document(u.getUid());

        userRef.get().addOnSuccessListener(doc -> {
            if (!doc.exists()) return;

            String birthday = doc.getString("birthday");
            if (birthday == null || birthday.isEmpty()) return;

            // Parse user's birthday (expected format: yyyy-MM-dd)
            String[] parts = birthday.split("-");
            if (parts.length < 3) return;
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);

            // Today's date
            java.util.Calendar c = java.util.Calendar.getInstance();
            int todayMonth = c.get(java.util.Calendar.MONTH) + 1;
            int todayDay = c.get(java.util.Calendar.DAY_OF_MONTH);

            // If today == birthday
            if (todayMonth == month && todayDay == day) {
                String todayKey = String.format("%04d-%02d-%02d",
                        c.get(java.util.Calendar.YEAR), todayMonth, todayDay);

                // Prevent multiple rewards in the same day
                String lastRewardDate = doc.getString("lastBirthdayReward");
                if (todayKey.equals(lastRewardDate)) {
                    return; // already rewarded today
                }

                Long currentPoints = doc.getLong("points");
                if (currentPoints == null) currentPoints = 0L;
                long newPoints = currentPoints + 15L;

                Map<String, Object> update = new HashMap<>();
                update.put("points", newPoints);
                update.put("lastBirthdayReward", todayKey);
                update.put("updatedAt", FieldValue.serverTimestamp());

                userRef.set(update, SetOptions.merge())
                        .addOnSuccessListener(unused ->
                                Toast.makeText(this, "🎉 Happy Birthday! +15 points added!", Toast.LENGTH_LONG).show()
                        );
            }
        });
    }


    private void handleUserDoc(DocumentSnapshot doc) {
        boolean missing = true;
        if (doc != null && doc.exists()) {
            String name = doc.getString("fullName");
            String bday = doc.getString("birthday");
            missing = (name == null || name.trim().isEmpty()
                    || bday == null || bday.trim().isEmpty());
        }

        if (missing) {
            setProfileRequired(true, true);
            selectTabProgrammatically(R.id.profileFragment);
        } else {
            setProfileRequired(false, false);
            selectTabProgrammatically(selectedItemId);
        }
    }

    private void setProfileRequired(boolean required, boolean toast) {
        profileRequired = required;
        if (toast && required) {
            Toast.makeText(this, "Please complete your profile.", Toast.LENGTH_SHORT).show();
        }
    }

    public void onProfileCompleted() {
        setProfileRequired(false, false);
        Toast.makeText(this, "Profile completed!", Toast.LENGTH_SHORT).show();
        selectTabProgrammatically(R.id.homeFragment);
    }

    public void openScanTab() {
        if (!profileRequired) selectTabProgrammatically(R.id.scanFragment);
        else {
            Toast.makeText(this, "Please complete your profile first.", Toast.LENGTH_SHORT).show();
            selectTabProgrammatically(R.id.profileFragment);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(KEY_SELECTED, selectedItemId);
        super.onSaveInstanceState(outState);
    }

    public interface ScrollToTop { void scrollToTop(); }

}
