package com.example.loyaltyapp.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.example.loyaltyapp.Event;
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
    // Catalog loading must not unlock a balance-changing request already in flight.
    private final MutableLiveData<Boolean> isMutating = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    // One-time signals (Event) so a config change doesn't re-fire the dialog/toast.
    private final MutableLiveData<Event<RedemptionState>> redemptionState = new MutableLiveData<>();
    private final MutableLiveData<Event<Integer>> cancelRefunded = new MutableLiveData<>();

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
            userPoints.setValue(user != null ? user.getPoints() : 0);
        }
    };

    private String activeFilter = "all";
    private String currentUid;
    private long pendingGeneration;
    private final MutableLiveData<Boolean> checkingPending = new MutableLiveData<>(false);

    public LiveData<Boolean> getCheckingPending() { return checkingPending; }

    public void recoverPendingReward(boolean userRequested) {
        if (currentUid == null || Boolean.TRUE.equals(isMutating.getValue())) return;
        final String requestUid = currentUid;
        final long generation = ++pendingGeneration;
        checkingPending.setValue(true);
        rewardsRepo.pendingReward(new RewardsRepository.PendingCallback() {
            @Override
            public void onSuccess(com.example.loyaltyapp.ApiService.PendingReward pending) {
                if (generation != pendingGeneration || !requestUid.equals(currentUid)) return;
                checkingPending.setValue(false);
                redemptionState.setValue(new Event<>(new RedemptionState(true, null, true,
                        pending == null ? null : pending.code,
                        pending == null || !"pending".equals(pending.status) ? 0 : pending.expiresAtEpochMs)));
                if (pending == null && userRequested) errorMessage.setValue("No pending reward. Completed, cancelled, or refunded rewards appear in your activity.");
            }

            @Override
            public void onError(String message) {
                if (generation != pendingGeneration || !requestUid.equals(currentUid)) return;
                checkingPending.setValue(false);
                errorMessage.setValue("Could not check your pending reward. Tap My Rewards to retry. " + message);
            }
        });
    }

    private void invalidatePendingLookup() {
        pendingGeneration++;
        checkingPending.setValue(false);
    }

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

    public LiveData<Boolean> getIsMutating() {
        return isMutating;
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

    public LiveData<Event<RedemptionState>> getRedemptionState() {
        return redemptionState;
    }

    public LiveData<Event<Integer>> getCancelRefunded() {
        return cancelRefunded;
    }

    public void setFilter(String filter) {
        if (!activeFilter.equals(filter)) {
            activeFilter = filter;
            loadRewards();
        }
    }
    
    public void refresh() {
        loadRewards();
        recoverPendingReward(false);
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
        if (Boolean.TRUE.equals(isMutating.getValue())) return;
        if (currentUid == null) {
            errorMessage.setValue("Please sign in again.");
            return;
        }
        if (reward == null || reward.id == null || reward.id.trim().isEmpty()) {
            errorMessage.setValue("This reward is unavailable. Refresh and try again.");
            return;
        }
        final String requestUid = currentUid;
        invalidatePendingLookup();
        isMutating.setValue(true);

        rewardsRepo.redeem(reward.id, new RewardsRepository.RedeemCallback() {
            @Override
            public void onSuccess(String code, long expiresAtEpochMs, int totalPoints) {
                if (!requestUid.equals(currentUid)) return;
                isMutating.setValue(false);
                userPoints.setValue(totalPoints);
                // Carry the pending code + expiry so the fragment can show a QR
                // with a countdown for the cashier to scan.
                redemptionState.postValue(new Event<>(
                        new RedemptionState(true, null, true, code, expiresAtEpochMs)));
            }

            @Override
            public void onError(String message) {
                if (!requestUid.equals(currentUid)) return;
                isMutating.setValue(false);
                redemptionState.postValue(new Event<>(
                        new RedemptionState(true, message, false, null, 0L)));
                recoverPendingReward(false);
            }
        });
    }

    /** Cancel through the backend, update the balance, and report the refund. */
    public void cancelRedeem(String code) {
        if (Boolean.TRUE.equals(isMutating.getValue())) return;
        if (currentUid == null) {
            errorMessage.setValue("Please sign in again.");
            return;
        }
        if (code == null || code.trim().isEmpty()) {
            errorMessage.setValue("No reward code to cancel.");
            return;
        }
        final String requestUid = currentUid;
        invalidatePendingLookup();
        isMutating.setValue(true);
        rewardsRepo.cancelRedeem(code, new RewardsRepository.CancelCallback() {
            @Override
            public void onSuccess(int refunded, int totalPoints) {
                if (!requestUid.equals(currentUid)) return;
                isMutating.setValue(false);
                userPoints.setValue(totalPoints);
                cancelRefunded.postValue(new Event<>(refunded));
                recoverPendingReward(false);
            }

            @Override
            public void onError(String message) {
                if (!requestUid.equals(currentUid)) return;
                isMutating.setValue(false);
                errorMessage.postValue(message);
                recoverPendingReward(false);
            }
        });
    }

    // Calling again after view recreation keeps the existing account listener.
    public void init(String uid) {
        if (uid != null && uid.equals(currentUid)) return;
        currentUid = uid;
        invalidatePendingLookup();
        isMutating.setValue(false);
        redemptionState.setValue(null);
        cancelRefunded.setValue(null);
        errorMessage.setValue(null);
        userData.setValue(null);
        if (uid == null || uid.trim().isEmpty()) {
            currentUid = null;
            userRepo.cleanup();
        } else {
            userRepo.listenToUser(uid, userData);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        currentUid = null;
        invalidatePendingLookup();
        // P1: pair the observeForever in the constructor with explicit removal
        // so the ViewModel can be GC'd once the screen is gone.
        userData.removeObserver(userObserver);
        userRepo.cleanup();
    }

    public static class RedemptionState {
        public final boolean isFinished;
        public final String error;
        public final boolean isSuccess;
        public final String code;             // pending redeem code (success only)
        public final long expiresAtEpochMs;   // countdown target (success only)

        public RedemptionState(boolean isFinished, String error, boolean isSuccess,
                               String code, long expiresAtEpochMs) {
            this.isFinished = isFinished;
            this.error = error;
            this.isSuccess = isSuccess;
            this.code = code;
            this.expiresAtEpochMs = expiresAtEpochMs;
        }
    }
}
