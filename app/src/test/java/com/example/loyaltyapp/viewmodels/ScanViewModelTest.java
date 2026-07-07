package com.example.loyaltyapp.viewmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;

import com.example.loyaltyapp.data.repository.ScanRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE)
public class ScanViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private ScanRepository mockRepository;
    private FirebaseAuth mockAuth;
    private FirebaseUser mockUser;

    private ScanViewModel viewModel;

    @Before
    public void setup() {
        mockRepository = mock(ScanRepository.class);
        mockAuth = mock(FirebaseAuth.class);
        mockUser = mock(FirebaseUser.class);

        when(mockAuth.getCurrentUser()).thenReturn(mockUser);
        when(mockUser.getUid()).thenReturn("user123");

        viewModel = new ScanViewModel(mockRepository, mockAuth);
    }

    @Test
    public void testProcessScannedCode_NullOrEmptyCode_PostsError() {
        // Act
        viewModel.processScannedCode("");

        // Assert
        ScanViewModel.ScanState state = viewModel.getScanState().getValue();
        assertNotNull(state);
        assertTrue(state.errorMsg.contains("Invalid"));
    }

    @Test
    public void testProcessScannedCode_Unauthenticated_PostsError() {
        // Arrange
        when(mockAuth.getCurrentUser()).thenReturn(null);

        // Act
        viewModel.processScannedCode("SOME_CODE");

        // Assert
        ScanViewModel.ScanState state = viewModel.getScanState().getValue();
        assertNotNull(state);
        assertTrue(state.errorMsg.contains("Authentication required"));
    }

    @Test
    public void testProcessScannedCode_ValidEarnCode_CallsEarnAndShowsPoints() {
        // Arrange: bare earn code goes to the backend via repository.earn(...).
        // Stub the callback to fire success so we can assert the resulting state.
        String code = "ABCDEFGH23";
        doAnswer(inv -> {
            ScanRepository.EarnCallback cb = inv.getArgument(1);
            cb.onSuccess(10, 110, 5);
            return null;
        }).when(mockRepository).earn(eq(code), any(ScanRepository.EarnCallback.class));

        // Act
        viewModel.processScannedCode(code);

        // Assert
        verify(mockRepository).earn(eq(code), any(ScanRepository.EarnCallback.class));
        ScanViewModel.ScanState state = viewModel.getScanState().getValue();
        assertNotNull(state);
        assertTrue(state.isSuccess);
        assertEquals("+10 Points", state.successMain);
    }

    @Test
    public void testProcessScannedCode_EarnError_PostsError() {
        // Arrange: backend rejects the code; repository reports a mapped message.
        String code = "ABCDEFGH23";
        doAnswer(inv -> {
            ScanRepository.EarnCallback cb = inv.getArgument(1);
            cb.onError("This code has expired.");
            return null;
        }).when(mockRepository).earn(eq(code), any(ScanRepository.EarnCallback.class));

        // Act
        viewModel.processScannedCode(code);

        // Assert
        ScanViewModel.ScanState state = viewModel.getScanState().getValue();
        assertNotNull(state);
        assertEquals("This code has expired.", state.errorMsg);
    }

    @Test
    public void testProcessScannedCode_ValidRedeemCode_CallsExecuteSpend() {
        // Arrange
        // REDEEM|codeId|userUid|costPoints
        String code = "REDEEM|redeemId123|user123|50";
        Task<String> mockTask = Tasks.forResult("Free Coffee");
        when(mockRepository.executeSpendTransaction(eq("redeemId123"), eq("user123"), eq(50), eq("user123"))).thenReturn(mockTask);

        // Act
        viewModel.processScannedCode(code);

        // Assert
        verify(mockRepository).executeSpendTransaction(eq("redeemId123"), eq("user123"), eq(50), eq("user123"));
    }

    @Test
    public void testProcessScannedCode_RedeemCodeForDifferentUser_PostsError() {
        // Arrange
        // Current user is user123, code is for userXYZ
        String code = "REDEEM|redeemId123|userXYZ|50";

        // Act
        viewModel.processScannedCode(code);

        // Assert
        ScanViewModel.ScanState state = viewModel.getScanState().getValue();
        assertNotNull(state);
        assertTrue(state.errorMsg.contains("belongs to another account"));
    }
}
