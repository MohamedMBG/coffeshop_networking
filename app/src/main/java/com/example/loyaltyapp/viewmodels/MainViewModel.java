package com.example.loyaltyapp.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.loyaltyapp.data.repository.ConfigRepository;
import com.example.loyaltyapp.data.repository.UserRepository;
import com.example.loyaltyapp.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Map;

public class MainViewModel extends ViewModel {

    private final ConfigRepository configRepo;
    private final UserRepository userRepo;

    private final MutableLiveData<Map<String, Object>> appStatus = new MutableLiveData<>();
    private final MutableLiveData<User> currentUser = new MutableLiveData<>();

    public MainViewModel() {
        this(new ConfigRepository(), new UserRepository(), FirebaseAuth.getInstance());
    }

    @androidx.annotation.VisibleForTesting
    public MainViewModel(ConfigRepository configRepo, UserRepository userRepo, FirebaseAuth auth) {
        this.configRepo = configRepo;
        this.userRepo = userRepo;

        configRepo.listenToAppStatus(appStatus);

        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser != null) {
            userRepo.listenToUser(firebaseUser.getUid(), currentUser);
        }
    }

    public LiveData<Map<String, Object>> getAppStatus() {
        return appStatus;
    }

    public LiveData<User> getCurrentUser() {
        return currentUser;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        configRepo.cleanup();
        userRepo.cleanup();
    }
}
