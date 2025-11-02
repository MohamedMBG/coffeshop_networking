package com.example.loyaltyapp.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.loyaltyapp.R;
import com.example.loyaltyapp.SignUpActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class ProfileFragment extends Fragment {

    // UI (affichage uniquement)
    private MaterialToolbar toolbar;
    private TextView tvName, tvEmail, tvPhone, tvBirthday, tvPoints;

    // (facultatif) raccourcis
    private LinearLayout layoutNotifications, layoutSavedRewards, layoutHelp, layoutTerms, layoutPrivacy;
    MaterialCardView logoutLayout;

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String uid;
    private ListenerRegistration userListener;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
        // Remplace R.layout.fragment_profile par le nom exact de ton fichier si différent
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // Init Firebase
        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(requireContext(), SignUpActivity.class));
            requireActivity().finish();
            return;
        }
        uid = user.getUid();

        // Bind vues (affichage)
        toolbar     = v.findViewById(R.id.toolbar);
        tvName      = v.findViewById(R.id.tvName);
        tvEmail     = v.findViewById(R.id.tvEmail);
        tvPhone     = v.findViewById(R.id.tvPhone);
        tvBirthday  = v.findViewById(R.id.tvBirthday);
        tvPoints    = v.findViewById(R.id.tvPoints);

        // (facultatif) autres layouts si présents
        layoutNotifications = v.findViewById(R.id.layoutNotifications);
        layoutSavedRewards  = v.findViewById(R.id.layoutSavedRewards);
        layoutHelp          = v.findViewById(R.id.layoutHelp);
        layoutTerms         = v.findViewById(R.id.layoutTerms);
        layoutPrivacy       = v.findViewById(R.id.layoutPrivacy);
        logoutLayout        = v.findViewById(R.id.layoutSignOut);

        //log out process


        // Renseigner email / phone depuis FirebaseAuth
        tvEmail.setText(user.getEmail() != null ? user.getEmail() : getString(R.string.profile_email_placeholder));
        String phone = user.getPhoneNumber();
        tvPhone.setText(!TextUtils.isEmpty(phone) ? phone : getString(R.string.profile_phone_placeholder));

        // Écoute temps réel du document utilisateur (affichage only)
        DocumentReference userRef = db.collection("users").document(uid);
        userListener = userRef.addSnapshotListener((snapshot, e) -> {
            if (!isAdded()) return;
            if (e != null) {
                Toast.makeText(requireContext(), "Failed to load profile.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (snapshot != null && snapshot.exists()) bindUser(snapshot);
        });

        // Sign out
        logoutLayout.setOnClickListener(v1 -> {
            logOut();
        });



        // Listeners (replace your Toasts with showSnack(...))
        if (layoutNotifications != null) layoutNotifications.setOnClickListener(x -> showSnack("Notifications: bientôt"));
        if (layoutSavedRewards != null) layoutSavedRewards.setOnClickListener(x -> showSnack("Saved rewards: bientôt"));
        if (layoutHelp != null) layoutHelp.setOnClickListener(x -> showSnack("Help: bientôt"));
        if (layoutTerms != null) layoutTerms.setOnClickListener(x -> showSnack("Terms: bientôt"));
        if (layoutPrivacy != null) layoutPrivacy.setOnClickListener(x -> showSnack("Privacy: bientôt"));
    }

    // Helper (put inside your Fragment)
    private void showSnack(String message) {
        // anchor to the Fragment root view (works even without CoordinatorLayout)
        View anchor = requireView();
        Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT)
                .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                .setAction("OK", v -> {}) // optional action
                .show();
    }
    private void bindUser(@NonNull DocumentSnapshot doc) {
        String fullName = doc.getString("fullName");
        String birthday = doc.getString("birthday");
        Long points     = doc.getLong("points");

        tvName.setText(!TextUtils.isEmpty(fullName) ? fullName : getString(R.string.profile_name_placeholder));
        tvBirthday.setText(!TextUtils.isEmpty(birthday) ? birthday : getString(R.string.profile_birthday_placeholder));
        tvPoints.setText(points != null ? String.valueOf(points) : "0");
    }

    private void logOut() {
        FirebaseAuth.getInstance().signOut();
        startActivity(new Intent(requireContext(), SignUpActivity.class));
        requireActivity().finish();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
    }
}
