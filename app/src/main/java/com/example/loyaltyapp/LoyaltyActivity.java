package com.example.loyaltyapp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
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
import com.example.loyaltyapp.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import androidx.lifecycle.ViewModelProvider;
import com.example.loyaltyapp.viewmodels.MainViewModel;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class LoyaltyActivity extends AppCompatActivity {

    private static final String KEY_SELECTED = "selected_menu";

    private com.example.loyaltyapp.databinding.ActivityLoyaltyBinding binding;
    private BottomNavigationView bottomNav;

    private MainViewModel viewModel;

    private int selectedItemId = R.id.homeFragment;
    private boolean profileRequired = false;
    private boolean suppressNavCallback = false;

    private FirebaseAuth auth;
    private String uid;

    private final Map<Integer, Fragment> fragments = new HashMap<>(5);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = com.example.loyaltyapp.databinding.ActivityLoyaltyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] { android.Manifest.permission.POST_NOTIFICATIONS }, 1001);
            }
        }

        checkBirthdayReward();

        if (savedInstanceState != null) {
            selectedItemId = savedInstanceState.getInt(KEY_SELECTED, R.id.homeFragment);
        }

        auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
            return;
        }
        uid = user.getUid();
        
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        bottomNav = binding.bottomNavigation;
        bottomNav.setItemActiveIndicatorColor(null);

        // ou pour mettre une couleur transparente :
        bottomNav.setItemActiveIndicatorColor(
                ColorStateList.valueOf(getResources().getColor(android.R.color.transparent, getTheme())));
        setupBottomNav();

        boolean requireProfileExtra = getIntent().getBooleanExtra("require_profile", false)
                || getIntent().getBooleanExtra("force_profile", false);

        if (requireProfileExtra) {
            setProfileRequired(true, false);
            selectTabProgrammatically(R.id.profileFragment);
        } else {
            viewModel.getCurrentUser().observe(this, this::handleUserDoc);
        }

        // Ensure there is an initial fragment visible (prevents empty screen if
        // listener not fired yet)
        if (getSupportFragmentManager().findFragmentByTag(String.valueOf(selectedItemId)) == null) {
            selectTabProgrammatically(selectedItemId);
        }
        
        viewModel.getAppStatus().observe(this, status -> {
            if (status != null) {
                boolean active = Boolean.TRUE.equals((Boolean) status.get("isActive"));
                if (!active) {
                    Intent i = new Intent(this, BlockedActivity.class);
                    i.putExtra("reason", (String) status.get("message"));
                    startActivity(i);
                    finish();
                }
            }
        });
    }

    private void setupBottomNav() {
        bottomNav.setOnItemSelectedListener(item -> {
            if (suppressNavCallback)
                return true;

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
        if (selectedItemId == menuId && fragments.containsKey(menuId))
            return;

        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();

        Fragment current = fragments.get(selectedItemId);
        if (current != null && current.isAdded())
            tx.hide(current);

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
        if (menuId == R.id.homeFragment)
            return new HomeFragment();
        if (menuId == R.id.navigation_activity)
            return new ActivityFragment();
        if (menuId == R.id.scanFragment)
            return new ScanFragment();
        if (menuId == R.id.profileFragment)
            return new ProfileFragment();
        if (menuId == R.id.rewardsFragment)
            return new RewarsdFragment(); // ✅ correct
        return new HomeFragment();
    }



    private void checkBirthdayReward() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null)
            return;

        Map<String, String> body = new HashMap<>();
        body.put("uid", u.getUid());

        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.claimBirthdayReward(body).enqueue(new retrofit2.Callback<Map<String, Object>>() {
            @Override
            public void onResponse(retrofit2.Call<Map<String, Object>> call,
                    retrofit2.Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Boolean success = (Boolean) response.body().get("success");
                    if (Boolean.TRUE.equals(success)) {
                        Toast.makeText(LoyaltyActivity.this, "🎉 Happy Birthday! +15 points added!", Toast.LENGTH_LONG)
                                .show();
                    }
                }
            }

            @Override
            public void onFailure(retrofit2.Call<Map<String, Object>> call, Throwable t) {
                // Silently ignore failures on startup
            }
        });
    }

    private void handleUserDoc(User user) {
        boolean missing = true;
        if (user != null) {
            String name = user.getFullName();
            String bday = user.getBirthday();
            String gender = user.getGender();
            // P0 security: gate on profileComplete (UI flag) not isVerified
            // (backend trust flag). See User.java field comments.
            boolean complete = user.isProfileComplete();
            missing = (name == null || name.trim().isEmpty()
                    || bday == null || bday.trim().isEmpty()
                    || gender == null || gender.trim().isEmpty()
                    || !complete);
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
        if (!profileRequired)
            selectTabProgrammatically(R.id.scanFragment);
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

    public interface ScrollToTop {
        void scrollToTop();
    }

}
