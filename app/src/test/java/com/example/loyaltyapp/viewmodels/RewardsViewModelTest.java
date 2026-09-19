package com.example.loyaltyapp.viewmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.example.loyaltyapp.data.repository.RewardsRepository;
import com.example.loyaltyapp.data.repository.UserRepository;
import com.example.loyaltyapp.models.User;
import com.example.loyaltyapp.models.Rewards;
import org.mockito.ArgumentCaptor;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class RewardsViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private RewardsRepository mockRewardsRepo;
    private UserRepository mockUserRepo;

    private RewardsViewModel viewModel;

    @Before
    public void setup() {
        mockRewardsRepo = mock(RewardsRepository.class);
        mockUserRepo = mock(UserRepository.class);
        // Do not instantiate viewModel here since its constructor triggers loadRewards()
    }

    @Test
    public void testInit_LoadsRewardsAndListensToUser() {
        // Act
        viewModel = new RewardsViewModel(mockRewardsRepo, mockUserRepo);
        viewModel.init("user123");

        // Assert
        // Verified it attempts to fetch rewards using the default "all" filter
        verify(mockRewardsRepo).fetchRewards(eq("all"), any(RewardsRepository.OnRewardsLoaded.class));
        
        // Verified it started listening to the user via UserRepository
        verify(mockUserRepo).listenToUser(eq("user123"), any(MutableLiveData.class));
    }

    @Test
    public void testSetFilter_ReloadsWithNewFilter() {
        // Arrange
        viewModel = new RewardsViewModel(mockRewardsRepo, mockUserRepo);

        // Act
        viewModel.setFilter("Food");

        // Assert
        // Ensure that the repository was called with the specific filter
        verify(mockRewardsRepo).fetchRewards(eq("Food"), any(RewardsRepository.OnRewardsLoaded.class));
    }

    private Rewards reward() {
        Rewards reward = new Rewards();
        reward.id = "coffee";
        reward.redeemPoints = 50;
        return reward;
    }

    private void signedIn() {
        viewModel = new RewardsViewModel(mockRewardsRepo, mockUserRepo);
        viewModel.init("alice");
    }

    private RewardsRepository.PendingCallback pendingCallback(int calls) {
        ArgumentCaptor<RewardsRepository.PendingCallback> captor = ArgumentCaptor.forClass(RewardsRepository.PendingCallback.class);
        verify(mockRewardsRepo, times(calls)).pendingReward(captor.capture());
        return captor.getValue();
    }

    private com.example.loyaltyapp.ApiService.PendingReward pending() {
        com.example.loyaltyapp.ApiService.PendingReward pending = new com.example.loyaltyapp.ApiService.PendingReward();
        pending.code = "RECOVERED";
        pending.status = "pending";
        pending.expiresAtEpochMs = 999999L;
        return pending;
    }

    @Test
    public void reopeningRestoresQrAfterEventWasConsumed() {
        signedIn();
        viewModel.recoverPendingReward(false);
        pendingCallback(1).onSuccess(pending());
        assertEquals("RECOVERED", viewModel.getRedemptionState().getValue().getContentIfNotHandled().code);
        viewModel.recoverPendingReward(true);
        pendingCallback(2).onSuccess(pending());
        assertEquals("RECOVERED", viewModel.getRedemptionState().getValue().getContentIfNotHandled().code);
    }

    @Test
    public void freshViewModelRecoversWithoutLocallyStoredCode() {
        signedIn();
        viewModel.recoverPendingReward(false);
        pendingCallback(1).onSuccess(pending());
        viewModel = new RewardsViewModel(mockRewardsRepo, mockUserRepo);
        viewModel.init("alice");
        viewModel.recoverPendingReward(false);
        pendingCallback(2).onSuccess(pending());
        assertEquals("RECOVERED", viewModel.getRedemptionState().getValue().getContentIfNotHandled().code);
        verify(mockRewardsRepo, never()).redeem(any(), any());
    }

    @Test
    public void lookupFailureAllowsExplicitRetryAndLatestResponseWins() {
        signedIn();
        viewModel.recoverPendingReward(true);
        pendingCallback(1).onError("Offline");
        assertFalse(viewModel.getCheckingPending().getValue());
        assertTrue(viewModel.getErrorMessage().getValue().contains("retry"));
        viewModel.recoverPendingReward(true);
        RewardsRepository.PendingCallback old = pendingCallback(2);
        viewModel.recoverPendingReward(true);
        pendingCallback(3).onSuccess(null);
        old.onSuccess(pending());
        assertNull(viewModel.getRedemptionState().getValue().getContentIfNotHandled().code);
    }

    @Test
    public void lostRedeemResponseTriggersLookupWithoutAnotherDeduction() {
        signedIn();
        viewModel.redeemReward(reward());
        redeemCallback().onError("Connection lost");
        pendingCallback(1).onSuccess(pending());
        assertEquals("RECOVERED", viewModel.getRedemptionState().getValue().getContentIfNotHandled().code);
        verify(mockRewardsRepo, times(1)).redeem(eq("coffee"), any());
    }

    @Test
    public void oldLookupCannotOverrideNewMutationOrAccount() {
        signedIn();
        viewModel.recoverPendingReward(false);
        RewardsRepository.PendingCallback old = pendingCallback(1);
        viewModel.redeemReward(reward());
        old.onSuccess(pending());
        assertNull(viewModel.getRedemptionState().getValue());
        viewModel.init("bob");
        old.onSuccess(pending());
        assertNull(viewModel.getRedemptionState().getValue());
    }

    @Test
    public void expiredRewardAndEmptyLookupDoNotRestoreUsableQr() {
        signedIn();
        viewModel.recoverPendingReward(false);
        com.example.loyaltyapp.ApiService.PendingReward expired = pending();
        expired.status = "awaiting_refund";
        pendingCallback(1).onSuccess(expired);
        assertEquals(0L, viewModel.getRedemptionState().getValue().getContentIfNotHandled().expiresAtEpochMs);
        viewModel.recoverPendingReward(true);
        pendingCallback(2).onSuccess(null);
        assertNull(viewModel.getRedemptionState().getValue().getContentIfNotHandled().code);
        assertTrue(viewModel.getErrorMessage().getValue().contains("No pending reward"));
    }

    private RewardsRepository.RedeemCallback redeemCallback() {
        ArgumentCaptor<RewardsRepository.RedeemCallback> captor =
                ArgumentCaptor.forClass(RewardsRepository.RedeemCallback.class);
        verify(mockRewardsRepo).redeem(eq("coffee"), captor.capture());
        return captor.getValue();
    }

    @Test
    public void balanceListenerPublishesPointsAndInitDoesNotDuplicateIt() {
        doAnswer(inv -> {
            MutableLiveData<User> data = inv.getArgument(1);
            User user = new User();
            user.setPoints(125);
            data.setValue(user);
            return null;
        }).when(mockUserRepo).listenToUser(eq("alice"), any(MutableLiveData.class));
        signedIn();
        viewModel.init("alice");
        assertEquals(Integer.valueOf(125), viewModel.getUserPoints().getValue());
        verify(mockUserRepo, times(1)).listenToUser(eq("alice"), any(MutableLiveData.class));
    }

    @Test
    public void duplicateRedeemAndCancelAreBlockedEvenDuringCatalogRefresh() {
        signedIn();
        viewModel.redeemReward(reward());
        viewModel.refresh();
        viewModel.redeemReward(reward());
        viewModel.cancelRedeem("pending");
        verify(mockRewardsRepo, times(1)).redeem(eq("coffee"), any());
        verify(mockRewardsRepo, never()).cancelRedeem(any(), any());
        assertTrue(viewModel.getIsMutating().getValue());
    }

    @Test
    public void redeemSuccessUpdatesBalanceAndEmitsCodeOnce() {
        signedIn();
        viewModel.redeemReward(reward());
        redeemCallback().onSuccess("pending-code", 123456L, 75);
        assertFalse(viewModel.getIsMutating().getValue());
        assertEquals(Integer.valueOf(75), viewModel.getUserPoints().getValue());
        assertEquals("pending-code", viewModel.getRedemptionState().getValue()
                .getContentIfNotHandled().code);
        assertNull(viewModel.getRedemptionState().getValue().getContentIfNotHandled());
    }

    @Test
    public void redeemFailureUnlocksRetryWithoutChangingBalance() {
        signedIn();
        viewModel.redeemReward(reward());
        redeemCallback().onError("Unavailable");
        assertFalse(viewModel.getIsMutating().getValue());
        assertEquals(Integer.valueOf(0), viewModel.getUserPoints().getValue());
        assertEquals("Unavailable", viewModel.getRedemptionState().getValue()
                .getContentIfNotHandled().error);
        viewModel.redeemReward(reward());
        verify(mockRewardsRepo, times(2)).redeem(eq("coffee"), any());
    }

    @Test
    public void cancellationSerializesMutationsAndUpdatesRefundedBalance() {
        signedIn();
        viewModel.cancelRedeem("pending");
        viewModel.cancelRedeem("pending");
        viewModel.redeemReward(reward());
        ArgumentCaptor<RewardsRepository.CancelCallback> captor =
                ArgumentCaptor.forClass(RewardsRepository.CancelCallback.class);
        verify(mockRewardsRepo, times(1)).cancelRedeem(eq("pending"), captor.capture());
        verify(mockRewardsRepo, never()).redeem(any(), any());
        captor.getValue().onSuccess(50, 125);
        assertFalse(viewModel.getIsMutating().getValue());
        assertEquals(Integer.valueOf(125), viewModel.getUserPoints().getValue());
        assertEquals(Integer.valueOf(50), viewModel.getCancelRefunded().getValue().getContentIfNotHandled());
    }

    @Test
    public void cancellationFailureUnlocksRetry() {
        signedIn();
        viewModel.cancelRedeem("pending");
        ArgumentCaptor<RewardsRepository.CancelCallback> captor =
                ArgumentCaptor.forClass(RewardsRepository.CancelCallback.class);
        verify(mockRewardsRepo).cancelRedeem(eq("pending"), captor.capture());
        captor.getValue().onError("Offline");
        assertFalse(viewModel.getIsMutating().getValue());
        viewModel.cancelRedeem("pending");
        verify(mockRewardsRepo, times(2)).cancelRedeem(eq("pending"), any());
    }

    @Test
    public void lateResponseFromPreviousAccountDoesNotExposeItsReward() {
        signedIn();
        viewModel.redeemReward(reward());
        RewardsRepository.RedeemCallback callback = redeemCallback();
        viewModel.init("bob");
        callback.onSuccess("alice-secret", 123456L, 75);
        assertEquals(Integer.valueOf(0), viewModel.getUserPoints().getValue());
        assertNull(viewModel.getRedemptionState().getValue());
    }

    @Test
    public void signedOutOrInvalidRequestsDoNotReachBackend() {
        viewModel = new RewardsViewModel(mockRewardsRepo, mockUserRepo);
        viewModel.redeemReward(reward());
        viewModel.cancelRedeem("pending");
        viewModel.init("alice");
        viewModel.redeemReward(null);
        viewModel.cancelRedeem(" ");
        verify(mockRewardsRepo, never()).redeem(any(), any());
        verify(mockRewardsRepo, never()).cancelRedeem(any(), any());
    }
}
