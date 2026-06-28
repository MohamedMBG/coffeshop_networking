package com.example.loyaltyapp.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.example.loyaltyapp.data.repository.RewardsRepository;
import com.example.loyaltyapp.data.repository.UserRepository;
import com.example.loyaltyapp.models.Rewards;
import com.example.loyaltyapp.models.User;

import java.util.List;

public class RewardsViewModel extends ViewModel {

    private final RewardsRepository rewardsRepo;
    private final UserRepository userRepo;

    private final MutableLiveData<List<Rewards>> rewardsList = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<RedemptionState> redemptionState = new MutableLiveData<>();
    
    // Listen to real-time points from UserRepository
    private final MutableLiveData<User> userData = new MutableLiveData<>();
    private final MutableLiveData<Integer> userPoints = new MutableLiveData<>(0);

    // P1: hold a reference to the observer so we can remove it in onCleared.
    // Previously the lambda was passed directly to observeForever with no way
    // to unregister, which leaked the ViewModel for the lifetime of the
    // LiveData (and the LiveData for as long as anything held this VM).
    private final Observer<User> userObserver = new Observer<User>() {
        @Override
        public void onChanged(User user) {
            userPoints.postValue(user != null ? user.getPoints() : 0);
        }
    };

    private String activeFilter = "all";

    public RewardsViewModel() {
        this(new RewardsRepository(), new UserRepository());
    }

    @androidx.annotation.VisibleForTesting
    public RewardsViewModel(RewardsRepository rewardsRepo, UserRepository userRepo) {
        this.rewardsRepo = rewardsRepo;
        this.userRepo = userRepo;

        userData.observeForever(userObserver);

        loadRewards();
    }

    public LiveData<List<Rewards>> getRewards() {
        return rewardsList;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<User> getUserData() {
        return userData;
    }

    public LiveData<Integer> getUserPoints() {
        return userPoints;
    }

    public LiveData<RedemptionState> getRedemptionState() {
        return redemptionState;
    }

    public void setFilter(String filter) {
        if (!activeFilter.equals(filter)) {
            activeFilter = filter;
            loadRewards();
        }
    }
    
    public void refresh() {
        loadRewards();
    }

    private void loadRewards() {
        isLoading.setValue(true);
        rewardsRepo.fetchRewards(activeFilter, new RewardsRepository.OnRewardsLoaded() {
            @Override
            public void onSuccess(List<Rewards> rewards) {
                rewardsList.postValue(rewards);
                isLoading.postValue(false);
            }

            @Override
            public void onError(Exception e) {
                errorMessage.postValue(e.getMessage());
                isLoading.postValue(false);
            }
        });
    }

    public void redeemReward(Rewards reward) {
        isLoading.setValue(true);
        redemptionState.setValue(new RedemptionState(false, null, false));

        rewardsRepo.submitRedemption(reward, new RewardsRepository.RedeemCallback() {
            @Override
            public void onSuccess() {
                isLoading.postValue(false);
                redemptionState.postValue(new RedemptionState(true, null, true));
            }

            @Override
            public void onError(String message) {
                isLoading.postValue(false);
                errorMessage.postValue(message);
                redemptionState.postValue(new RedemptionState(true, message, false));
            }
        });
    }

    // Call this from Fragment to start listening (UID comes from an Auth ViewModel/Repo ideally, 
    // but for now let's set it if Fragment knows it, or we can add it to UserRepository). 
    // Let's create an init method.
    public void init(String uid) {
        userRepo.listenToUser(uid, userData);
    }

    public void resetRedemptionState() {
        redemptionState.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // P1: pair the observeForever in the constructor with explicit removal
        // so the ViewModel can be GC'd once the screen is gone.
        userData.removeObserver(userObserver);
        userRepo.cleanup();
    }

    public static class RedemptionState {
        public final boolean isFinished;
        public final String error;
        public final boolean isSuccess;

        public RedemptionState(boolean isFinished, String error, boolean isSuccess) {
            this.isFinished = isFinished;
            this.error = error;
            this.isSuccess = isSuccess;
        }
    }
}
